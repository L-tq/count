package com.ltq.count.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CounterDao {
    @Query(
        """
        SELECT c.id AS id, c.name AS name, c.initialValue AS initialValue, c.allowNegative AS allowNegative,
               c.createdAt AS createdAt, c.sortOrder AS sortOrder, c.pinned AS pinned, c.defaultStep AS defaultStep,
               COALESCE(SUM(e.delta), 0) AS deltaSum, COUNT(e.id) AS eventCount
        FROM counters c LEFT JOIN events e ON e.counterId = c.id
        GROUP BY c.id
        ORDER BY c.pinned DESC, c.sortOrder ASC, c.createdAt ASC
        """,
    )
    fun observeAggregates(): Flow<List<CounterAggregateRow>>

    @Query(
        """
        SELECT c.id AS id, c.name AS name, c.initialValue AS initialValue, c.allowNegative AS allowNegative,
               c.createdAt AS createdAt, c.sortOrder AS sortOrder, c.pinned AS pinned, c.defaultStep AS defaultStep,
               COALESCE(SUM(e.delta), 0) AS deltaSum, COUNT(e.id) AS eventCount
        FROM counters c LEFT JOIN events e ON e.counterId = c.id
        WHERE c.id = :counterId
        GROUP BY c.id
        """,
    )
    fun observeAggregate(counterId: String): Flow<CounterAggregateRow?>

    @Query(
        """
        SELECT c.id AS id, c.name AS name, c.initialValue AS initialValue, c.allowNegative AS allowNegative,
               c.createdAt AS createdAt, c.sortOrder AS sortOrder, c.pinned AS pinned, c.defaultStep AS defaultStep,
               COALESCE(SUM(e.delta), 0) AS deltaSum, COUNT(e.id) AS eventCount
        FROM counters c LEFT JOIN events e ON e.counterId = c.id
        WHERE c.id = :counterId
        GROUP BY c.id
        """,
    )
    suspend fun aggregate(counterId: String): CounterAggregateRow?

    @Query("SELECT * FROM counters WHERE id = :counterId")
    suspend fun find(counterId: String): CounterEntity?

    @Query("SELECT * FROM counters ORDER BY pinned DESC, sortOrder ASC, createdAt ASC")
    suspend fun getAll(): List<CounterEntity>

    @Query("SELECT COALESCE(SUM(delta), 0) FROM events WHERE counterId = :counterId")
    suspend fun sumDelta(counterId: String): Long

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM counters WHERE pinned = 0")
    suspend fun maxNormalSortOrder(): Int

    @Query("SELECT COUNT(*) FROM counters")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(counter: CounterEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(counters: List<CounterEntity>)

    @Update
    suspend fun update(counter: CounterEntity)

    @Query("UPDATE counters SET sortOrder = :sortOrder WHERE id = :counterId")
    suspend fun setSortOrder(counterId: String, sortOrder: Int)

    @Query("UPDATE counters SET pinned = :pinned WHERE id = :counterId")
    suspend fun setPinned(counterId: String, pinned: Boolean)

    @Query("DELETE FROM counters WHERE id = :counterId")
    suspend fun delete(counterId: String)

    @Query("DELETE FROM counters")
    suspend fun deleteAll()
}
