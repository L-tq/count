package com.ltq.count.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ltq.count.R
import com.ltq.count.data.prefs.CardSize
import com.ltq.count.data.prefs.SettingsRepository
import com.ltq.count.data.prefs.ThemeMode
import com.ltq.count.data.repo.BackupRepository
import com.ltq.count.data.repo.CounterRepository
import com.ltq.count.domain.backup.BackupCodec
import com.ltq.count.domain.backup.BackupError
import com.ltq.count.domain.backup.BackupParseResult
import com.ltq.count.domain.backup.ImportMode
import com.ltq.count.domain.backup.ImportPlan
import com.ltq.count.domain.backup.ImportedData
import com.ltq.count.domain.backup.NegativeImportPolicy
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ExportFormat { JSON, CSV }

data class DataStats(
    val counters: Int = 0,
    val events: Int = 0,
    val tags: Int = 0,
)

enum class ImportStage { CHOOSE_MODE, CONFIRM_REPLACE, CONFLICTS }

data class PendingImport(
    val data: ImportedData,
    val stage: ImportStage,
    val mode: ImportMode,
    val plan: ImportPlan? = null,
)

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val cardSize: CardSize = CardSize.STANDARD,
    val stats: DataStats = DataStats(),
    val busy: Boolean = false,
    val pending: PendingImport? = null,
)

sealed interface SettingsEffect {
    data class Message(val messageRes: Int, val args: List<Any> = emptyList()) : SettingsEffect
    data class ParseFailure(val error: BackupError) : SettingsEffect
    data class ImportSummary(
        val counters: Int,
        val events: Int,
        val duplicates: Int,
        val skipped: Int,
    ) : SettingsEffect
}

class SettingsViewModel(
    private val counterRepository: CounterRepository,
    private val settingsRepository: SettingsRepository,
    private val backupRepository: BackupRepository,
    private val clock: Clock = Clock.systemUTC(),
) : ViewModel() {
    private val busy = MutableStateFlow(false)
    private val pending = MutableStateFlow<PendingImport?>(null)

    private val _effects = Channel<SettingsEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private val stats = combine(
        counterRepository.observeSummaries(),
        counterRepository.observeTags(),
    ) { summaries, tags ->
        DataStats(
            counters = summaries.size,
            events = summaries.sumOf { it.eventCount },
            tags = tags.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DataStats())

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.themeMode,
        settingsRepository.cardSize,
        stats,
        busy,
        pending,
    ) { themeMode, cardSize, dataStats, isBusy, pendingImport ->
        SettingsUiState(
            themeMode = themeMode,
            cardSize = cardSize,
            stats = dataStats,
            busy = isBusy,
            pending = pendingImport,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setCardSize(size: CardSize) {
        viewModelScope.launch { settingsRepository.setCardSize(size) }
    }

    fun suggestedName(format: ExportFormat): String {
        val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")
            .format(LocalDateTime.ofInstant(clock.instant(), ZoneId.systemDefault()))
        return when (format) {
            ExportFormat.JSON -> "count-backup-$stamp.json"
            ExportFormat.CSV -> "count-events-$stamp.csv"
        }
    }

    fun export(format: ExportFormat, uri: Uri) {
        viewModelScope.launch {
            busy.value = true
            val payload = runCatching { counterRepository.buildExport() }.getOrNull()
            if (payload == null || payload.counterCount == 0) {
                busy.value = false
                _effects.send(SettingsEffect.Message(R.string.settings_export_empty))
                return@launch
            }
            val text = if (format == ExportFormat.JSON) payload.json else payload.csv
            val written = backupRepository.writeText(uri, text)
            busy.value = false
            _effects.send(
                if (written) {
                    SettingsEffect.Message(
                        R.string.settings_export_success,
                        listOf(payload.counterCount, payload.eventCount),
                    )
                } else {
                    SettingsEffect.Message(R.string.settings_export_failed)
                },
            )
        }
    }

    fun importFile(uri: Uri) {
        viewModelScope.launch {
            busy.value = true
            val text = backupRepository.readText(uri)
            if (text == null) {
                busy.value = false
                _effects.send(SettingsEffect.Message(R.string.settings_import_read_failed))
                return@launch
            }
            when (val parsed = BackupCodec.decode(text)) {
                is BackupParseResult.Failure -> {
                    busy.value = false
                    _effects.send(SettingsEffect.ParseFailure(parsed.error))
                }
                is BackupParseResult.Success -> {
                    busy.value = false
                    pending.value = PendingImport(
                        data = parsed.data,
                        stage = ImportStage.CHOOSE_MODE,
                        mode = ImportMode.MERGE,
                    )
                }
            }
        }
    }

    fun chooseMode(mode: ImportMode) {
        if (busy.value) return
        val current = pending.value ?: return
        pending.value = null
        if (mode == ImportMode.REPLACE) {
            pending.value = current.copy(stage = ImportStage.CONFIRM_REPLACE, mode = ImportMode.REPLACE)
        } else {
            planAndApply(current.data, ImportMode.MERGE, NegativeImportPolicy.SKIP_AFFECTED)
        }
    }

    fun confirmReplace() {
        if (busy.value) return
        val current = pending.value ?: return
        planAndApply(current.data, ImportMode.REPLACE, NegativeImportPolicy.SKIP_AFFECTED)
    }

    fun chooseNegativePolicy(policy: NegativeImportPolicy) {
        if (busy.value) return
        val current = pending.value ?: return
        if (policy == NegativeImportPolicy.SKIP_AFFECTED) {
            val plan = current.plan
            pending.value = null
            if (plan != null) applyPlan(plan)
        } else {
            planAndApply(current.data, current.mode, NegativeImportPolicy.ALLOW_NEGATIVE)
        }
    }

    fun cancelPending() {
        pending.value = null
    }

    private fun planAndApply(
        data: ImportedData,
        mode: ImportMode,
        policy: NegativeImportPolicy,
    ) {
        viewModelScope.launch {
            busy.value = true
            val plan = runCatching {
                backupRepository.planImport(data, mode, policy)
            }.getOrNull()
            busy.value = false
            if (plan == null) {
                pending.value = null
                _effects.send(SettingsEffect.Message(R.string.settings_import_failed))
                return@launch
            }
            if (plan.conflicts.isNotEmpty() && policy == NegativeImportPolicy.SKIP_AFFECTED) {
                pending.value = PendingImport(
                    data = data,
                    stage = ImportStage.CONFLICTS,
                    mode = mode,
                    plan = plan,
                )
            } else {
                applyPlan(plan)
            }
        }
    }

    private fun applyPlan(plan: ImportPlan) {
        viewModelScope.launch {
            busy.value = true
            val result = runCatching { backupRepository.apply(plan) }
            busy.value = false
            pending.value = null
            if (result.isFailure) {
                result.exceptionOrNull()?.let { error ->
                    android.util.Log.e("CountImport", "Import apply failed", error)
                }
                _effects.send(SettingsEffect.Message(R.string.settings_import_failed))
                return@launch
            }
            _effects.send(
                SettingsEffect.ImportSummary(
                    counters = plan.counts.totalCounters,
                    events = plan.counts.eventsAdded,
                    duplicates = plan.counts.eventsDuplicate,
                    skipped = plan.counts.countersSkipped,
                ),
            )
        }
    }
}

fun settingsViewModelFactory(
    counterRepository: CounterRepository,
    settingsRepository: SettingsRepository,
    backupRepository: BackupRepository,
) = viewModelFactory {
    initializer { SettingsViewModel(counterRepository, settingsRepository, backupRepository) }
}
