package com.ltq.count.ui.util

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Month
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Presentation-only formatting. Persisted timestamps stay UTC instants. */
object CountFormat {
    fun eventTime(instant: Instant, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofPattern("MM-dd HH:mm", locale).withZone(zone).format(instant)

    fun eventDate(instant: Instant, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofPattern("MM-dd", locale).withZone(zone).format(instant)

    fun eventClock(instant: Instant, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofPattern("HH:mm", locale).withZone(zone).format(instant)

    fun fullTime(instant: Instant, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", locale).withZone(zone).format(instant)

    fun date(date: LocalDate, locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofPattern("yyyy-MM-dd", locale).format(date)

    fun monthDay(date: LocalDate, locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofPattern("M/d", locale).format(date)

    /** Bucket labels for intraday granularities; the date is carried by the range header. */
    fun bucketClock(time: LocalDateTime, locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofPattern("HH:mm", locale).format(time)

    fun bucketDateTime(time: LocalDateTime, locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofPattern("MM-dd HH:mm", locale).format(time)

    fun signedLong(delta: Long): String = if (delta > 0) "+$delta" else delta.toString()

    fun monthShort(month: Int, locale: Locale = Locale.getDefault()): String =
        Month.of(month).getDisplayName(TextStyle.SHORT, locale)

    fun weekdayNarrow(day: DayOfWeek, locale: Locale = Locale.getDefault()): String =
        day.getDisplayName(TextStyle.NARROW, locale)

    fun signed(delta: Int): String = if (delta > 0) "+$delta" else delta.toString()
}
