package com.ltq.count.domain.backup

import com.ltq.count.domain.model.CountEvent
import com.ltq.count.domain.model.Counter
import com.ltq.count.domain.model.EventSource
import com.ltq.count.domain.model.Tag
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportPlannerTest {
    private val t0 = Instant.parse("2026-01-01T00:00:00Z")

    @Test
    fun `merge unions events and skips duplicates by id`() {
        val existing = ExistingSnapshot(
            counters = listOf(counter("c1", "A", sortOrder = 0)),
            events = listOf(event("e1", "c1", 2, "2026-01-02T00:00:00Z")),
        )
        val imported = ImportedData(
            counters = listOf(
                importedCounter(
                    counter("c1", "Renamed", sortOrder = 9),
                    listOf(
                        event("e1", "c1", 2, "2026-01-02T00:00:00Z"),
                        event("e2", "c1", 3, "2026-01-03T00:00:00Z"),
                    ),
                ),
                importedCounter(
                    counter("c2", "B", sortOrder = 1),
                    listOf(event("e3", "c2", 5, "2026-01-04T00:00:00Z")),
                ),
            ),
            tags = emptyList(),
            warnings = emptyList(),
        )

        val plan = ImportPlanner.plan(existing, imported, ImportMode.MERGE, NegativeImportPolicy.SKIP_AFFECTED)

        assertEquals(1, plan.countersToInsert.size)
        assertEquals("c2", plan.countersToInsert.single().id)
        assertEquals(1, plan.countersToInsert.single().sortOrder)
        assertTrue(plan.countersToUpdate.isEmpty())
        assertEquals(setOf("e2", "e3"), plan.eventsToInsert.map { it.id }.toSet())
        assertEquals(1, plan.counts.eventsDuplicate)
        assertEquals(1, plan.counts.countersMerged)
        assertEquals(1, plan.counts.countersAdded)
        assertFalse(plan.replaceAll)
    }

    @Test
    fun `merge matches events without ids by counter timestamp and delta`() {
        val existingEvent = event("db-1", "c1", 2, "2026-01-02T00:00:00Z")
        val existing = ExistingSnapshot(
            counters = listOf(counter("c1", "A")),
            events = listOf(existingEvent),
        )
        val importedEvent = existingEvent.copy(id = "file-1")
        val imported = ImportedData(
            counters = listOf(
                ImportedCounter(
                    counter = counter("c1", "A"),
                    events = listOf(importedEvent),
                    dedupeKeys = mapOf(
                        "file-1" to "cmp:c1:${existingEvent.timestamp.toEpochMilli()}:2",
                    ),
                ),
            ),
            tags = emptyList(),
            warnings = emptyList(),
        )

        val plan = ImportPlanner.plan(existing, imported, ImportMode.MERGE, NegativeImportPolicy.SKIP_AFFECTED)
        assertTrue(plan.eventsToInsert.isEmpty())
        assertEquals(1, plan.counts.eventsDuplicate)
    }

    @Test
    fun `merge keeps existing counter metadata and merges all tags`() {
        val tagA = Tag("a", "A", t0)
        val tagB = Tag("b", "B", t0)
        val tagC = Tag("c", "C", t0)
        val existing = ExistingSnapshot(
            counters = listOf(counter("c1", "Original", tagIds = listOf("a"))),
            tags = listOf(tagA, tagB, tagC),
        )
        val imported = ImportedData(
            counters = listOf(
                importedCounter(
                    counter("c1", "Changed", tagIds = listOf("b", "c")),
                    emptyList(),
                ),
            ),
            tags = listOf(tagA, tagB, tagC),
            warnings = emptyList(),
        )

        val plan = ImportPlanner.plan(existing, imported, ImportMode.MERGE, NegativeImportPolicy.SKIP_AFFECTED)
        val updated = plan.countersToUpdate.single()
        assertEquals("Original", updated.name)
        assertEquals(listOf("a", "b", "c"), updated.tagIds)
    }

    @Test
    fun `negative import conflict is skipped when policy says skip`() {
        val imported = ImportedData(
            counters = listOf(
                importedCounter(
                    counter("c1", "A", initial = 0, allowNegative = false),
                    listOf(event("e1", "c1", -3, "2026-01-02T00:00:00Z")),
                ),
            ),
            tags = emptyList(),
            warnings = emptyList(),
        )

        val plan = ImportPlanner.plan(ExistingSnapshot(), imported, ImportMode.MERGE, NegativeImportPolicy.SKIP_AFFECTED)
        assertEquals(1, plan.conflicts.size)
        assertEquals(-3L, plan.conflicts.single().worstValue)
        assertTrue(plan.countersToInsert.isEmpty())
        assertTrue(plan.eventsToInsert.isEmpty())
        assertEquals(1, plan.counts.countersSkipped)
    }

    @Test
    fun `negative import conflict flips allowNegative when policy says allow`() {
        val imported = ImportedData(
            counters = listOf(
                importedCounter(
                    counter("c1", "A", initial = 0, allowNegative = false),
                    listOf(event("e1", "c1", -3, "2026-01-02T00:00:00Z")),
                ),
            ),
            tags = emptyList(),
            warnings = emptyList(),
        )

        val plan = ImportPlanner.plan(ExistingSnapshot(), imported, ImportMode.MERGE, NegativeImportPolicy.ALLOW_NEGATIVE)
        assertTrue(plan.conflicts.single().counterId == "c1")
        assertTrue(plan.countersToInsert.single().allowNegative)
        assertEquals(1, plan.eventsToInsert.size)
    }

    @Test
    fun `merge into existing counter detects negative dip in the union`() {
        val existing = ExistingSnapshot(
            counters = listOf(counter("c1", "A", initial = 0, allowNegative = false)),
            events = listOf(event("e1", "c1", 5, "2026-01-02T00:00:00Z")),
        )
        val imported = ImportedData(
            counters = listOf(
                importedCounter(
                    counter("c1", "A", initial = 0, allowNegative = false),
                    listOf(event("e2", "c1", -6, "2026-01-03T00:00:00Z")),
                ),
            ),
            tags = emptyList(),
            warnings = emptyList(),
        )

        val plan = ImportPlanner.plan(existing, imported, ImportMode.MERGE, NegativeImportPolicy.ALLOW_NEGATIVE)
        assertEquals(-1L, plan.conflicts.single().worstValue)
        assertTrue(plan.countersToUpdate.single().allowNegative)
        assertEquals(1, plan.eventsToInsert.size)
    }

    @Test
    fun `replace plan keeps file order and metadata and marks full replacement`() {
        val existing = ExistingSnapshot(
            counters = listOf(counter("c0", "Old")),
            events = listOf(event("old", "c0", 1, "2026-01-01T00:00:00Z")),
        )
        val imported = ImportedData(
            counters = listOf(
                importedCounter(counter("c2", "B", sortOrder = 3, pinned = true), emptyList()),
                importedCounter(counter("c1", "A", sortOrder = 1), emptyList()),
            ),
            tags = emptyList(),
            warnings = emptyList(),
        )

        val plan = ImportPlanner.plan(existing, imported, ImportMode.REPLACE, NegativeImportPolicy.SKIP_AFFECTED)
        assertTrue(plan.replaceAll)
        assertEquals(listOf("c1", "c2"), plan.countersToInsert.map { it.id })
        assertEquals(1, plan.countersToInsert.first().sortOrder)
        assertTrue(plan.countersToInsert.last().pinned)
    }

    @Test
    fun `replacing with an empty file yields an empty plan that still replaces`() {
        val plan = ImportPlanner.plan(
            ExistingSnapshot(counters = listOf(counter("c1", "A"))),
            ImportedData(emptyList(), emptyList(), emptyList()),
            ImportMode.REPLACE,
            NegativeImportPolicy.SKIP_AFFECTED,
        )
        assertTrue(plan.replaceAll)
        assertTrue(plan.countersToInsert.isEmpty())
        assertTrue(plan.hasChanges)
    }

    @Test
    fun `replace re-inserts tags even when the id already exists locally`() {
        val tag = Tag("tag-1", "健康", t0)
        val existing = ExistingSnapshot(
            counters = listOf(counter("c1", "Old", tagIds = listOf(tag.id))),
            tags = listOf(tag),
        )
        val imported = ImportedData(
            counters = listOf(importedCounter(counter("c2", "New", tagIds = listOf(tag.id)), emptyList())),
            tags = listOf(tag),
            warnings = emptyList(),
        )

        val plan = ImportPlanner.plan(existing, imported, ImportMode.REPLACE, NegativeImportPolicy.SKIP_AFFECTED)
        assertTrue(plan.replaceAll)
        assertEquals(listOf(tag), plan.tagsToInsert)
        assertEquals(listOf(tag.id), plan.countersToInsert.single().tagIds)
    }

    private fun counter(
        id: String,
        name: String,
        initial: Int = 0,
        allowNegative: Boolean = false,
        sortOrder: Int = 0,
        pinned: Boolean = false,
        tagIds: List<String> = emptyList(),
    ) = Counter(
        id = id,
        name = name,
        initialValue = initial,
        allowNegative = allowNegative,
        createdAt = t0,
        sortOrder = sortOrder,
        pinned = pinned,
        defaultStep = 1,
        tagIds = tagIds,
    )

    private fun importedCounter(counter: Counter, events: List<CountEvent>) = ImportedCounter(
        counter = counter,
        events = events,
        dedupeKeys = events.associate { it.id to "id:${it.id}" },
    )

    private fun event(id: String, counterId: String, delta: Int, at: String) = CountEvent(
        id = id,
        counterId = counterId,
        delta = delta,
        timestamp = Instant.parse(at),
        source = EventSource.MANUAL,
    )
}

