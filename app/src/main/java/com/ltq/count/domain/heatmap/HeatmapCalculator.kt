package com.ltq.count.domain.heatmap

import com.ltq.count.domain.model.CountEvent
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

enum class HeatmapMetric { EVENT_COUNT, ABS_DELTA, NET_DELTA }

/** [isIntraday] selects clock-labelled buckets (minute/hour) instead of calendar buckets. */
enum class HeatmapGranularity(val isIntraday: Boolean) {
    MINUTE(true),
    HOUR(true),
    DAY(false),
    WEEK(false),
    MONTH(false),
}

/** One rendered bucket. [level] is 0 for empty, 1..4 for increasing magnitude. */
data class HeatCell(
    val key: LocalDateTime,
    val value: Long,
    val eventCount: Int,
    val level: Int,
    val negative: Boolean,
)

data class MonthLabel(val weekIndex: Int, val month: Int)

/**
 * Full render description for the activity heatmap.
 *
 * [weeks] is only populated for the DAY granularity: each entry is one week column with seven
 * rows (Monday..Sunday); null rows are padding outside the visible range.
 *
 * [rangeStart] and [rangeEnd] describe the visible window inclusively: calendar granularities
 * keep their date at midnight, minute ends at the current minute, and hour ends at :59.
 */
data class HeatmapData(
    val granularity: HeatmapGranularity,
    val metric: HeatmapMetric,
    val cells: List<HeatCell>,
    val weeks: List<List<HeatCell?>>,
    val monthLabels: List<MonthLabel>,
    val maxMagnitude: Long,
    val totalValue: Long,
    val totalEvents: Int,
    val rangeStart: LocalDateTime,
    val rangeEnd: LocalDateTime,
    val hasNegative: Boolean,
) {
    val isEmpty: Boolean get() = totalEvents == 0
}

/**
 * Buckets events by the user's local time zone. Events are stored as UTC instants; only this
 * projection depends on the zone, which keeps the persistence layer zone independent.
 *
 * Minute and hour granularities are rolling windows anchored at [calculate]'s `now`: the last
 * 60 minutes and the last 24 hours respect the device clock instead of the calendar.
 */
object HeatmapCalculator {
    private const val DAY_GRID_DAYS = 364L
    private const val WEEK_BUCKETS = 26
    private const val MONTH_BUCKETS = 12
    private const val HOUR_BUCKETS = 24
    private const val MINUTE_BUCKETS = 60
    private const val LEVELS = 4

    fun bucketKey(instant: Instant, zone: ZoneId, granularity: HeatmapGranularity): LocalDateTime {
        val local = instant.atZone(zone).toLocalDateTime()
        return when (granularity) {
            HeatmapGranularity.MINUTE -> local.truncatedTo(ChronoUnit.MINUTES)
            HeatmapGranularity.HOUR -> local.truncatedTo(ChronoUnit.HOURS)
            HeatmapGranularity.DAY -> local.toLocalDate().atStartOfDay()
            HeatmapGranularity.WEEK -> local.toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .atStartOfDay()

            HeatmapGranularity.MONTH -> local.toLocalDate().withDayOfMonth(1).atStartOfDay()
        }
    }

    fun calculate(
        events: List<CountEvent>,
        metric: HeatmapMetric,
        granularity: HeatmapGranularity,
        zone: ZoneId,
        now: Instant,
    ): HeatmapData {
        val (rangeStart, rangeEnd, keys) = rangeFor(granularity, now.atZone(zone))
        val buckets = LinkedHashMap<LocalDateTime, MutableBucket>()
        for (key in keys) buckets[key] = MutableBucket()
        for (event in events) {
            val key = bucketKey(event.timestamp, zone, granularity)
            buckets[key]?.add(event)
        }

        val counters = buckets.map { (key, bucket) ->
            HeatCell(
                key = key,
                value = bucket.value(metric),
                eventCount = bucket.count,
                level = 0,
                negative = metric == HeatmapMetric.NET_DELTA && bucket.net < 0,
            )
        }
        val maxMagnitude = counters.maxOfOrNull { kotlin.math.abs(it.value) } ?: 0L
        val leveled = counters.map { it.copy(level = levelOf(it.value, maxMagnitude)) }

        val weeks: List<List<HeatCell?>> = if (granularity == HeatmapGranularity.DAY) {
            buildWeeks(rangeStart, rangeEnd, leveled)
        } else {
            emptyList()
        }
        val monthLabels = if (granularity == HeatmapGranularity.DAY) {
            buildMonthLabels(weeks, rangeStart)
        } else {
            emptyList()
        }

        return HeatmapData(
            granularity = granularity,
            metric = metric,
            cells = leveled,
            weeks = weeks,
            monthLabels = monthLabels,
            maxMagnitude = maxMagnitude,
            totalValue = leveled.sumOf { it.value },
            totalEvents = buckets.values.sumOf { it.count },
            rangeStart = rangeStart,
            rangeEnd = rangeEnd,
            hasNegative = leveled.any { it.value < 0 },
        )
    }

