package com.ltq.count.domain.backup

import com.ltq.count.domain.model.CountEvent
import com.ltq.count.domain.model.Counter
import com.ltq.count.domain.model.Tag

enum class ImportMode { MERGE, REPLACE }

/** What to do when imported events would drive a non-negative counter below zero. */
enum class NegativeImportPolicy { SKIP_AFFECTED, ALLOW_NEGATIVE }

data class ExistingSnapshot(
    val counters: List<Counter> = emptyList(),
    val events: List<CountEvent> = emptyList(),
    val tags: List<Tag> = emptyList(),
)

data class NegativeConflict(
    val counterId: String,
    val counterName: String,
    val worstValue: Long,
)

data class ImportCounts(
    val countersAdded: Int = 0,
    val countersMerged: Int = 0,
    val countersSkipped: Int = 0,
    val eventsAdded: Int = 0,
    val eventsDuplicate: Int = 0,
    val tagsAdded: Int = 0,
) {
    val totalCounters: Int get() = countersAdded + countersMerged
}

/**
 * Everything the repository needs to apply an import, computed without touching the database so
 * merge/replace/dedupe decisions stay unit-testable.
 */
data class ImportPlan(
    val mode: ImportMode,
    val replaceAll: Boolean,
    val countersToInsert: List<Counter>,
    val countersToUpdate: List<Counter>,
    val eventsToInsert: List<CountEvent>,
    val tagsToInsert: List<Tag>,
    val counts: ImportCounts,
    val conflicts: List<NegativeConflict>,
    val warnings: List<BackupWarning>,
) {
    val hasChanges: Boolean
        get() = countersToInsert.isNotEmpty() || countersToUpdate.isNotEmpty() || eventsToInsert.isNotEmpty() || replaceAll
}

object ImportPlanner {
    private data class Candidate(
        val counter: Counter,
        val events: List<CountEvent>,
        val isNew: Boolean,
    )

