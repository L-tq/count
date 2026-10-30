package com.ltq.count.domain.model

import java.time.Instant

/** Immutable, event-sourced counter definition. The current value is never stored. */
data class Counter(
    val id: String,
    val name: String,
    val initialValue: Int,
    val allowNegative: Boolean,
    val createdAt: Instant,
    val sortOrder: Int,
    val pinned: Boolean,
    val defaultStep: Int,
    val tagIds: List<String> = emptyList(),
)

/** A single immutable change of a counter. Values are never edited or deleted. */
data class CountEvent(
    val id: String,
    val counterId: String,
    val delta: Int,
    val timestamp: Instant,
    val note: String? = null,
    val source: EventSource = EventSource.MANUAL,
    val sessionId: String? = null,
)

enum class EventSource {
    MANUAL,
    WIDGET,
    SHORTCUT,
    RESET,
    UNDO;

    companion object {
        fun parse(raw: String): EventSource? = entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
    }
}

data class Tag(
    val id: String,
    val name: String,
    val createdAt: Instant,
)

/** Home-screen projection: counter plus its aggregated value and tags. */
data class CounterSummary(
    val counter: Counter,
    val currentValue: Long,
    val eventCount: Int,
    val tags: List<Tag>,
)
