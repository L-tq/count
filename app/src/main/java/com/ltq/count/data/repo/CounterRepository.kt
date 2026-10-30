package com.ltq.count.data.repo

import androidx.room.withTransaction
import com.ltq.count.data.db.CountDatabase
import com.ltq.count.data.db.CountEventEntity
import com.ltq.count.data.db.CounterEntity
import com.ltq.count.data.db.CounterTagEntity
import com.ltq.count.data.db.TagEntity
import com.ltq.count.data.db.toDomain
import com.ltq.count.data.db.toEntity
import com.ltq.count.domain.AdjustOutcome
import com.ltq.count.domain.CounterMath
import com.ltq.count.domain.CounterRules
import com.ltq.count.domain.RuleViolation
import com.ltq.count.domain.UndoOutcome
import com.ltq.count.domain.backup.BackupCodec
import com.ltq.count.domain.backup.ExistingSnapshot
import com.ltq.count.domain.backup.ImportPlan
import com.ltq.count.domain.model.CountEvent
import com.ltq.count.domain.model.Counter
import com.ltq.count.domain.model.CounterSummary
import com.ltq.count.domain.model.EventSource
import com.ltq.count.domain.model.Tag
import java.time.Clock
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

data class CounterInput(
    val name: String,
    val initialValue: Int,
    val allowNegative: Boolean,
    val defaultStep: Int,
    val tagIds: List<String> = emptyList(),
) {
    fun normalized(): CounterInput = copy(name = name.trim(), tagIds = tagIds.distinct())
}

sealed interface CounterWriteResult {
    data class Saved(val counterId: String) : CounterWriteResult
    data class Invalid(val violation: RuleViolation) : CounterWriteResult
    data class WouldGoNegative(val currentValue: Long) : CounterWriteResult
    data object Missing : CounterWriteResult
}

sealed interface TagWriteResult {
    data class Saved(val tag: Tag) : TagWriteResult
    data class Existing(val tag: Tag) : TagWriteResult
    data class Invalid(val violation: RuleViolation) : TagWriteResult
}

data class ExportPayload(
    val json: String,
    val csv: String,
    val counterCount: Int,
    val eventCount: Int,
    val tagCount: Int,
)

/**
 * Single source of truth for writes. Every mutation that changes a counter value appends an
 * immutable event inside one database transaction; validation and aggregation happen there too,
 * so concurrent quick taps cannot observe a stale value.
 */
