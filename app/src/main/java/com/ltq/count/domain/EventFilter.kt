package com.ltq.count.domain

import com.ltq.count.domain.model.CountEvent
import java.time.Duration
import java.time.Instant

enum class TimeRangeFilter { ALL, LAST_7_DAYS, LAST_30_DAYS, LAST_90_DAYS }

enum class SignFilter { ANY, POSITIVE, NEGATIVE }

enum class NoteFilter { ANY, WITH_NOTE, WITHOUT_NOTE }

data class EventFilter(
    val timeRange: TimeRangeFilter = TimeRangeFilter.ALL,
    val sign: SignFilter = SignFilter.ANY,
    val note: NoteFilter = NoteFilter.ANY,
) {
    val isDefault: Boolean
        get() = timeRange == TimeRangeFilter.ALL && sign == SignFilter.ANY && note == NoteFilter.ANY
}

object EventFilters {
    fun apply(events: List<CountEvent>, filter: EventFilter, now: Instant): List<CountEvent> {
        val cutoff: Instant? = when (filter.timeRange) {
            TimeRangeFilter.ALL -> null
            TimeRangeFilter.LAST_7_DAYS -> now.minus(Duration.ofDays(7))
            TimeRangeFilter.LAST_30_DAYS -> now.minus(Duration.ofDays(30))
            TimeRangeFilter.LAST_90_DAYS -> now.minus(Duration.ofDays(90))
        }
        return events.filter { event ->
            (cutoff == null || !event.timestamp.isBefore(cutoff)) &&
                when (filter.sign) {
                    SignFilter.ANY -> true
                    SignFilter.POSITIVE -> event.delta > 0
                    SignFilter.NEGATIVE -> event.delta < 0
                } &&
                when (filter.note) {
                    NoteFilter.ANY -> true
                    NoteFilter.WITH_NOTE -> !event.note.isNullOrBlank()
                    NoteFilter.WITHOUT_NOTE -> event.note.isNullOrBlank()
                }
        }
    }
}
