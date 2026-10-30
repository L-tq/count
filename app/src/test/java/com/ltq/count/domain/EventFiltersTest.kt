package com.ltq.count.domain

import com.ltq.count.domain.model.CountEvent
import com.ltq.count.domain.model.EventSource
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EventFiltersTest {
    private val now = Instant.parse("2026-10-30T00:00:00Z")
    private val events = listOf(
        event("today", 3, "2026-10-29T12:00:00Z", note = "早上"),
        event("week", -2, "2026-10-26T12:00:00Z", note = null),
        event("month", 1, "2026-10-05T12:00:00Z", note = "  "),
        event("old", 5, "2026-06-01T12:00:00Z", note = null),
    )

    @Test
    fun `time range filters keep the inclusive cutoff`() {
        val filtered = EventFilters.apply(events, EventFilter(timeRange = TimeRangeFilter.LAST_7_DAYS), now)
        assertEquals(setOf("today", "week"), filtered.map { it.id }.toSet())
    }

    @Test
    fun `sign filter only returns matching deltas`() {
        assertEquals(
            setOf("today", "month", "old"),
            EventFilters.apply(events, EventFilter(sign = SignFilter.POSITIVE), now).map { it.id }.toSet(),
        )
        assertEquals(
            setOf("week"),
            EventFilters.apply(events, EventFilter(sign = SignFilter.NEGATIVE), now).map { it.id }.toSet(),
        )
    }

    @Test
    fun `note filter treats blank notes as missing`() {
        assertEquals(
            setOf("today"),
            EventFilters.apply(events, EventFilter(note = NoteFilter.WITH_NOTE), now).map { it.id }.toSet(),
        )
        val without = EventFilters.apply(events, EventFilter(note = NoteFilter.WITHOUT_NOTE), now)
        assertTrue(without.any { it.id == "month" })
        assertTrue(without.any { it.id == "old" })
    }

    @Test
    fun `combined filters intersect`() {
        val filtered = EventFilters.apply(
            events,
            EventFilter(TimeRangeFilter.LAST_30_DAYS, SignFilter.POSITIVE, NoteFilter.WITH_NOTE),
            now,
        )
        assertEquals(listOf("today"), filtered.map { it.id })
    }

    @Test
    fun `default filter keeps everything`() {
        assertEquals(events, EventFilters.apply(events, EventFilter(), now))
        assertTrue(EventFilter().isDefault)
    }

    private fun event(id: String, delta: Int, at: String, note: String?) = CountEvent(
        id = id,
        counterId = "c1",
        delta = delta,
        timestamp = Instant.parse(at),
        note = note,
        source = EventSource.MANUAL,
    )
}
