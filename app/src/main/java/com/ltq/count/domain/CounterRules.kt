package com.ltq.count.domain

import com.ltq.count.domain.model.CountEvent
import com.ltq.count.domain.model.Counter
import java.time.Instant

/** Validation shared by the create/edit form, the repository and the JSON importer. */
enum class RuleViolation {
    NameBlank,
    NameTooLong,
    InitialValueOutOfRange,
    InitialValueNegativeNotAllowed,
    StepOutOfRange,
    DeltaZero,
    DeltaOutOfRange,
    NoteTooLong,
    HistoryNegative,
}

object CounterRules {
    fun validateName(raw: String): RuleViolation? {
        val name = raw.trim()
        return when {
            name.isEmpty() -> RuleViolation.NameBlank
            name.length > CounterMath.MAX_NAME_LENGTH -> RuleViolation.NameTooLong
            else -> null
        }
    }

    fun validateInitialValue(value: Int, allowNegative: Boolean): RuleViolation? = when {
        !CounterMath.isValidInitialValue(value) -> RuleViolation.InitialValueOutOfRange
        !allowNegative && value < 0 -> RuleViolation.InitialValueNegativeNotAllowed
        else -> null
    }

    fun validateStep(step: Int): RuleViolation? =
        if (CounterMath.isValidStep(step)) null else RuleViolation.StepOutOfRange

    fun validateDelta(delta: Int): RuleViolation? = when {
        delta == 0 -> RuleViolation.DeltaZero
        !CounterMath.isValidDelta(delta) -> RuleViolation.DeltaOutOfRange
        else -> null
    }

    fun validateNote(note: String?): RuleViolation? =
        if (note != null && note.length > CounterMath.MAX_NOTE_LENGTH) RuleViolation.NoteTooLong else null

    /** A counter definition plus its planned event stream is consistent. */
    fun validateCounter(counter: Counter, events: List<CountEvent>): RuleViolation? {
        validateName(counter.name)?.let { return it }
        validateInitialValue(counter.initialValue, counter.allowNegative)?.let { return it }
        validateStep(counter.defaultStep)?.let { return it }
        var running = counter.initialValue.toLong()
        for (event in events.sortedBy { it.timestamp }) {
            validateDelta(event.delta)?.let { return it }
            running += event.delta.toLong()
            if (!counter.allowNegative && running < 0) return RuleViolation.HistoryNegative
        }
        return null
    }
}

/** Inclusive quick-adjust results returned to the UI. */
sealed interface AdjustOutcome {
    data class Applied(val event: CountEvent, val newValue: Long) : AdjustOutcome
    data class BlockedByNegative(val currentValue: Long, val attemptedDelta: Int) : AdjustOutcome
    data class Invalid(val violation: RuleViolation) : AdjustOutcome
    data object CounterMissing : AdjustOutcome
    data object AlreadyAtBaseline : AdjustOutcome
}

sealed interface UndoOutcome {
    data class Applied(val reversedEventIds: List<String>, val newValue: Long) : UndoOutcome
    data class BlockedByNegative(val currentValue: Long) : UndoOutcome
    data object NothingToUndo : UndoOutcome
    data object EventMissing : UndoOutcome
    data object NotLatest : UndoOutcome
}

/** Event ordering used everywhere: oldest first, ties broken by id for determinism. */
val EventOrder: Comparator<CountEvent> =
    compareBy<CountEvent> { it.timestamp }.thenBy { it.id }

fun List<CountEvent>.sortedChronologically(): List<CountEvent> = sortedWith(EventOrder)
