package com.ltq.count.ui.quick

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ltq.count.R
import com.ltq.count.data.repo.CounterRepository
import com.ltq.count.domain.AdjustOutcome
import com.ltq.count.domain.model.CounterSummary
import com.ltq.count.domain.model.EventSource
import java.util.UUID
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class QuickUiState(
    val loading: Boolean = true,
    val summary: CounterSummary? = null,
    val sessionDelta: Long = 0,
    val step: Int = 1,
)

sealed interface QuickEffect {
    data class Message(val messageRes: Int, val args: List<Any> = emptyList()) : QuickEffect
}

class QuickCountViewModel(
    private val counterId: String,
    private val repository: CounterRepository,
) : ViewModel() {
    private val sessionId: String = UUID.randomUUID().toString()
    private val sessionDelta = MutableStateFlow(0L)
    private val stepOverride = MutableStateFlow<Int?>(null)

    private val _effects = Channel<QuickEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    val uiState: StateFlow<QuickUiState> = combine(
        repository.observeCounter(counterId),
        sessionDelta,
        stepOverride,
    ) { summary, delta, override ->
        QuickUiState(
            loading = false,
            summary = summary,
            sessionDelta = delta,
            step = override ?: summary?.counter?.defaultStep ?: 1,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), QuickUiState())

    fun setStep(step: Int) {
        if (step in 1..com.ltq.count.domain.CounterMath.MAX_STEP) stepOverride.value = step
    }

    fun adjust(direction: Int, source: EventSource) {
        val step = uiState.value.step
        val delta = direction * step
        viewModelScope.launch {
            when (val outcome = repository.adjust(counterId, delta, source, sessionId)) {
                is AdjustOutcome.Applied -> sessionDelta.update { it + delta }
                is AdjustOutcome.BlockedByNegative -> _effects.send(
                    QuickEffect.Message(
                        R.string.value_blocked_negative,
                        listOf(outcome.currentValue, delta),
                    ),
                )
                is AdjustOutcome.Invalid -> _effects.send(QuickEffect.Message(R.string.value_invalid))
                AdjustOutcome.CounterMissing -> _effects.send(QuickEffect.Message(R.string.value_counter_missing))
                AdjustOutcome.AlreadyAtBaseline -> _effects.send(QuickEffect.Message(R.string.value_already_baseline))
            }
        }
    }
}

fun quickCountViewModelFactory(counterId: String, repository: CounterRepository) = viewModelFactory {
    initializer { QuickCountViewModel(counterId, repository) }
}