    /**
     * Merge: counters are matched by id, events are unioned with id (or composite) dedupe.
     * Replace: the whole database is replaced by the file; ids are preserved as-is.
     */
    fun plan(
        existing: ExistingSnapshot,
        imported: ImportedData,
        mode: ImportMode,
        negativePolicy: NegativeImportPolicy,
    ): ImportPlan {
        val existingCounterById = existing.counters.associateBy { it.id }
        val existingTagIds = existing.tags.mapTo(HashSet()) { it.id }
        val importedTagsById = imported.tags.associateBy { it.id }
        val knownTagIds = HashSet(existingTagIds).apply { addAll(importedTagsById.keys) }

        val tagsToInsert = when (mode) {
            // A replace wipes the tag table, so every imported tag must be re-inserted.
            ImportMode.REPLACE -> imported.tags
            ImportMode.MERGE -> imported.tags.filter { it.id !in existingTagIds }
        }

        val existingEventKeys = if (mode == ImportMode.MERGE) {
            BackupCodec.existingDedupeKeys(existing.events)
        } else {
            emptySet()
        }

        var baseSortOrder = if (mode == ImportMode.MERGE) {
            (existing.counters.filterNot { it.pinned }.maxOfOrNull { it.sortOrder } ?: -1) + 1
        } else {
            0
        }

        val candidates = mutableListOf<Candidate>()
        var duplicateEvents = 0

        // Preserve the file's relative order for appended counters.
        val orderedImported = imported.counters.sortedWith(
            compareBy<ImportedCounter>({ it.counter.sortOrder }, { it.counter.createdAt }, { it.counter.id }),
        )

        for (importedCounter in orderedImported) {
            val existingCounter = existingCounterById[importedCounter.counter.id]
            val mergedTagIds = mutableListOf<String>()
            val sourceTagIds = if (existingCounter != null && mode == ImportMode.MERGE) {
                existingCounter.tagIds + importedCounter.counter.tagIds
            } else {
                importedCounter.counter.tagIds
            }
            for (tagId in sourceTagIds) {
                if (tagId !in knownTagIds || tagId in mergedTagIds) continue
                mergedTagIds += tagId
            }

            val newEvents = when (mode) {
                ImportMode.MERGE -> importedCounter.events.filter { event ->
                    val key = importedCounter.dedupeKeys[event.id] ?: BackupCodec.eventDedupeKey(event)
                    key !in existingEventKeys
                }
                ImportMode.REPLACE -> importedCounter.events
            }
            val duplicates = importedCounter.events.size - newEvents.size
            duplicateEvents += duplicates

            val finalCounter = when {
                mode == ImportMode.REPLACE -> importedCounter.counter.copy(tagIds = mergedTagIds)
                existingCounter == null -> importedCounter.counter.copy(
                    sortOrder = baseSortOrder++,
                    tagIds = mergedTagIds,
                )
                else -> existingCounter.copy(tagIds = mergedTagIds)
            }

            candidates += Candidate(
                counter = finalCounter,
                events = newEvents,
                isNew = existingCounter == null || mode == ImportMode.REPLACE,
            )
        }

        // Negative-value conflicts are evaluated against the complete merged stream.
        val conflicts = mutableListOf<NegativeConflict>()
        val existingEventsByCounter = existing.events.groupBy { it.counterId }
        for (candidate in candidates) {
            val counter = candidate.counter
            if (counter.allowNegative) continue
            val history = if (mode == ImportMode.MERGE && !candidate.isNew) {
                (existingEventsByCounter[counter.id] ?: emptyList()) + candidate.events
            } else {
                candidate.events
            }
            var running = counter.initialValue.toLong()
            var worst = Long.MAX_VALUE
            var violated = false
            for (event in history.sortedWith(compareBy({ it.timestamp }, { it.id }))) {
                running += event.delta.toLong()
                if (running < 0) {
                    violated = true
                    if (running < worst) worst = running
                }
            }
            if (violated) {
                conflicts += NegativeConflict(counter.id, counter.name, worst)
            }
        }
        val conflictIds = conflicts.mapTo(HashSet()) { it.counterId }

        val countersToInsert = mutableListOf<Counter>()
        val countersToUpdate = mutableListOf<Counter>()
        val eventsToInsert = mutableListOf<CountEvent>()
        var countersMerged = 0
        var countersSkipped = 0

        for (candidate in candidates) {
            val isConflict = candidate.counter.id in conflictIds
            if (isConflict && negativePolicy == NegativeImportPolicy.SKIP_AFFECTED) {
                countersSkipped += 1
                continue
            }
            var counter = candidate.counter
            if (isConflict) {
                // Allow the file through by explicitly enabling negative values on that counter,
                // keeping the "allowNegative == false means value >= 0" invariant intact.
                counter = counter.copy(allowNegative = true)
            }
            if (candidate.isNew) {
                countersToInsert += counter
            } else {
                val existingCounter = existingCounterById.getValue(counter.id)
                if (counter != existingCounter) countersToUpdate += counter
                countersMerged += 1
            }
            eventsToInsert += candidate.events
        }

        val warnings = imported.warnings.toMutableList()

        return ImportPlan(
            mode = mode,
            replaceAll = mode == ImportMode.REPLACE,
            countersToInsert = countersToInsert,
            countersToUpdate = countersToUpdate,
            eventsToInsert = eventsToInsert.sortedWith(compareBy({ it.timestamp }, { it.id })),
            tagsToInsert = tagsToInsert,
            counts = ImportCounts(
                countersAdded = countersToInsert.size,
                countersMerged = countersMerged,
                countersSkipped = countersSkipped,
                eventsAdded = eventsToInsert.size,
                eventsDuplicate = duplicateEvents,
                tagsAdded = tagsToInsert.size,
            ),
            conflicts = conflicts,
            warnings = warnings,
        )
    }
}
