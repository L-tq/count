package com.ltq.count.domain

import com.ltq.count.domain.model.CountEvent
import com.ltq.count.domain.model.Counter
import com.ltq.count.domain.model.EventSource
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CounterRulesTest {
    @Test
    fun `blank and overlong names are rejected`() {
        assertEquals(RuleViolation.NameBlank, CounterRules.validateName(" "))
        assertEquals(RuleViolation.NameTooLong, CounterRules.validateName("x".repeat(CounterMath.MAX_NAME_LENGTH + 1)))
        assertNull(CounterRules.validateName("喝水"))
    }

    @Test
    fun `negative initial value requires allowNegative`() {
        assertEquals(RuleViolation.InitialValueNegativeNotAllowed, CounterRules.validateInitialValue(-1, false))
        assertNull(CounterRules.validateInitialValue(-1, true))
    }

    @Test
    fun `step must stay inside supported range`() {
        assertEquals(RuleViolation.StepOutOfRange, CounterRules.validateStep(0))
        assertEquals(RuleViolation.StepOutOfRange, CounterRules.validateStep(CounterMath.MAX_STEP + 1))
        assertNull(CounterRules.validateStep(1))
        assertNull(CounterRules.validateStep(10))
    }

    @Test
    fun `any number of tags is allowed`() {
        val tagged = counter(initialValue = 0, allowNegative = true)
            .copy(tagIds = listOf("a", "b", "c", "d"))
        assertNull(CounterRules.validateCounter(tagged, emptyList()))
    }

    @Test
    fun `history that dips below zero is rejected only when negative values are disallowed`() {
        val events = listOf(
            event("1", delta = 5, at = "2026-01-01T00:00:00Z"),
            event("2", delta = -6, at = "2026-01-02T00:00:00Z"),
        )
        val strict = counter(initialValue = 0, allowNegative = false)
        val relaxed = counter(initialValue = 0, allowNegative = true)
        assertEquals(RuleViolation.HistoryNegative, CounterRules.validateCounter(strict, events))
        assertNull(CounterRules.validateCounter(relaxed, events))
    }

    @Test
    fun `notes longer than the limit are rejected`() {
        assertEquals(
            RuleViolation.NoteTooLong,
            CounterRules.validateNote("n".repeat(CounterMath.MAX_NOTE_LENGTH + 1)),
        )
        assertNull(CounterRules.validateNote(null))
    }

    private fun counter(initialValue: Int, allowNegative: Boolean) = Counter(
        id = "c1",
        name = "Test",
        initialValue = initialValue,
        allowNegative = allowNegative,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        sortOrder = 0,
        pinned = false,
        defaultStep = 1,
    )

    private fun event(id: String, delta: Int, at: String) = CountEvent(
        id = id,
        counterId = "c1",
        delta = delta,
        timestamp = Instant.parse(at),
        source = EventSource.MANUAL,
    )
}
