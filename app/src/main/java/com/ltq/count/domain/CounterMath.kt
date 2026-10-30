package com.ltq.count.domain

import com.ltq.count.domain.model.CountEvent

/**
 * Pure arithmetic rules of the event-sourced model.
 *
 * A counter's current value is always `initialValue + sum(events.delta)`; nothing in this
 * object mutates history, it only proposes deltas for the repository to append.
 */
object CounterMath {
    const val MAX_ABS_INITIAL = 1_000_000_000
    const val MAX_ABS_DELTA = 1_000_000_000
    const val MAX_STEP = 1_000
    const val MAX_NAME_LENGTH = 60
    const val MAX_NOTE_LENGTH = 500
    const val MAX_TAG_NAME_LENGTH = 20

    fun currentValue(initialValue: Int, deltas: Iterable<Int>): Long {
        var sum = initialValue.toLong()
        for (delta in deltas) sum += delta.toLong()
        return sum
    }

    fun currentValueFromEvents(initialValue: Int, events: Iterable<CountEvent>): Long =
        currentValue(initialValue, events.map { it.delta })

    fun wouldGoNegative(currentValue: Long, delta: Int, allowNegative: Boolean): Boolean =
        !allowNegative && currentValue + delta.toLong() < 0L

    /**
     * Delta that turns [currentValue] back into [initialValue]. Returns null when the delta
     * cannot be represented as an Int (practically unreachable because deltas are bounded).
     * Returns 0 when the counter already sits on its baseline.
     */
    fun resetDelta(initialValue: Int, currentValue: Long): Int? {
        val delta = initialValue.toLong() - currentValue
        return if (delta in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) delta.toInt() else null
    }

    /** Reverse event delta used by undo. Null only for the pathological Int.MIN_VALUE delta. */
    fun reverseDelta(delta: Int): Int? = if (delta == Int.MIN_VALUE) null else -delta

    fun isValidDelta(delta: Int): Boolean = delta != 0 && delta in -MAX_ABS_DELTA..MAX_ABS_DELTA

    fun isValidInitialValue(value: Int): Boolean = value in -MAX_ABS_INITIAL..MAX_ABS_INITIAL

    fun isValidStep(step: Int): Boolean = step in 1..MAX_STEP
}
