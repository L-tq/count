package com.ltq.count.ui.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ltq.count.R
import com.ltq.count.data.repo.CounterInput
import com.ltq.count.data.repo.CounterRepository
import com.ltq.count.data.repo.CounterWriteResult
import com.ltq.count.data.repo.TagWriteResult
import com.ltq.count.domain.CounterMath
import com.ltq.count.domain.RuleViolation
import com.ltq.count.domain.model.Tag
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditBaseline(
    val name: String,
    val initialText: String,
    val allowNegative: Boolean,
    val stepText: String,
    val tagIds: List<String>,
)

data class CounterEditUiState(
    val isNew: Boolean = true,
    val loading: Boolean = false,
    val notFound: Boolean = false,
    val name: String = "",
    val initialText: String = "0",
    val allowNegative: Boolean = false,
    val stepText: String = "1",
    val selectedTagIds: List<String> = emptyList(),
    val currentValue: Long = 0,
    val baseline: EditBaseline = EditBaseline("", "0", false, "1", emptyList()),
    val saving: Boolean = false,
) {
    val dirty: Boolean
        get() = baseline != EditBaseline(name, initialText, allowNegative, stepText, selectedTagIds)
}

sealed interface EditEffect {
    data class Saved(val counterId: String) : EditEffect
    data class Error(val messageRes: Int, val args: List<Any> = emptyList()) : EditEffect
}

class CounterEditViewModel(
    private val repository: CounterRepository,
    private val counterId: String?,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CounterEditUiState(isNew = counterId == null, loading = counterId != null))
    val uiState: StateFlow<CounterEditUiState> = _uiState.asStateFlow()

    private val _effects = Channel<EditEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    val allTags: StateFlow<List<Tag>> = repository.observeTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        if (counterId != null) {
            viewModelScope.launch {
                val summary = repository.observeCounter(counterId).first()
                if (summary == null) {
                    _uiState.update { it.copy(loading = false, notFound = true) }
                } else {
                    val tags = summary.tags.map { it.id }
                    val baseline = EditBaseline(
                        name = summary.counter.name,
                        initialText = summary.counter.initialValue.toString(),
                        allowNegative = summary.counter.allowNegative,
                        stepText = summary.counter.defaultStep.toString(),
                        tagIds = tags,
                    )
                    _uiState.update {
                        it.copy(
                            loading = false,
                            name = baseline.name,
                            initialText = baseline.initialText,
                            allowNegative = baseline.allowNegative,
                            stepText = baseline.stepText,
                            selectedTagIds = tags,
                            currentValue = summary.currentValue,
                            baseline = baseline,
                        )
                    }
                }
            }
        }
    }

    fun setName(value: String) {
        _uiState.update { it.copy(name = value.take(CounterMath.MAX_NAME_LENGTH + 10)) }
    }

    fun setInitialText(value: String) {
        val filtered = value.filter { ch -> ch.isDigit() || ch == '-' }.take(12)
        _uiState.update { it.copy(initialText = filtered) }
    }

    fun setAllowNegative(value: Boolean) {
        _uiState.update { it.copy(allowNegative = value) }
    }

    fun setStepText(value: String) {
        _uiState.update { it.copy(stepText = value.filter { ch -> ch.isDigit() }.take(4)) }
    }

    fun toggleTag(tagId: String) {
        _uiState.update { state ->
            if (tagId in state.selectedTagIds) {
                state.copy(selectedTagIds = state.selectedTagIds - tagId)
            } else {
                state.copy(selectedTagIds = state.selectedTagIds + tagId)
            }
        }
    }

    fun setTags(tagIds: List<String>) {
        _uiState.update { it.copy(selectedTagIds = tagIds.distinct()) }
    }

    fun createTag(name: String, onResult: (TagWriteResult) -> Unit) {
        viewModelScope.launch { onResult(repository.createTag(name)) }
    }

    fun save() {
        val state = _uiState.value
        if (state.saving) return
        val initial = state.initialText.toIntOrNull()
        val step = state.stepText.toIntOrNull()
        val nameViolation = com.ltq.count.domain.CounterRules.validateName(state.name)
        when {
            nameViolation != null -> emitError(nameViolation)
            initial == null || !CounterMath.isValidInitialValue(initial) ->
                emitError(RuleViolation.InitialValueOutOfRange)
            !state.allowNegative && initial < 0 ->
                emitError(RuleViolation.InitialValueNegativeNotAllowed)
            step == null || !CounterMath.isValidStep(step) ->
                emitError(RuleViolation.StepOutOfRange)
            else -> {
                val input = CounterInput(
                    name = state.name,
                    initialValue = initial,
                    allowNegative = state.allowNegative,
                    defaultStep = step,
                    tagIds = state.selectedTagIds,
                )
                _uiState.update { it.copy(saving = true) }
                viewModelScope.launch {
                    val result = if (counterId == null) {
                        repository.createCounter(input)
                    } else {
                        repository.updateCounter(counterId, input)
                    }
                    _uiState.update { it.copy(saving = false) }
                    when (result) {
                        is CounterWriteResult.Saved -> _effects.send(EditEffect.Saved(result.counterId))
                        is CounterWriteResult.Invalid -> emitError(result.violation)
                        is CounterWriteResult.WouldGoNegative -> _effects.send(
                            EditEffect.Error(
                                R.string.edit_error_negative_current,
                                listOf(result.currentValue),
                            ),
                        )
                        CounterWriteResult.Missing -> _effects.send(
                            EditEffect.Error(R.string.value_counter_missing),
                        )
                    }
                }
            }
        }
    }

    private fun emitError(violation: RuleViolation) {
        val effect = when (violation) {
            RuleViolation.NameBlank -> EditEffect.Error(R.string.edit_error_name_blank)
            RuleViolation.NameTooLong -> EditEffect.Error(
                R.string.edit_error_name_long,
                listOf(CounterMath.MAX_NAME_LENGTH),
            )
            RuleViolation.InitialValueOutOfRange -> EditEffect.Error(
                R.string.edit_error_initial_range,
                listOf(-CounterMath.MAX_ABS_INITIAL, CounterMath.MAX_ABS_INITIAL),
            )
            RuleViolation.InitialValueNegativeNotAllowed -> EditEffect.Error(
                R.string.edit_error_initial_negative,
            )
            RuleViolation.StepOutOfRange -> EditEffect.Error(
                R.string.edit_error_step,
                listOf(CounterMath.MAX_STEP),
            )
            RuleViolation.NoteTooLong -> EditEffect.Error(
                R.string.note_too_long,
                listOf(CounterMath.MAX_NOTE_LENGTH),
            )
            else -> EditEffect.Error(R.string.error_generic)
        }
        viewModelScope.launch { _effects.send(effect) }
    }
}

fun counterEditViewModelFactory(repository: CounterRepository, counterId: String?) = viewModelFactory {
    initializer { CounterEditViewModel(repository, counterId) }
}
