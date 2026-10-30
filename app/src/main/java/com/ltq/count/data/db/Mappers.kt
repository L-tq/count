package com.ltq.count.data.db

import com.ltq.count.domain.model.CountEvent
import com.ltq.count.domain.model.Counter
import com.ltq.count.domain.model.EventSource
import com.ltq.count.domain.model.Tag
import java.time.Instant

fun CounterEntity.toDomain(tagIds: List<String> = emptyList()): Counter = Counter(
    id = id,
    name = name,
    initialValue = initialValue,
    allowNegative = allowNegative,
    createdAt = Instant.ofEpochMilli(createdAt),
    sortOrder = sortOrder,
    pinned = pinned,
    defaultStep = defaultStep,
    tagIds = tagIds,
)

fun CounterAggregateRow.toDomain(tagIds: List<String> = emptyList()): Counter = Counter(
    id = id,
    name = name,
    initialValue = initialValue,
    allowNegative = allowNegative,
    createdAt = Instant.ofEpochMilli(createdAt),
    sortOrder = sortOrder,
    pinned = pinned,
    defaultStep = defaultStep,
    tagIds = tagIds,
)

fun Counter.toEntity(): CounterEntity = CounterEntity(
    id = id,
    name = name,
    initialValue = initialValue,
    allowNegative = allowNegative,
    createdAt = createdAt.toEpochMilli(),
    sortOrder = sortOrder,
    pinned = pinned,
    defaultStep = defaultStep,
)

fun CountEventEntity.toDomain(): CountEvent = CountEvent(
    id = id,
    counterId = counterId,
    delta = delta,
    timestamp = Instant.ofEpochMilli(timestamp),
    note = note,
    source = EventSource.parse(source) ?: EventSource.MANUAL,
    sessionId = sessionId,
)

fun CountEvent.toEntity(): CountEventEntity = CountEventEntity(
    id = id,
    counterId = counterId,
    delta = delta,
    timestamp = timestamp.toEpochMilli(),
    note = note,
    source = source.name,
    sessionId = sessionId,
)

fun TagEntity.toDomain(): Tag = Tag(
    id = id,
    name = name,
    createdAt = Instant.ofEpochMilli(createdAt),
)