class CounterRepository(
    private val db: CountDatabase,
    private val clock: Clock = Clock.systemUTC(),
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val counters = db.counterDao()
    private val events = db.eventDao()
    private val tags = db.tagDao()
    private val links = db.counterTagDao()

    val currentInstant: Instant get() = clock.instant()

    fun observeSummaries(): Flow<List<CounterSummary>> =
        combine(counters.observeAggregates(), links.observeLinks()) { rows, tagRows ->
            val byCounter = tagRows.groupBy { it.counterId }
            rows.map { row ->
                val rowTags = byCounter[row.id].orEmpty().map {
                    Tag(it.tagId, it.tagName, Instant.ofEpochMilli(it.tagCreatedAt))
                }
                CounterSummary(
                    counter = row.toDomain(rowTags.map { it.id }),
                    currentValue = row.initialValue.toLong() + row.deltaSum,
                    eventCount = row.eventCount,
                    tags = rowTags,
                )
            }
        }

    fun observeCounter(counterId: String): Flow<CounterSummary?> =
        combine(counters.observeAggregate(counterId), links.observeLinks()) { row, tagRows ->
            row?.let {
                val rowTags = tagRows.filter { link -> link.counterId == counterId }.map { link ->
                    Tag(link.tagId, link.tagName, Instant.ofEpochMilli(link.tagCreatedAt))
                }
                CounterSummary(
                    counter = it.toDomain(rowTags.map { tag -> tag.id }),
                    currentValue = it.initialValue.toLong() + it.deltaSum,
                    eventCount = it.eventCount,
                    tags = rowTags,
                )
            }
        }

    fun observeEvents(counterId: String): Flow<List<CountEvent>> =
        events.observeForCounter(counterId).map { list -> list.map { it.toDomain() } }

    fun observeTags(): Flow<List<Tag>> = tags.observeTags().map { list -> list.map { it.toDomain() } }

    suspend fun createCounter(input: CounterInput): CounterWriteResult = db.withTransaction {
        val normalized = input.normalized()
        CounterRules.validateName(normalized.name)?.let { return@withTransaction CounterWriteResult.Invalid(it) }
        CounterRules.validateInitialValue(normalized.initialValue, normalized.allowNegative)
            ?.let { return@withTransaction CounterWriteResult.Invalid(it) }
        CounterRules.validateStep(normalized.defaultStep)
            ?.let { return@withTransaction CounterWriteResult.Invalid(it) }

        val validTagIds = filterKnownTagIds(normalized.tagIds)
        val id = newId()
        val sortOrder = counters.maxNormalSortOrder() + 1
        val now = clock.instant()
        counters.insert(
            CounterEntity(
                id = id,
                name = normalized.name,
                initialValue = normalized.initialValue,
                allowNegative = normalized.allowNegative,
                createdAt = now.toEpochMilli(),
                sortOrder = sortOrder,
                pinned = false,
                defaultStep = normalized.defaultStep,
            ),
        )
        replaceTags(id, validTagIds)
        CounterWriteResult.Saved(id)
    }

    suspend fun updateCounter(counterId: String, input: CounterInput): CounterWriteResult = db.withTransaction {
        val existing = counters.find(counterId) ?: return@withTransaction CounterWriteResult.Missing
        val normalized = input.normalized()
        CounterRules.validateName(normalized.name)?.let { return@withTransaction CounterWriteResult.Invalid(it) }
        CounterRules.validateInitialValue(normalized.initialValue, normalized.allowNegative)
            ?.let { return@withTransaction CounterWriteResult.Invalid(it) }
        CounterRules.validateStep(normalized.defaultStep)
            ?.let { return@withTransaction CounterWriteResult.Invalid(it) }

        val currentValue = normalized.initialValue.toLong() + counters.sumDelta(counterId)
        if (!normalized.allowNegative && currentValue < 0) {
            return@withTransaction CounterWriteResult.WouldGoNegative(currentValue)
        }

        counters.update(
            existing.copy(
                name = normalized.name,
                initialValue = normalized.initialValue,
                allowNegative = normalized.allowNegative,
                defaultStep = normalized.defaultStep,
            ),
        )
        replaceTags(counterId, filterKnownTagIds(normalized.tagIds))
        CounterWriteResult.Saved(counterId)
    }

    suspend fun adjust(
        counterId: String,
        delta: Int,
        source: EventSource = EventSource.MANUAL,
        sessionId: String? = null,
        note: String? = null,
    ): AdjustOutcome = db.withTransaction {
        val counter = counters.find(counterId) ?: return@withTransaction AdjustOutcome.CounterMissing
        CounterRules.validateDelta(delta)?.let { return@withTransaction AdjustOutcome.Invalid(it) }
        CounterRules.validateNote(note)?.let { return@withTransaction AdjustOutcome.Invalid(it) }

        val currentValue = counter.initialValue.toLong() + counters.sumDelta(counterId)
        if (CounterMath.wouldGoNegative(currentValue, delta, counter.allowNegative)) {
            return@withTransaction AdjustOutcome.BlockedByNegative(currentValue, delta)
        }

        val event = CountEvent(
            id = newId(),
            counterId = counterId,
            delta = delta,
            timestamp = clock.instant(),
            note = note?.takeIf { it.isNotBlank() },
            source = source,
            sessionId = sessionId,
        )
        events.insert(event.toEntity())
        AdjustOutcome.Applied(event, currentValue + delta)
    }

    suspend fun resetToInitial(counterId: String): AdjustOutcome = db.withTransaction {
        val counter = counters.find(counterId) ?: return@withTransaction AdjustOutcome.CounterMissing
        val currentValue = counter.initialValue.toLong() + counters.sumDelta(counterId)
        val delta = CounterMath.resetDelta(counter.initialValue, currentValue)
            ?: return@withTransaction AdjustOutcome.Invalid(RuleViolation.DeltaOutOfRange)
        if (delta == 0) return@withTransaction AdjustOutcome.AlreadyAtBaseline
        if (CounterMath.wouldGoNegative(currentValue, delta, counter.allowNegative)) {
            return@withTransaction AdjustOutcome.BlockedByNegative(currentValue, delta)
        }
        val event = CountEvent(
            id = newId(),
            counterId = counterId,
            delta = delta,
            timestamp = clock.instant(),
            note = null,
            source = EventSource.RESET,
        )
        events.insert(event.toEntity())
        AdjustOutcome.Applied(event, currentValue + delta)
    }

    suspend fun undoLatest(counterId: String): UndoOutcome = db.withTransaction {
        val counter = counters.find(counterId) ?: return@withTransaction UndoOutcome.EventMissing
        val all = events.allForCounter(counterId)
        val latest = all.lastOrNull() ?: return@withTransaction UndoOutcome.NothingToUndo
        reverseEvents(counter, all, listOf(latest))
    }

    /** Undo exactly one event. Fails if newer events exist so the caller never hides a gap. */
    suspend fun undoEvent(counterId: String, eventId: String): UndoOutcome = db.withTransaction {
        val counter = counters.find(counterId) ?: return@withTransaction UndoOutcome.EventMissing
        val all = events.allForCounter(counterId)
        val index = all.indexOfFirst { it.id == eventId }
        if (index < 0) return@withTransaction UndoOutcome.EventMissing
        if (index != all.lastIndex) return@withTransaction UndoOutcome.NotLatest
        reverseEvents(counter, all, listOf(all[index]))
    }

    /** Undo the given event and every event appended after it, by appending reverse events. */
    suspend fun undoFrom(counterId: String, eventId: String): UndoOutcome = db.withTransaction {
        val counter = counters.find(counterId) ?: return@withTransaction UndoOutcome.EventMissing
        val all = events.allForCounter(counterId)
        val index = all.indexOfFirst { it.id == eventId }
        if (index < 0) return@withTransaction UndoOutcome.EventMissing
        reverseEvents(counter, all, all.subList(index, all.size))
    }

    private suspend fun reverseEvents(
        counter: CounterEntity,
        history: List<CountEventEntity>,
        toReverse: List<CountEventEntity>,
    ): UndoOutcome {
        val currentValue = counter.initialValue.toLong() + history.sumOf { it.delta.toLong() }
        var running = currentValue
        val reversed = toReverse.asReversed()
        for (event in reversed) {
            running -= event.delta.toLong()
            if (!counter.allowNegative && running < 0) {
                return UndoOutcome.BlockedByNegative(currentValue)
            }
        }
        val base = clock.instant()
        val inserted = mutableListOf<String>()
        reversed.forEachIndexed { index, original ->
            val reverseDelta = CounterMath.reverseDelta(original.delta)
                ?: return UndoOutcome.BlockedByNegative(currentValue)
            val undo = CountEvent(
                id = newId(),
                counterId = counter.id,
                delta = reverseDelta,
                timestamp = base.plusMillis(index.toLong()),
                note = null,
                source = EventSource.UNDO,
            )
            events.insert(undo.toEntity())
            inserted += undo.id
        }
        return UndoOutcome.Applied(inserted, running)
    }

    suspend fun updateEventNote(eventId: String, note: String?): RuleViolation? {
        val normalized = note?.trim()?.takeIf { it.isNotEmpty() }
        CounterRules.validateNote(normalized)?.let { return it }
        events.updateNote(eventId, normalized)
        return null
    }

    suspend fun setPinned(counterId: String, pinned: Boolean) {
        db.withTransaction {
            if (counters.find(counterId) == null) return@withTransaction
            if (pinned) {
                counters.setPinned(counterId, true)
            } else {
                val nextOrder = counters.maxNormalSortOrder() + 1
                counters.setPinned(counterId, false)
                counters.setSortOrder(counterId, nextOrder)
            }
        }
    }

    suspend fun reorderNormalCounters(orderedIds: List<String>) {
        db.withTransaction {
            orderedIds.forEachIndexed { index, id -> counters.setSortOrder(id, index) }
        }
    }

    suspend fun deleteCounter(counterId: String) {
        db.withTransaction {
            counters.delete(counterId)
            tags.deleteUnused()
        }
    }

    suspend fun setTags(counterId: String, tagIds: List<String>): CounterWriteResult {
        val distinct = tagIds.distinct()
        return db.withTransaction {
            counters.find(counterId) ?: return@withTransaction CounterWriteResult.Missing
            replaceTags(counterId, filterKnownTagIds(distinct))
            CounterWriteResult.Saved(counterId)
        }
    }

    /**
     * Deletes the tag and its counter links. Counters and their events are intentionally kept.
     * The counter_tags foreign key cascades, so no manual link cleanup is needed.
     */
    suspend fun deleteTag(tagId: String): Boolean = db.withTransaction {
        tags.deleteById(tagId) > 0
    }

    suspend fun createTag(name: String): TagWriteResult = db.withTransaction {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@withTransaction TagWriteResult.Invalid(RuleViolation.NameBlank)
        if (trimmed.length > CounterMath.MAX_TAG_NAME_LENGTH) {
            return@withTransaction TagWriteResult.Invalid(RuleViolation.NameTooLong)
        }
        tags.findByName(trimmed)?.let { return@withTransaction TagWriteResult.Existing(it.toDomain()) }
        val tag = Tag(id = newId(), name = trimmed, createdAt = clock.instant())
        tags.insert(TagEntity(tag.id, tag.name, tag.createdAt.toEpochMilli()))
        TagWriteResult.Saved(tag)
    }

    suspend fun snapshot(): ExistingSnapshot = db.withTransaction { snapshotInternal() }

    private suspend fun snapshotInternal(): ExistingSnapshot {
        val counterEntities = counters.getAll()
        val linksByCounter = links.getAll().groupBy { it.counterId }
        return ExistingSnapshot(
            counters = counterEntities.map { entity ->
                entity.toDomain(linksByCounter[entity.id].orEmpty().map { it.tagId })
            },
            events = events.getAll().map { it.toDomain() },
            tags = tags.getTags().map { it.toDomain() },
        )
    }

    suspend fun buildExport(now: Instant = clock.instant()): ExportPayload = db.withTransaction {
        val snapshot = snapshotInternal()
        val eventsByCounter = snapshot.events.groupBy { it.counterId }
        ExportPayload(
            json = BackupCodec.encode(snapshot.counters, eventsByCounter, snapshot.tags, now),
            csv = BackupCodec.encodeCsv(snapshot.counters, eventsByCounter),
            counterCount = snapshot.counters.size,
            eventCount = snapshot.events.size,
            tagCount = snapshot.tags.size,
        )
    }

    suspend fun applyImport(plan: ImportPlan) {
        db.withTransaction {
            if (plan.replaceAll) {
                events.deleteAll()
                links.deleteAll()
                counters.deleteAll()
                tags.deleteAll()
            }
            if (plan.tagsToInsert.isNotEmpty()) {
                tags.insertAll(plan.tagsToInsert.map {
                    TagEntity(it.id, it.name, it.createdAt.toEpochMilli())
                })
            }
            if (plan.countersToInsert.isNotEmpty()) {
                counters.insertAll(plan.countersToInsert.map { it.toEntity() })
                plan.countersToInsert.forEach { replaceTags(it.id, it.tagIds) }
            }
            for (counter in plan.countersToUpdate) {
                val existing = counters.find(counter.id) ?: continue
                counters.update(
                    existing.copy(
                        name = counter.name,
                        initialValue = counter.initialValue,
                        allowNegative = counter.allowNegative,
                        defaultStep = counter.defaultStep,
                    ),
                )
                replaceTags(counter.id, counter.tagIds)
            }
            if (plan.eventsToInsert.isNotEmpty()) {
                events.insertAll(plan.eventsToInsert.map { it.toEntity() })
            }
            if (plan.replaceAll) tags.deleteUnused()
        }
    }

    private suspend fun replaceTags(counterId: String, tagIds: List<String>) {
        links.deleteForCounter(counterId)
        if (tagIds.isNotEmpty()) {
            links.insertAll(tagIds.map { CounterTagEntity(counterId, it) })
        }
    }

    private suspend fun filterKnownTagIds(tagIds: List<String>): List<String> =
        tagIds.filter { tags.find(it) != null }
}
