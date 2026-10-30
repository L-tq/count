package com.ltq.count.domain.heatmap

import com.ltq.count.domain.model.CountEvent
import com.ltq.count.domain.model.EventSource
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeatmapCalculatorTest {
    private val shanghai = ZoneId.of("Asia/Shanghai")
    private val today = LocalDate.of(2026, 10, 30) // a Friday
    private val now = today.atTime(12, 0).atZone(shanghai).toInstant()

    @Test
    fun `day buckets follow the local zone midnight`() {
        // 2026-10-29T16:30Z is 2026-10-30 00:30 in Shanghai, but still 2026-10-29 in UTC.
        val event = event("a", 1, "2026-10-29T16:30:00Z")
        val shanghaiDay = HeatmapCalculator.bucketKey(event.timestamp, shanghai, HeatmapGranularity.DAY)
        val utcDay = HeatmapCalculator.bucketKey(event.timestamp, ZoneId.of("UTC"), HeatmapGranularity.DAY)
        assertEquals(LocalDate.of(2026, 10, 30).atStartOfDay(), shanghaiDay)
        assertEquals(LocalDate.of(2026, 10, 29).atStartOfDay(), utcDay)

        val heatmap = HeatmapCalculator.calculate(
            listOf(event),
            HeatmapMetric.EVENT_COUNT,
            HeatmapGranularity.DAY,
            shanghai,
            now,
        )
        assertEquals(1L, cell(heatmap, LocalDate.of(2026, 10, 30)).value)
        assertEquals(0L, cell(heatmap, LocalDate.of(2026, 10, 29)).value)
    }

    @Test
    fun `three metrics aggregate the same buckets differently`() {
        val events = listOf(
            event("a", 3, "2026-10-30T08:00:00Z"),
            event("b", -1, "2026-10-30T09:00:00Z"),
        )
        val date = LocalDate.of(2026, 10, 30)
        val count = HeatmapCalculator.calculate(events, HeatmapMetric.EVENT_COUNT, HeatmapGranularity.DAY, shanghai, now)
        val abs = HeatmapCalculator.calculate(events, HeatmapMetric.ABS_DELTA, HeatmapGranularity.DAY, shanghai, now)
        val net = HeatmapCalculator.calculate(events, HeatmapMetric.NET_DELTA, HeatmapGranularity.DAY, shanghai, now)

        assertEquals(2L, cell(count, date).value)
        assertEquals(4L, cell(abs, date).value)
        assertEquals(2L, cell(net, date).value)
        assertFalse(cell(net, date).negative)
    }

    @Test
    fun `week buckets start on monday in local time`() {
        val monday = event("mon", 1, "2026-10-26T00:30:00+08:00")
        val sunday = event("sun", 1, "2026-11-01T23:30:00+08:00")
        val heatmap = HeatmapCalculator.calculate(
            listOf(monday, sunday),
            HeatmapMetric.EVENT_COUNT,
            HeatmapGranularity.WEEK,
            shanghai,
            now,
        )
        assertEquals(LocalDate.of(2026, 10, 26).atStartOfDay(), cell(heatmap, LocalDate.of(2026, 10, 26)).key)
        assertEquals(2L, cell(heatmap, LocalDate.of(2026, 10, 26)).value)
        assertEquals(26, heatmap.cells.size)
    }

    @Test
    fun `month buckets split on the local month boundary`() {
        val september = event("sep", 1, "2026-09-30T23:59:00+08:00")
        val october = event("oct", 1, "2026-10-01T00:01:00+08:00")
        val heatmap = HeatmapCalculator.calculate(
            listOf(september, october),
            HeatmapMetric.EVENT_COUNT,
            HeatmapGranularity.MONTH,
            shanghai,
            now,
        )
        assertEquals(1L, cell(heatmap, LocalDate.of(2026, 9, 1)).value)
        assertEquals(1L, cell(heatmap, LocalDate.of(2026, 10, 1)).value)
        assertEquals(12, heatmap.cells.size)
    }

    @Test
    fun `minute buckets truncate to the local minute and keep sixty slots`() {
        val event = event("m", 1, "2026-10-30T14:03:40+08:00")
        val key = HeatmapCalculator.bucketKey(event.timestamp, shanghai, HeatmapGranularity.MINUTE)
        assertEquals(LocalDateTime.of(2026, 10, 30, 14, 3), key)

        val heatmap = HeatmapCalculator.calculate(
            listOf(event),
            HeatmapMetric.EVENT_COUNT,
            HeatmapGranularity.MINUTE,
            shanghai,
            Instant.parse("2026-10-30T14:03:55+08:00"),
        )
        assertEquals(60, heatmap.cells.size)
        assertEquals(key, heatmap.cells.last().key)
        assertEquals(1L, cell(heatmap, key).value)
        assertEquals(key, heatmap.rangeEnd)
        assertTrue(heatmap.weeks.isEmpty())
        assertTrue(heatmap.granularity.isIntraday)
    }

    @Test
    fun `hour buckets truncate to the local hour and drop events older than a day`() {
        val recent = event("recent", 1, "2026-10-30T13:45:00+08:00")
        val old = event("old", 1, "2026-10-29T13:59:00+08:00") // 24h02m before 14:01
        val heatmap = HeatmapCalculator.calculate(
            listOf(recent, old),
            HeatmapMetric.EVENT_COUNT,
            HeatmapGranularity.HOUR,
            shanghai,
            Instant.parse("2026-10-30T14:01:00+08:00"),
        )
        assertEquals(24, heatmap.cells.size)
        assertEquals(1, heatmap.totalEvents)
        assertEquals(LocalDateTime.of(2026, 10, 30, 14, 0), heatmap.cells.last().key)
        assertEquals(LocalDateTime.of(2026, 10, 30, 14, 59), heatmap.rangeEnd)
        assertEquals(1L, cell(heatmap, LocalDateTime.of(2026, 10, 30, 13, 0)).value)
        assertTrue(heatmap.granularity.isIntraday)
    }

    @Test
    fun `day grid is week aligned and starts on monday`() {
        val heatmap = HeatmapCalculator.calculate(
            emptyList(),
            HeatmapMetric.EVENT_COUNT,
            HeatmapGranularity.DAY,
            shanghai,
            now,
        )
        assertTrue(heatmap.weeks.isNotEmpty())
        heatmap.weeks.forEach { week -> assertEquals(7, week.size) }
        val first = heatmap.weeks.first().first()!!
        assertEquals(java.time.DayOfWeek.MONDAY, first.key.dayOfWeek)
        val last = heatmap.weeks.last().last()!!
        assertEquals(java.time.DayOfWeek.SUNDAY, last.key.dayOfWeek)
        assertFalse(heatmap.monthLabels.isEmpty())
    }

    @Test
    fun `four levels scale to the largest bucket`() {
        val events = listOf(
            event("small", 1, "2026-10-28T08:00:00Z"),
            event("big", 8, "2026-10-29T08:00:00Z"),
        )
        val heatmap = HeatmapCalculator.calculate(
            events,
            HeatmapMetric.ABS_DELTA,
            HeatmapGranularity.DAY,
            shanghai,
            now,
        )
        assertEquals(1, cell(heatmap, LocalDate.of(2026, 10, 28)).level)
        assertEquals(4, cell(heatmap, LocalDate.of(2026, 10, 29)).level)
        assertEquals(0, cell(heatmap, LocalDate.of(2026, 10, 27)).level)
    }

    @Test
    fun `net delta marks negative buckets and keeps a separate sign`() {
        val events = listOf(event("neg", -5, "2026-10-30T08:00:00Z"))
        val heatmap = HeatmapCalculator.calculate(
            events,
            HeatmapMetric.NET_DELTA,
            HeatmapGranularity.DAY,
            shanghai,
            now,
        )
        val cell = cell(heatmap, LocalDate.of(2026, 10, 30))
        assertEquals(-5L, cell.value)
        assertTrue(cell.negative)
        assertEquals(4, cell.level)
        assertTrue(heatmap.hasNegative)
    }

    @Test
    fun `events outside the rolling window are not counted`() {
        val old = event("old", 4, "2025-01-01T00:00:00Z")
        val heatmap = HeatmapCalculator.calculate(
            listOf(old),
            HeatmapMetric.EVENT_COUNT,
            HeatmapGranularity.DAY,
            shanghai,
            now,
        )
        assertEquals(0, heatmap.totalEvents)
        assertEquals(0L, heatmap.totalValue)
    }

    @Test
    fun `empty heatmap has no levels and no labels crash`() {
        val heatmap = HeatmapCalculator.calculate(
            emptyList(),
            HeatmapMetric.NET_DELTA,
            HeatmapGranularity.MONTH,
            shanghai,
            now,
        )
        assertTrue(heatmap.isEmpty)
        assertTrue(heatmap.cells.all { it.level == 0 })
        assertNull(heatmap.cells.firstOrNull { it.value != 0L })
    }

    private fun cell(heatmap: HeatmapData, key: LocalDate): HeatCell =
        heatmap.cells.first { it.key == key.atStartOfDay() }

    private fun cell(heatmap: HeatmapData, key: LocalDateTime): HeatCell =
        heatmap.cells.first { it.key == key }

    private fun event(id: String, delta: Int, at: String) = CountEvent(
        id = id,
        counterId = "c1",
        delta = delta,
        timestamp = Instant.parse(at),
        source = EventSource.MANUAL,
    )
}
