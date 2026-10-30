package com.ltq.count.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ltq.count.R
import com.ltq.count.data.repo.CounterRepository
import com.ltq.count.data.repo.CounterWriteResult
import com.ltq.count.data.repo.TagWriteResult
import com.ltq.count.domain.AdjustOutcome
import com.ltq.count.domain.UndoOutcome
import com.ltq.count.domain.model.CounterSummary
import com.ltq.count.domain.model.Tag
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val loading: Boolean = true,
    val pinned: List<CounterSummary> = emptyList(),
    val normal: List<CounterSummary> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val selectedTagId: String? = null,
    val sessionSteps: Map<String, Int> = emptyMap(),
)

sealed interface HomeEffect {
    data class ValueChanged(val counterId: String, val eventId: String, val newValue: Long) : HomeEffect
    data class Message(val messageRes: Int, val args: List<Any> = emptyList()) : HomeEffect
}

class HomeViewModel(private val repository: CounterRepository) : ViewModel() {
    private val selectedTagId = MutableStateFlow<String?>(null)
    private val sessionSteps = MutableStateFlow<Map<String, Int>>(emptyMap())

    private val _effects = Channel<HomeEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    val uiState: StateFlow<HomeUiState> = combine(
        repository.observeSummaries(),
        repository.observeTags(),
        selectedTagId,
        sessionSteps,
    ) { summaries, tags, tagId, steps ->
        // A tag can disappear (deleted here or replaced by an import); fall back to "All".
        val activeTagId = tagId?.takeIf { id -> tags.any { it.id == id } }
        val filtered = if (activeTagId == null) {
            summaries
        } else {
            summaries.filter { summary -> summary.tags.any { it.id == activeTagId } }
        }
        HomeUiState(
            loading = false,
            pinned = filtered.filter { it.counter.pinned },
            normal = filtered.filterNot { it.counter.pinned },
            tags = tags,
            selectedTagId = activeTagId,
            sessionSteps = steps,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun selectTag(tagId: String?) {
        selectedTagId.value = tagId
    }

    fun deleteTag(tag: Tag) {
        viewModelScope.launch {
            val deleted = runCatching { repository.deleteTag(tag.id) }.getOrDefault(false)
            if (deleted) {
                if (selectedTagId.value == tag.id) selectedTagId.value = null
                _effects.send(HomeEffect.Message(R.string.tag_deleted, listOf(tag.name)))
            } else {
                _effects.send(HomeEffect.Message(R.string.tag_delete_failed))
            }
        }
    }

    fun stepFor(counterId: String, defaultStep: Int): Int = sessionSteps.value[counterId] ?: defaultStep

    fun setSessionStep(counterId: String, step: Int) {
        if (step < 1) return
        sessionSteps.update { it + (counterId to step) }
    }

    fun adjust(summary: CounterSummary, direction: Int) {
        val step = stepFor(summary.counter.id, summary.counter.defaultStep)
        val delta = direction * step
        viewModelScope.launch {
            when (val outcome = repository.adjust(summary.counter.id, delta)) {
                is AdjustOutcome.Applied -> _effects.send(
                    HomeEffect.ValueChanged(summary.counter.id, outcome.event.id, outcome.newValue),
                )
                is AdjustOutcome.BlockedByNegative -> _effects.send(
                    HomeEffect.Message(
                        R.string.value_blocked_negative,
                        listOf(outcome.currentValue, delta),
                    ),
                )
                is AdjustOutcome.Invalid -> _effects.send(HomeEffect.Message(R.string.value_invalid))
                AdjustOutcome.CounterMissing -> _effects.send(HomeEffect.Message(R.string.value_counter_missing))
                AdjustOutcome.AlreadyAtBaseline -> _effects.send(HomeEffect.Message(R.string.value_already_baseline))
            }
        }
    }

    fun undo(counterId: String, eventId: String) {
        viewModelScope.launch {
            when (repository.undoEvent(counterId, eventId)) {
                is UndoOutcome.Applied -> _effects.send(HomeEffect.Message(R.string.snackbar_undo_success))
                is UndoOutcome.BlockedByNegative -> _effects.send(HomeEffect.Message(R.string.snackbar_undo_blocked))
                UndoOutcome.NotLatest, UndoOutcome.NothingToUndo ->
                    _effects.send(HomeEffect.Message(R.string.value_invalid))
                UndoOutcome.EventMissing -> _effects.send(HomeEffect.Message(R.string.value_counter_missing))
            }
        }
    }

    fun togglePin(summary: CounterSummary) {
        viewModelScope.launch {
            repository.setPinned(summary.counter.id, !summary.counter.pinned)
        }
    }

    fun delete(counterId: String) {
        viewModelScope.launch { repository.deleteCounter(counterId) }
    }

    fun setTags(counterId: String, tagIds: List<String>) {
        viewModelScope.launch {
            when (repository.setTags(counterId, tagIds)) {
                is CounterWriteResult.Invalid -> _effects.send(HomeEffect.Message(R.string.error_generic))
                CounterWriteResult.Missing -> _effects.send(HomeEffect.Message(R.string.value_counter_missing))
                is CounterWriteResult.WouldGoNegative, is CounterWriteResult.Saved -> Unit
            }
        }
    }

    fun createTag(name: String, onResult: (TagWriteResult) -> Unit) {
        viewModelScope.launch { onResult(repository.createTag(name)) }
    }

    fun reorder(orderedIds: List<String>) {
        viewModelScope.launch { repository.reorderNormalCounters(orderedIds) }
    }
}

fun homeViewModelFactory(repository: CounterRepository) = viewModelFactory {
    initializer { HomeViewModel(repository) }
}
