package com.ltq.count.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "counters")
data class CounterEntity(
    @PrimaryKey val id: String,
    val name: String,
    val initialValue: Int,
    val allowNegative: Boolean,
    val createdAt: Long,
    val sortOrder: Int,
    val pinned: Boolean,
    val defaultStep: Int,
)

@Entity(
    tableName = "events",
    foreignKeys = [
        ForeignKey(
            entity = CounterEntity::class,
            parentColumns = ["id"],
            childColumns = ["counterId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("counterId"),
        Index(value = ["counterId", "timestamp"]),
        Index("timestamp"),
    ],
)
data class CountEventEntity(
    @PrimaryKey val id: String,
    val counterId: String,
    val delta: Int,
    /** UTC epoch milliseconds; the UI converts to the device time zone for display. */
    val timestamp: Long,
    val note: String?,
    val source: String,
    val sessionId: String?,
)

@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
)

@Entity(
    tableName = "counter_tags",
    primaryKeys = ["counterId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = CounterEntity::class,
            parentColumns = ["id"],
            childColumns = ["counterId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tagId")],
)
data class CounterTagEntity(
    val counterId: String,
    val tagId: String,
)

/** Aggregated projection; the current value is computed by SQL, never stored. */
data class CounterAggregateRow(
    val id: String,
    val name: String,
    val initialValue: Int,
    val allowNegative: Boolean,
    val createdAt: Long,
    val sortOrder: Int,
    val pinned: Boolean,
    val defaultStep: Int,
    @ColumnInfo(name = "deltaSum") val deltaSum: Long,
    val eventCount: Int,
)

data class CounterTagRow(
    val counterId: String,
    val tagId: String,
    val tagName: String,
    val tagCreatedAt: Long,
)
