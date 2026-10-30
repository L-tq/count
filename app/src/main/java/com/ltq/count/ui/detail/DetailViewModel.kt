package com.ltq.count.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ltq.count.R
import com.ltq.count.data.repo.CounterRepository
import com.ltq.count.domain.AdjustOutcome
import com.ltq.count.domain.EventFilter
import com.ltq.count.domain.EventFilters
import com.ltq.count.domain.RuleViolation
import com.ltq.count.domain.UndoOutcome
import com.ltq.count.domain.heatmap.HeatmapCalculator
import com.ltq.count.domain.heatmap.HeatmapData
import com.ltq.count.domain.heatmap.HeatmapGranularity
import com.ltq.count.domain.heatmap.HeatmapMetric
import com.ltq.count.domain.model.CountEvent
import com.ltq.count.domain.model.CounterSummary
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DetailUiState(
    val loading: Boolean = true,
    val counter: CounterSummary? = null,
    val allEvents: List<CountEvent> = emptyList(),
    val filteredEvents: List<CountEvent> = emptyList(),
    val filter: EventFilter = EventFilter(),
    val metric: HeatmapMetric = HeatmapMetric.EVENT_COUNT,
    val granularity: HeatmapGranularity = HeatmapGranularity.DAY,
    val heatmap: HeatmapData? = null,
    val zoneId: ZoneId = ZoneId.systemDefault(),
)

sealed interface DetailEffect {
    data class ValueChanged(
        val eventId: String,
        val newValue: Long,
        val reset: Boolean,
    ) : DetailEffect

    data class Message(val messageRes: Int, val args: List<Any> = emptyList()) : DetailEffect
}

class DetailViewModel(
    private val counterId: String,
    private val repository: CounterRepository,
) : ViewModel() {
    private val filter = MutableStateFlow(EventFilter())
    private val metric = MutableStateFlow(HeatmapMetric.EVENT_COUNT)
    private val granularity = MutableStateFlow(HeatmapGranularity.DAY)

    private val _effects = Channel<DetailEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    val uiState: StateFlow<DetailUiState> = combine(
        repository.observeCounter(counterId),
        repository.observeEvents(counterId),
        filter,
        metric,
        granularity,
    ) { counter, events, currentFilter, currentMetric, currentGranularity ->
        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        DetailUiState(
            loading = false,
            counter = counter,
            allEvents = events,
            filteredEvents = EventFilters.apply(events, currentFilter, now),
            filter = currentFilter,
            metric = currentMetric,
            granularity = currentGranularity,
            heatmap = HeatmapCalculator.calculate(
                events = events,
                metric = currentMetric,
                granularity = currentGranularity,
                zone = zone,
                now = now,
            ),
            zoneId = zone,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUiState())

    fun setFilter(newFilter: EventFilter) {
        filter.value = newFilter
    }

    fun setMetric(newMetric: HeatmapMetric) {
        metric.value = newMetric
    }

    fun setGranularity(newGranularity: HeatmapGranularity) {
        granularity.value = newGranularity
    }

    fun resetToInitial() {
        viewModelScope.launch {
            when (val outcome = repository.resetToInitial(counterId)) {
                is AdjustOutcome.Applied -> _effects.send(
                    DetailEffect.ValueChanged(outcome.event.id, outcome.newValue, reset = true),
                )
                is AdjustOutcome.BlockedByNegative -> _effects.send(
                    DetailEffect.Message(
                        R.string.value_blocked_negative,
                        listOf(outcome.currentValue, outcome.attemptedDelta),
                    ),
                )
                is AdjustOutcome.Invalid -> _effects.send(DetailEffect.Message(R.string.value_invalid))
                AdjustOutcome.CounterMissing -> _effects.send(DetailEffect.Message(R.string.value_counter_missing))
                AdjustOutcome.AlreadyAtBaseline -> _effects.send(DetailEffect.Message(R.string.value_already_baseline))
            }
        }
    }

    fun undoLatest() {
        viewModelScope.launch {
            when (val outcome = repository.undoLatest(counterId)) {
                is UndoOutcome.Applied -> _effects.send(
                    DetailEffect.Message(R.string.detail_undo_done, listOf(outcome.reversedEventIds.size)),
                )
                is UndoOutcome.BlockedByNegative -> _effects.send(DetailEffect.Message(R.string.detail_undo_blocked))
                UndoOutcome.NothingToUndo -> _effects.send(DetailEffect.Message(R.string.detail_undo_none))
                UndoOutcome.NotLatest -> _effects.send(DetailEffect.Message(R.string.value_invalid))
                UndoOutcome.EventMissing -> _effects.send(DetailEffect.Message(R.string.value_counter_missing))
            }
        }
    }

    fun undoEvent(eventId: String) {
        viewModelScope.launch {
            when (repository.undoEvent(counterId, eventId)) {
                is UndoOutcome.Applied -> _effects.send(DetailEffect.Message(R.string.snackbar_undo_success))
                is UndoOutcome.BlockedByNegative -> _effects.send(DetailEffect.Message(R.string.snackbar_undo_blocked))
                UndoOutcome.NothingToUndo -> _effects.send(DetailEffect.Message(R.string.detail_undo_none))
                UndoOutcome.NotLatest -> _effects.send(DetailEffect.Message(R.string.detail_undo_none))
                UndoOutcome.EventMissing -> _effects.send(DetailEffect.Message(R.string.value_counter_missing))
            }
        }
    }

    fun undoFrom(eventId: String) {
        viewModelScope.launch {
            when (val outcome = repository.undoFrom(counterId, eventId)) {
                is UndoOutcome.Applied -> _effects.send(
                    DetailEffect.Message(R.string.detail_undo_done, listOf(outcome.reversedEventIds.size)),
                )
                is UndoOutcome.BlockedByNegative -> _effects.send(DetailEffect.Message(R.string.detail_undo_blocked))
                UndoOutcome.NothingToUndo -> _effects.send(DetailEffect.Message(R.string.detail_undo_none))
                UndoOutcome.NotLatest -> _effects.send(DetailEffect.Message(R.string.detail_undo_none))
                UndoOutcome.EventMissing -> _effects.send(DetailEffect.Message(R.string.value_counter_missing))
            }
        }
    }

    fun updateNote(eventId: String, note: String?) {
        viewModelScope.launch {
            when (repository.updateEventNote(eventId, note)) {
                RuleViolation.NoteTooLong -> _effects.send(
                    DetailEffect.Message(R.string.note_too_long, listOf(com.ltq.count.domain.CounterMath.MAX_NOTE_LENGTH)),
                )
                null -> Unit
                else -> _effects.send(DetailEffect.Message(R.string.error_generic))
            }
        }
    }
}

fun detailViewModelFactory(counterId: String, repository: CounterRepository) = viewModelFactory {
    initializer { DetailViewModel(counterId, repository) }
}