    private fun levelOf(value: Long, maxMagnitude: Long): Int {
        val magnitude = kotlin.math.abs(value)
        if (magnitude == 0L || maxMagnitude == 0L) return 0
        val raw = kotlin.math.ceil(LEVELS * magnitude.toDouble() / maxMagnitude.toDouble()).toInt()
        return raw.coerceIn(1, LEVELS)
    }

    private data class Range(
        val start: LocalDateTime,
        val end: LocalDateTime,
        val keys: List<LocalDateTime>,
    )

    private fun rangeFor(granularity: HeatmapGranularity, now: ZonedDateTime): Range {
        val today = now.toLocalDate()
        return when (granularity) {
            HeatmapGranularity.MINUTE -> {
                val currentMinute = now.toLocalDateTime().truncatedTo(ChronoUnit.MINUTES)
                val start = currentMinute.minusMinutes((MINUTE_BUCKETS - 1).toLong())
                val keys = (0 until MINUTE_BUCKETS).map { start.plusMinutes(it.toLong()) }
                Range(start, currentMinute, keys)
            }

            HeatmapGranularity.HOUR -> {
                val currentHour = now.toLocalDateTime().truncatedTo(ChronoUnit.HOURS)
                val start = currentHour.minusHours((HOUR_BUCKETS - 1).toLong())
                val keys = (0 until HOUR_BUCKETS).map { start.plusHours(it.toLong()) }
                Range(start, currentHour.plusMinutes(59), keys)
            }

            HeatmapGranularity.DAY -> {
                val start = today.minusDays(DAY_GRID_DAYS)
                val gridStart = start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val gridEnd = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
                val keys = generateSequence(gridStart) { it.plusDays(1) }
                    .takeWhile { !it.isAfter(gridEnd) }
                    .map { it.atStartOfDay() }
                    .toList()
                Range(gridStart.atStartOfDay(), gridEnd.atStartOfDay(), keys)
            }

            HeatmapGranularity.WEEK -> {
                val currentWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val start = currentWeek.minusWeeks((WEEK_BUCKETS - 1).toLong())
                val keys = (0 until WEEK_BUCKETS).map { start.plusWeeks(it.toLong()).atStartOfDay() }
                Range(start.atStartOfDay(), currentWeek.plusDays(6).atStartOfDay(), keys)
            }

            HeatmapGranularity.MONTH -> {
                val currentMonth = YearMonth.from(today)
                val start = currentMonth.minusMonths((MONTH_BUCKETS - 1).toLong()).atDay(1)
                val keys = (0 until MONTH_BUCKETS).map { start.plusMonths(it.toLong()).atStartOfDay() }
                Range(start.atStartOfDay(), currentMonth.atEndOfMonth().atStartOfDay(), keys)
            }
        }
    }

    private fun buildWeeks(
        rangeStart: LocalDateTime,
        rangeEnd: LocalDateTime,
        cells: List<HeatCell>,
    ): List<List<HeatCell?>> {
        val byKey = cells.associateBy { it.key }
        val endDate = rangeEnd.toLocalDate()
        val weeks = mutableListOf<List<HeatCell?>>()
        var cursor = rangeStart.toLocalDate()
        while (!cursor.isAfter(endDate)) {
            val column = (0 until 7).map { offset ->
                val date = cursor.plusDays(offset.toLong())
                if (date.isAfter(endDate)) null else byKey[date.atStartOfDay()]
            }
            weeks += column
            cursor = cursor.plusWeeks(1)
        }
        return weeks
    }

    private fun buildMonthLabels(weeks: List<List<HeatCell?>>, rangeStart: LocalDateTime): List<MonthLabel> {
        val labels = mutableListOf<MonthLabel>()
        var lastMonth = -1
        weeks.forEachIndexed { index, column ->
            val first = column.firstOrNull { it != null } ?: return@forEachIndexed
            val month = first.key.monthValue
            if (month != lastMonth) {
                // Avoid a cramped label on the very first column.
                if (index > 0) labels += MonthLabel(index, month)
                lastMonth = month
            }
        }
        if (labels.isEmpty()) {
            labels += MonthLabel(0, rangeStart.monthValue)
        }
        return labels
    }

    private class MutableBucket {
        var net: Long = 0
        var absolute: Long = 0
        var count: Int = 0
        var netSum: Long = 0

        fun add(event: CountEvent) {
            count += 1
            netSum += event.delta.toLong()
            net = netSum
            absolute += kotlin.math.abs(event.delta.toLong())
        }

        fun value(metric: HeatmapMetric): Long = when (metric) {
            HeatmapMetric.EVENT_COUNT -> count.toLong()
            HeatmapMetric.ABS_DELTA -> absolute
            HeatmapMetric.NET_DELTA -> net
        }
    }
}
