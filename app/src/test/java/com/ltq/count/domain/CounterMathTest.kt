package com.ltq.count.domain

import com.ltq.count.domain.model.CountEvent
import com.ltq.count.domain.model.EventSource
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CounterMathTest {
    @Test
    fun `current value is initial value plus every delta`() {
        val events = listOf(
            event("a", delta = 3, at = "2026-01-01T00:00:00Z"),
            event("b", delta = -5, at = "2026-01-02T00:00:00Z"),
            event("c", delta = 2, at = "2026-01-03T00:00:00Z"),
        )
        assertEquals(0L, CounterMath.currentValueFromEvents(0, events))
        assertEquals(-10L, CounterMath.currentValueFromEvents(-10, events))
        assertEquals(-5L, CounterMath.currentValueFromEvents(-5, emptyList()))
    }

    @Test
    fun `aggregate survives many negative deltas`() {
        val events = (1..1000).map { event("e$it", delta = -1, at = "2026-01-01T00:00:00Z") }
        assertEquals(-1000L, CounterMath.currentValueFromEvents(0, events))
    }

    @Test
    fun `non negative counters reject a delta that would cross zero`() {
        assertTrue(CounterMath.wouldGoNegative(currentValue = 2, delta = -3, allowNegative = false))
        assertFalse(CounterMath.wouldGoNegative(currentValue = 3, delta = -3, allowNegative = false))
        assertFalse(CounterMath.wouldGoNegative(currentValue = 1, delta = -100, allowNegative = true))
    }

    @Test
    fun `reset delta restores the baseline without touching history`() {
        assertEquals(-7, CounterMath.resetDelta(initialValue = 0, currentValue = 7))
        assertEquals(12, CounterMath.resetDelta(initialValue = 5, currentValue = -7))
        assertEquals(0, CounterMath.resetDelta(initialValue = -3, currentValue = -3))
    }

    @Test
    fun `undo delta is the exact opposite`() {
        assertEquals(-4, CounterMath.reverseDelta(4))
        assertEquals(9, CounterMath.reverseDelta(-9))
        assertNull(CounterMath.reverseDelta(Int.MIN_VALUE))
    }

    @Test
    fun `delta validation rejects zero and unbounded values`() {
        assertEquals(RuleViolation.DeltaZero, CounterRules.validateDelta(0))
        assertEquals(RuleViolation.DeltaOutOfRange, CounterRules.validateDelta(CounterMath.MAX_ABS_DELTA + 1))
        assertNull(CounterRules.validateDelta(1))
        assertNull(CounterRules.validateDelta(-CounterMath.MAX_ABS_DELTA))
    }

    @Test
    fun `event order is deterministic for identical timestamps`() {
        val same = "2026-05-05T10:00:00Z"
        val ordered = listOf(
            event("b", delta = 1, at = same),
            event("a", delta = 2, at = same),
        ).sortedChronologically()
        assertEquals(listOf("a", "b"), ordered.map { it.id })
    }

    private fun event(id: String, delta: Int, at: String) = CountEvent(
        id = id,
        counterId = "c1",
        delta = delta,
        timestamp = Instant.parse(at),
        source = EventSource.MANUAL,
    )
}
