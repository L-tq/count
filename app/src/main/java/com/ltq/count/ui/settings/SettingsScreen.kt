package com.ltq.count.ui.settings

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ltq.count.AppContainer
import com.ltq.count.BuildConfig
import com.ltq.count.R
import com.ltq.count.data.prefs.CardSize
import com.ltq.count.data.prefs.ThemeMode
import com.ltq.count.domain.backup.BackupError
import com.ltq.count.domain.backup.ImportMode
import com.ltq.count.domain.backup.NegativeImportPolicy
import com.ltq.count.ui.components.CircleIconButton
import com.ltq.count.ui.components.CountSnackbarHost
import com.ltq.count.ui.components.ConfirmDialog
import com.ltq.count.ui.components.Pill
import com.ltq.count.ui.components.SectionLabel
import com.ltq.count.ui.components.SoftCard
import com.ltq.count.ui.util.backupFieldLabel

@Composable
fun SettingsScreen(
    container: AppContainer,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(
        factory = settingsViewModelFactory(
            container.counterRepository,
            container.settingsRepository,
            container.backupRepository,
        ),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val exportJson = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let { viewModel.export(ExportFormat.JSON, it) } }
    val exportCsv = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri -> uri?.let { viewModel.export(ExportFormat.CSV, it) } }
    val importJson = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.importFile(it) } }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            val message = when (effect) {
                is SettingsEffect.Message ->
                    context.getString(effect.messageRes, *effect.args.toTypedArray())
                is SettingsEffect.ParseFailure -> effect.error.userMessage(context)
                is SettingsEffect.ImportSummary -> buildString {
                    append(
                        context.getString(
                            R.string.settings_import_done,
                            effect.counters,
                            effect.events,
                        ),
                    )
                    if (effect.duplicates > 0) {
                        append(
                            context.getString(
                                R.string.settings_import_suffix_duplicates,
                                effect.duplicates,
                            ),
                        )
                    }
                    if (effect.skipped > 0) {
                        append(
                            context.getString(
                                R.string.settings_import_suffix_skipped,
                                effect.skipped,
                            ),
                        )
                    }
                }
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { CountSnackbarHost(snackbarHostState) },
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.a11y_back),
                    onClick = onBack,
                )
                Text(
                    text = stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (state.busy) {
                item(key = "busy") {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    )
                }
            }

            item(key = "appearance") {
                SoftCard {
                    Text(
                        text = stringResource(R.string.settings_appearance),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(10.dp))
                    SectionLabel(text = stringResource(R.string.settings_theme))
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingChoice(
                            label = stringResource(R.string.settings_theme_system),
                            selected = state.themeMode == ThemeMode.SYSTEM,
                        ) { viewModel.setThemeMode(ThemeMode.SYSTEM) }
                        SettingChoice(
                            label = stringResource(R.string.settings_theme_light),
                            selected = state.themeMode == ThemeMode.LIGHT,
                        ) { viewModel.setThemeMode(ThemeMode.LIGHT) }
                        SettingChoice(
                            label = stringResource(R.string.settings_theme_dark),
                            selected = state.themeMode == ThemeMode.DARK,
                        ) { viewModel.setThemeMode(ThemeMode.DARK) }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.settings_theme_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(18.dp))
                    SectionLabel(text = stringResource(R.string.settings_card_size))
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingChoice(
                            label = stringResource(R.string.settings_card_size_compact),
                            selected = state.cardSize == CardSize.COMPACT,
                        ) { viewModel.setCardSize(CardSize.COMPACT) }
                        SettingChoice(
                            label = stringResource(R.string.settings_card_size_standard),
                            selected = state.cardSize == CardSize.STANDARD,
                        ) { viewModel.setCardSize(CardSize.STANDARD) }
                        SettingChoice(
                            label = stringResource(R.string.settings_card_size_comfortable),
                            selected = state.cardSize == CardSize.COMFORTABLE,
                        ) { viewModel.setCardSize(CardSize.COMFORTABLE) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.settings_card_size_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item(key = "data") {
                SoftCard {
                    Text(
                        text = stringResource(R.string.settings_data),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(
                            R.string.settings_stats,
                            state.stats.counters,
                            state.stats.events,
                            state.stats.tags,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    ActionRow(
                        icon = Icons.Rounded.Download,
                        title = stringResource(R.string.settings_export_json),
                        description = stringResource(R.string.settings_export_json_desc),
                        onClick = { exportJson.launch(viewModel.suggestedName(ExportFormat.JSON)) },
                    )
                    ActionRow(
                        icon = Icons.Rounded.Download,
                        title = stringResource(R.string.settings_export_csv),
                        description = stringResource(R.string.settings_export_csv_desc),
                        onClick = { exportCsv.launch(viewModel.suggestedName(ExportFormat.CSV)) },
                    )
                    ActionRow(
                        icon = Icons.Rounded.Upload,
                        title = stringResource(R.string.settings_import_json),
                        description = stringResource(R.string.settings_import_json_desc),
                        onClick = { importJson.launch(arrayOf("*/*")) },
                    )
                }
            }

            item(key = "about") {
                SoftCard {
                    Text(
                        text = stringResource(R.string.settings_about),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.settings_about_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.settings_privacy),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }

    val pending = state.pending
    if (pending != null) {
        when (pending.stage) {
            ImportStage.CHOOSE_MODE -> AlertDialog(
                onDismissRequest = viewModel::cancelPending,
                title = {
                    Text(
                        stringResource(R.string.settings_import_mode_title),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                },
                text = {
                    Column {
                        ImportModeChoice(
                            title = stringResource(R.string.settings_import_merge),
                            description = stringResource(R.string.settings_import_merge_desc),
                            onClick = { viewModel.chooseMode(ImportMode.MERGE) },
                        )
                        Spacer(Modifier.height(10.dp))
                        ImportModeChoice(
                            title = stringResource(R.string.settings_import_replace),
                            description = stringResource(R.string.settings_import_replace_desc),
                            onClick = { viewModel.chooseMode(ImportMode.REPLACE) },
                            destructive = true,
                        )
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = viewModel::cancelPending) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            )

            ImportStage.CONFIRM_REPLACE -> ConfirmDialog(
                title = stringResource(R.string.settings_import_replace_confirm_title),
                body = stringResource(
                    R.string.settings_import_replace_confirm_body,
                    state.stats.counters,
                    state.stats.events,
                ),
                confirmLabel = stringResource(R.string.settings_import_confirm_replace),
                cancelLabel = stringResource(R.string.action_cancel),
                destructive = true,
                onConfirm = viewModel::confirmReplace,
                onDismiss = viewModel::cancelPending,
            )

            ImportStage.CONFLICTS -> AlertDialog(
                onDismissRequest = viewModel::cancelPending,
                title = {
                    Text(
                        stringResource(R.string.settings_import_negative_title),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                },
                text = {
                    Column {
                        Text(
                            text = stringResource(
                                R.string.settings_import_negative_body,
                                pending.plan?.conflicts
                                    ?.joinToString("\n") { "${it.counterName}  ${it.worstValue}" }
                                    .orEmpty(),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.settings_import_negative_allow_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.chooseNegativePolicy(NegativeImportPolicy.ALLOW_NEGATIVE) }) {
                        Text(stringResource(R.string.settings_import_negative_allow))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.chooseNegativePolicy(NegativeImportPolicy.SKIP_AFFECTED) }) {
                        Text(stringResource(R.string.settings_import_negative_skip))
                    }
                },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            )
        }
    }
}

@Composable
private fun SettingChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Pill(label = label, selected = selected, onClick = onClick)
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun ImportModeChoice(
    title: String,
    description: String,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun BackupError.userMessage(context: Context): String = when (this) {
    BackupError.MalformedJson -> context.getString(R.string.settings_import_malformed)
    BackupError.MissingSchemaVersion -> context.getString(R.string.settings_import_missing_version)
    is BackupError.UnsupportedSchemaVersion ->
        context.getString(R.string.settings_import_unsupported, version)
    is BackupError.MissingField ->
        context.getString(
            R.string.settings_import_invalid_field,
            path,
            context.getString(R.string.backup_field_fallback),
        )
    is BackupError.InvalidField ->
        context.getString(
            R.string.settings_import_invalid_field,
            path,
            backupFieldLabel(context, field),
        )
}
