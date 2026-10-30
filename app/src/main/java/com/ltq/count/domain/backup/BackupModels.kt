package com.ltq.count.domain.backup

import kotlinx.serialization.Serializable

const val SCHEMA_VERSION = 1
const val BACKUP_APP_ID = "Count"

/**
 * Persisted JSON contract. `schemaVersion` is mandatory on read even though it has a default
 * here: the decoder inspects the raw object before deserialization so old or new files fail
 * with a precise message instead of silently dropping fields.
 */
@Serializable
data class BackupDocument(
    val schemaVersion: Int = SCHEMA_VERSION,
    val app: String = BACKUP_APP_ID,
    val exportedAt: String,
    val counters: List<BackupCounter> = emptyList(),
    val tags: List<BackupTag> = emptyList(),
)

@Serializable
data class BackupCounter(
    val id: String,
    val name: String,
    val initialValue: Int,
    val allowNegative: Boolean,
    val createdAt: String,
    val sortOrder: Int = 0,
    val pinned: Boolean = false,
    val defaultStep: Int = 1,
    val tagIds: List<String> = emptyList(),
    val events: List<BackupEvent> = emptyList(),
)

@Serializable
data class BackupEvent(
    val id: String? = null,
    val delta: Int,
    val timestamp: String,
    val note: String? = null,
    val source: String? = null,
    val sessionId: String? = null,
)

@Serializable
data class BackupTag(
    val id: String,
    val name: String,
    val createdAt: String? = null,
)

enum class BackupField {
    Id,
    Name,
    InitialValue,
    AllowNegative,
    CreatedAt,
    SortOrder,
    Pinned,
    DefaultStep,
    Delta,
    Timestamp,
    Source,
    Note,
    SchemaVersion,
    TagId,
    CounterList,
}

sealed interface BackupError {
    data object MalformedJson : BackupError
    data object MissingSchemaVersion : BackupError
    data class UnsupportedSchemaVersion(val version: Int) : BackupError
    data class MissingField(val path: String) : BackupError
    data class InvalidField(val path: String, val field: BackupField) : BackupError
}

enum class BackupWarningKind {
    DuplicateEventSkipped,
    ZeroDeltaSkipped,
    UnknownSource,
    UnknownTag,
}

data class BackupWarning(val kind: BackupWarningKind, val count: Int)

/** Validated, domain-shaped import payload. */
data class ImportedData(
    val counters: List<ImportedCounter>,
    val tags: List<com.ltq.count.domain.model.Tag>,
    val warnings: List<BackupWarning>,
)

data class ImportedCounter(
    val counter: com.ltq.count.domain.model.Counter,
    val events: List<com.ltq.count.domain.model.CountEvent>,
    /** Dedupe lookup key per imported event: `id:<id>` or `cmp:<counterId>:<millis>:<delta>`. */
    val dedupeKeys: Map<String, String>,
)

sealed interface BackupParseResult {
    data class Success(val data: ImportedData) : BackupParseResult
    data class Failure(val error: BackupError) : BackupParseResult
}
