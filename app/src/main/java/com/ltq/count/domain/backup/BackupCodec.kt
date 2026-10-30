package com.ltq.count.domain.backup

import com.ltq.count.domain.CounterMath
import com.ltq.count.domain.CounterRules
import com.ltq.count.domain.model.CountEvent
import com.ltq.count.domain.model.Counter
import com.ltq.count.domain.model.EventSource
import com.ltq.count.domain.model.Tag
import java.time.Instant
import java.time.format.DateTimeParseException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/**
 * JSON export/import codec. Encoding is pure; decoding validates the document structure up front
 * so the UI can present one precise error instead of a partially applied import. Semantic checks
 * that depend on user choices (negative values) belong to [ImportPlanner].
 */
object BackupCodec {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = true
    }

    fun encode(
        counters: List<Counter>,
        eventsByCounter: Map<String, List<CountEvent>>,
        tags: List<Tag>,
        exportedAt: Instant,
        pretty: Boolean = true,
    ): String {
        val tagById = tags.associateBy { it.id }
        val document = BackupDocument(
            schemaVersion = SCHEMA_VERSION,
            app = BACKUP_APP_ID,
            exportedAt = exportedAt.toString(),
            counters = counters.map { counter ->
                BackupCounter(
                    id = counter.id,
                    name = counter.name,
                    initialValue = counter.initialValue,
                    allowNegative = counter.allowNegative,
                    createdAt = counter.createdAt.toString(),
                    sortOrder = counter.sortOrder,
                    pinned = counter.pinned,
                    defaultStep = counter.defaultStep,
                    tagIds = counter.tagIds.filter { tagById.containsKey(it) },
                    events = (eventsByCounter[counter.id] ?: emptyList())
                        .sortedWith(compareBy({ it.timestamp }, { it.id }))
                        .map { event ->
                            BackupEvent(
                                id = event.id,
                                delta = event.delta,
                                timestamp = event.timestamp.toString(),
                                note = event.note,
                                source = event.source.name,
                                sessionId = event.sessionId,
                            )
                        },
                )
            },
            tags = tags.map { BackupTag(it.id, it.name, it.createdAt.toString()) },
        )
        val codec = if (pretty) json else Json(json) { prettyPrint = false }
        return codec.encodeToString(BackupDocument.serializer(), document)
    }

    fun decode(raw: String): BackupParseResult {
        if (raw.isBlank()) return BackupParseResult.Failure(BackupError.MalformedJson)
        val root: JsonObject = try {
            json.parseToJsonElement(raw).jsonObject
        } catch (_: Exception) {
            return BackupParseResult.Failure(BackupError.MalformedJson)
        }

        val version = (root["schemaVersion"] as? JsonPrimitive)?.intOrNull
            ?: return BackupParseResult.Failure(BackupError.MissingSchemaVersion)
        if (version > SCHEMA_VERSION || version < 1) {
            return BackupParseResult.Failure(BackupError.UnsupportedSchemaVersion(version))
        }

        val document = try {
            json.decodeFromString(BackupDocument.serializer(), raw)
        } catch (_: Exception) {
            return BackupParseResult.Failure(BackupError.MalformedJson)
        }
        return decodeDocument(document)
    }

    private fun decodeDocument(document: BackupDocument): BackupParseResult {
        val warningCounts = mutableMapOf<BackupWarningKind, Int>()
        fun warn(kind: BackupWarningKind, count: Int = 1) {
            if (count > 0) warningCounts[kind] = (warningCounts[kind] ?: 0) + count
        }

        val tags = mutableListOf<Tag>()
        val knownTagIds = mutableSetOf<String>()
        document.tags.forEachIndexed { index, tag ->
            val path = "tags[$index]"
            if (tag.id.isBlank()) return BackupParseResult.Failure(BackupError.InvalidField(path, BackupField.Id))
            if (tag.name.isBlank()) return BackupParseResult.Failure(BackupError.InvalidField(path, BackupField.Name))
            if (!knownTagIds.add(tag.id)) {
                warn(BackupWarningKind.UnknownTag)
                return@forEachIndexed
            }
            val createdAt = tag.createdAt?.let { parseInstant(it) } ?: Instant.EPOCH
            tags += Tag(tag.id, tag.name.trim(), createdAt)
        }

        val counters = mutableListOf<ImportedCounter>()
        val counterIds = mutableSetOf<String>()
        val seenEventKeys = mutableSetOf<String>()

        document.counters.forEachIndexed { counterIndex, raw ->
            val base = "counters[$counterIndex]"
            if (raw.id.isBlank()) return BackupParseResult.Failure(BackupError.InvalidField(base, BackupField.Id))
            if (!counterIds.add(raw.id)) return BackupParseResult.Failure(BackupError.InvalidField(base, BackupField.Id))
            if (raw.name.isBlank()) return BackupParseResult.Failure(BackupError.InvalidField(base, BackupField.Name))
            if (!CounterMath.isValidInitialValue(raw.initialValue)) {
                return BackupParseResult.Failure(BackupError.InvalidField(base, BackupField.InitialValue))
            }
            if (!raw.allowNegative && raw.initialValue < 0) {
                return BackupParseResult.Failure(BackupError.InvalidField(base, BackupField.AllowNegative))
            }
            if (!CounterMath.isValidStep(raw.defaultStep)) {
                return BackupParseResult.Failure(BackupError.InvalidField(base, BackupField.DefaultStep))
            }
            val createdAt = parseInstant(raw.createdAt)
                ?: return BackupParseResult.Failure(BackupError.InvalidField(base, BackupField.CreatedAt))

            val selectedTags = mutableListOf<String>()
            raw.tagIds.forEach { tagId ->
                when {
                    !knownTagIds.contains(tagId) -> warn(BackupWarningKind.UnknownTag)
                    tagId in selectedTags -> Unit
                    else -> selectedTags += tagId
                }
            }

            val events = mutableListOf<CountEvent>()
            val dedupeKeys = mutableMapOf<String, String>()
            raw.events.forEachIndexed { eventIndex, rawEvent ->
                val path = "$base.events[$eventIndex]"
                if (CounterRules.validateDelta(rawEvent.delta) != null) {
                    if (rawEvent.delta == 0) {
                        warn(BackupWarningKind.ZeroDeltaSkipped)
                        return@forEachIndexed
                    }
                    return BackupParseResult.Failure(BackupError.InvalidField(path, BackupField.Delta))
                }
                val timestamp = parseInstant(rawEvent.timestamp)
                    ?: return BackupParseResult.Failure(BackupError.InvalidField(path, BackupField.Timestamp))
                val note = rawEvent.note?.takeIf { it.isNotBlank() }
                if (CounterRules.validateNote(note) != null) {
                    return BackupParseResult.Failure(BackupError.InvalidField(path, BackupField.Note))
                }
                val rawSource = rawEvent.source
                val source = if (rawSource == null) {
                    EventSource.MANUAL
                } else {
                    EventSource.parse(rawSource) ?: run {
                        warn(BackupWarningKind.UnknownSource)
                        EventSource.MANUAL
                    }
                }

                val eventId = rawEvent.id?.takeIf { it.isNotBlank() }
                val dedupeKey = if (eventId != null) {
                    "id:$eventId"
                } else {
                    compositeKey(raw.id, timestamp, rawEvent.delta)
                }
                if (!seenEventKeys.add(dedupeKey)) {
                    warn(BackupWarningKind.DuplicateEventSkipped)
                    return@forEachIndexed
                }

                val generatedId = eventId ?: "imp-${raw.id}-$eventIndex"
                events += CountEvent(
                    id = generatedId,
                    counterId = raw.id,
                    delta = rawEvent.delta,
                    timestamp = timestamp,
                    note = note,
                    source = source,
                    sessionId = rawEvent.sessionId,
                )
                dedupeKeys[generatedId] = dedupeKey
            }

            counters += ImportedCounter(
                counter = Counter(
                    id = raw.id,
                    name = raw.name.trim(),
                    initialValue = raw.initialValue,
                    allowNegative = raw.allowNegative,
                    createdAt = createdAt,
                    sortOrder = raw.sortOrder,
                    pinned = raw.pinned,
                    defaultStep = raw.defaultStep,
                    tagIds = selectedTags,
                ),
                events = events,
                dedupeKeys = dedupeKeys,
            )
        }

        val warnings = warningCounts.map { BackupWarning(it.key, it.value) }
        return BackupParseResult.Success(ImportedData(counters = counters, tags = tags, warnings = warnings))
    }

    private fun parseInstant(raw: String): Instant? = try {
        Instant.parse(raw)
    } catch (_: DateTimeParseException) {
        null
    }

    private fun compositeKey(counterId: String, timestamp: Instant, delta: Int): String =
        "cmp:$counterId:${timestamp.toEpochMilli()}:$delta"

    /** Key used for merge dedupe: event id when present, otherwise (counterId, timestamp, delta). */
    fun eventDedupeKey(event: CountEvent): String = "id:${event.id}"

    fun existingDedupeKeys(events: List<CountEvent>): Set<String> {
        val keys = HashSet<String>(events.size * 2)
        for (event in events) {
            keys += eventDedupeKey(event)
            keys += compositeKey(event.counterId, event.timestamp, event.delta)
        }
        return keys
    }

    /** CSV is an extra, read-only export for spreadsheets. Never imported. */
    fun encodeCsv(counters: List<Counter>, eventsByCounter: Map<String, List<CountEvent>>): String {
        val builder = StringBuilder()
        builder.append("counterId,counterName,eventId,timestamp,delta,source,note\n")
        for (counter in counters) {
            val events = (eventsByCounter[counter.id] ?: emptyList())
                .sortedWith(compareBy({ it.timestamp }, { it.id }))
            for (event in events) {
                builder.append(csv(counter.id)).append(',')
                    .append(csv(counter.name)).append(',')
                    .append(csv(event.id)).append(',')
                    .append(csv(event.timestamp.toString())).append(',')
                    .append(event.delta).append(',')
                    .append(event.source.name).append(',')
                    .append(csv(event.note ?: "")).append('\n')
            }
        }
        return builder.toString()
    }

    private fun csv(value: String): String {
        val needsQuotes = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        val escaped = value.replace("\"", "\"\"")
        return if (needsQuotes) "\"$escaped\"" else escaped
    }
}
