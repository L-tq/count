package com.ltq.count.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Query("SELECT * FROM events WHERE counterId = :counterId ORDER BY timestamp DESC, id DESC")
    fun observeForCounter(counterId: String): Flow<List<CountEventEntity>>

    @Query("SELECT * FROM events WHERE counterId = :counterId ORDER BY timestamp ASC, id ASC")
    suspend fun allForCounter(counterId: String): List<CountEventEntity>

    @Query("SELECT * FROM events ORDER BY timestamp ASC, id ASC")
    suspend fun getAll(): List<CountEventEntity>

    @Query("SELECT * FROM events WHERE id = :eventId LIMIT 1")
    suspend fun find(eventId: String): CountEventEntity?

    @Query("SELECT COUNT(*) FROM events")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM events WHERE counterId = :counterId")
    suspend fun countForCounter(counterId: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(event: CountEventEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(events: List<CountEventEntity>)

    @Query("UPDATE events SET note = :note WHERE id = :eventId")
    suspend fun updateNote(eventId: String, note: String?)

    @Query("DELETE FROM events")
    suspend fun deleteAll()
}

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE ASC")
    fun observeTags(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE ASC")
    suspend fun getTags(): List<TagEntity>

    @Query("SELECT * FROM tags WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): TagEntity?

    @Query("SELECT * FROM tags WHERE id = :tagId LIMIT 1")
    suspend fun find(tagId: String): TagEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(tag: TagEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(tags: List<TagEntity>)

    @Query("DELETE FROM tags")
    suspend fun deleteAll()

    @Query("DELETE FROM tags WHERE id = :tagId")
    suspend fun deleteById(tagId: String): Int

    @Query("DELETE FROM tags WHERE id NOT IN (SELECT tagId FROM counter_tags)")
    suspend fun deleteUnused()
}

@Dao
interface CounterTagDao {
    @Query(
        """
        SELECT ct.counterId AS counterId, t.id AS tagId, t.name AS tagName, t.createdAt AS tagCreatedAt
        FROM counter_tags ct JOIN tags t ON t.id = ct.tagId
        ORDER BY t.name COLLATE NOCASE ASC
        """,
    )
    fun observeLinks(): Flow<List<CounterTagRow>>

    @Query("SELECT * FROM counter_tags WHERE counterId = :counterId")
    suspend fun forCounter(counterId: String): List<CounterTagEntity>

    @Query("SELECT * FROM counter_tags")
    suspend fun getAll(): List<CounterTagEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(links: List<CounterTagEntity>)

    @Query("DELETE FROM counter_tags WHERE counterId = :counterId")
    suspend fun deleteForCounter(counterId: String)

    @Query("DELETE FROM counter_tags")
    suspend fun deleteAll()
}
