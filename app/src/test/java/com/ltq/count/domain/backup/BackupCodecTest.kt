package com.ltq.count.domain.backup

import com.ltq.count.domain.model.CountEvent
import com.ltq.count.domain.model.Counter
import com.ltq.count.domain.model.EventSource
import com.ltq.count.domain.model.Tag
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {
    private val t0 = Instant.parse("2026-01-01T08:00:00Z")

    @Test
    fun `export and import round trip preserves counters events and tags`() {
        val tag = Tag("tag-1", "健康", t0)
        val counter = Counter(
            id = "c1",
            name = "喝水",
            initialValue = -3,
            allowNegative = true,
            createdAt = t0,
            sortOrder = 4,
            pinned = true,
            defaultStep = 5,
            tagIds = listOf(tag.id),
        )
        val events = listOf(
            event("e1", 2, "2026-01-02T00:00:00Z", note = null),
            event("e2", -1, "2026-01-03T00:00:00Z", note = "早上"),
            event("e3", 7, "2026-01-04T00:00:00Z", source = EventSource.RESET),
        )

        val json = BackupCodec.encode(listOf(counter), mapOf("c1" to events), listOf(tag), t0.plusSeconds(60))
        val decoded = BackupCodec.decode(json)
        assertTrue(decoded is BackupParseResult.Success)
        val data = (decoded as BackupParseResult.Success).data

        assertEquals(1, data.counters.size)
        val imported = data.counters.single()
        assertEquals(counter, imported.counter)
        assertEquals(events, imported.events)
        assertEquals(listOf(tag), data.tags)
        assertTrue(data.warnings.isEmpty())
    }

    @Test
    fun `any number of tags round trips`() {
        val tags = (1..4).map { Tag("tag-$it", "T$it", t0) }
        val counter = Counter(
            id = "c1",
            name = "Many tags",
            initialValue = 0,
            allowNegative = false,
            createdAt = t0,
            sortOrder = 0,
            pinned = false,
            defaultStep = 1,
            tagIds = tags.map { it.id },
        )

        val json = BackupCodec.encode(listOf(counter), emptyMap(), tags, t0.plusSeconds(60))
        val decoded = BackupCodec.decode(json) as BackupParseResult.Success

        assertEquals(tags.map { it.id }, decoded.data.counters.single().counter.tagIds)
        assertTrue(decoded.data.warnings.isEmpty())
    }

    @Test
    fun `unsupported schema version is rejected`() {
        val json = """{"schemaVersion":2,"exportedAt":"2026-01-01T00:00:00Z","counters":[]}"""
        val result = BackupCodec.decode(json)
        assertEquals(BackupParseResult.Failure(BackupError.UnsupportedSchemaVersion(2)), result)
    }

    @Test
    fun `missing schema version is rejected`() {
        val json = """{"exportedAt":"2026-01-01T00:00:00Z","counters":[]}"""
        assertEquals(BackupParseResult.Failure(BackupError.MissingSchemaVersion), BackupCodec.decode(json))
    }

    @Test
    fun `malformed json is rejected`() {
        assertEquals(BackupParseResult.Failure(BackupError.MalformedJson), BackupCodec.decode("{oops"))
        assertEquals(BackupParseResult.Failure(BackupError.MalformedJson), BackupCodec.decode(""))
    }

    @Test
    fun `invalid counter field reports its path`() {
        val json = """
            {"schemaVersion":1,"exportedAt":"2026-01-01T00:00:00Z","counters":[
              {"id":"c1","name":"","initialValue":0,"allowNegative":false,"createdAt":"2026-01-01T00:00:00Z","events":[]}
            ]}
        """.trimIndent()
        val result = BackupCodec.decode(json)
        assertEquals(
            BackupParseResult.Failure(BackupError.InvalidField("counters[0]", BackupField.Name)),
            result,
        )
    }

    @Test
    fun `zero delta events are skipped with a warning`() {
        val json = """
            {"schemaVersion":1,"exportedAt":"2026-01-01T00:00:00Z","counters":[
              {"id":"c1","name":"A","initialValue":0,"allowNegative":false,"createdAt":"2026-01-01T00:00:00Z",
               "events":[{"id":"e1","delta":0,"timestamp":"2026-01-02T00:00:00Z"}]}
            ]}
        """.trimIndent()
        val data = (BackupCodec.decode(json) as BackupParseResult.Success).data
        assertTrue(data.counters.single().events.isEmpty())
        assertEquals(1, data.warnings.first { it.kind == BackupWarningKind.ZeroDeltaSkipped }.count)
    }

    @Test
    fun `duplicate event ids inside a file are dropped`() {
        val json = """
            {"schemaVersion":1,"exportedAt":"2026-01-01T00:00:00Z","counters":[
              {"id":"c1","name":"A","initialValue":0,"allowNegative":false,"createdAt":"2026-01-01T00:00:00Z",
               "events":[
                 {"id":"same","delta":1,"timestamp":"2026-01-02T00:00:00Z"},
                 {"id":"same","delta":2,"timestamp":"2026-01-03T00:00:00Z"}
               ]}
            ]}
        """.trimIndent()
        val data = (BackupCodec.decode(json) as BackupParseResult.Success).data
        assertEquals(1, data.counters.single().events.size)
        assertEquals(1, data.warnings.first { it.kind == BackupWarningKind.DuplicateEventSkipped }.count)
    }

    @Test
    fun `events without ids still import and expose a composite dedupe key`() {
        val json = """
            {"schemaVersion":1,"exportedAt":"2026-01-01T00:00:00Z","counters":[
              {"id":"c1","name":"A","initialValue":0,"allowNegative":false,"createdAt":"2026-01-01T00:00:00Z",
               "events":[{"delta":2,"timestamp":"2026-01-02T00:00:00Z"}]}
            ]}
        """.trimIndent()
        val data = (BackupCodec.decode(json) as BackupParseResult.Success).data
        val event = data.counters.single().events.single()
        val key = data.counters.single().dedupeKeys.getValue(event.id)
        assertTrue(key.startsWith("cmp:c1:"))
    }

    @Test
    fun `csv export escapes commas and quotes`() {
        val counter = Counter(
            id = "c1",
            name = "A, B",
            initialValue = 0,
            allowNegative = false,
            createdAt = t0,
            sortOrder = 0,
            pinned = false,
            defaultStep = 1,
        )
        val events = listOf(event("e1", 1, "2026-01-02T00:00:00Z", note = "say \"hi\", now"))
        val csv = BackupCodec.encodeCsv(listOf(counter), mapOf("c1" to events))
        val header = csv.lineSequence().first()
        assertEquals("counterId,counterName,eventId,timestamp,delta,source,note", header)
        val line = csv.lineSequence().elementAt(1)
        assertTrue(line.startsWith("c1,\"A, B\",e1,"))
        assertTrue(line.endsWith("\"say \"\"hi\"\", now\""))
    }

    private fun event(
        id: String,
        delta: Int,
        at: String,
        note: String? = null,
        source: EventSource = EventSource.MANUAL,
    ) = CountEvent(
        id = id,
        counterId = "c1",
        delta = delta,
        timestamp = Instant.parse(at),
        note = note,
        source = source,
    )
}
