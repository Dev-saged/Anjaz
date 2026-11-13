package com.anjaz.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(task: TaskEntity)

    @Update
    suspend fun update(task: TaskEntity)

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun byId(id: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE archived = 0 ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE archived = 0 AND deadline >= :from AND deadline < :to ORDER BY deadline")
    fun observeByDeadline(from: Long, to: Long): Flow<List<TaskEntity>>

    @Query("SELECT COUNT(*) FROM tasks WHERE archived = 0 AND status = :status")
    fun countByStatus(status: String): Flow<Int>

    @Query(
        "SELECT COUNT(*) FROM tasks WHERE archived = 0 AND status NOT IN ('APPROVED','REJECTED','CANCELLED') " +
            "AND deadline IS NOT NULL AND deadline < :now",
    )
    fun countOverdue(now: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM tasks WHERE archived = 0 AND status <> 'CANCELLED'")
    fun countActiveTotal(): Flow<Int>

    @Query("SELECT COUNT(*) FROM tasks")
    suspend fun countAll(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: TaskEntity)
}

@Dao
interface TaskEventDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(event: TaskEventEntity)

    @Query("SELECT * FROM task_events WHERE taskId = :taskId ORDER BY at")
    fun observeFor(taskId: String): Flow<List<TaskEventEntity>>

    @Query("SELECT COUNT(*) FROM task_events")
    suspend fun countAll(): Int

    @Query("SELECT * FROM task_events WHERE id = :id")
    suspend fun byId(id: String): TaskEventEntity?
}

@Dao
interface OutboxDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(op: OutboxEntity): Long

    @Query("SELECT * FROM outbox WHERE state IN ('LOCAL','FAILED') ORDER BY createdAt")
    suspend fun pending(): List<OutboxEntity>

    @Query("UPDATE outbox SET state = :state, attempts = attempts + :attemptInc, lastError = :error, sentAt = :sentAt WHERE id = :key")
    suspend fun mark(key: String, state: String, attemptInc: Int, error: String?, sentAt: Long?)

    @Query("SELECT COUNT(*) FROM outbox WHERE state <> 'SYNCED'")
    fun observeUnsynced(): Flow<Int>

    @Query("SELECT COUNT(*) FROM outbox")
    suspend fun countAll(): Int

    @Query("SELECT COUNT(*) FROM outbox WHERE state <> 'SYNCED' AND payload LIKE '%' || :id || '%'")
    suspend fun unsyncedFor(id: String): Int

    @Query("UPDATE outbox SET state = 'FAILED' WHERE state = 'SENDING'")
    suspend fun recoverSending()

    @Query("SELECT COUNT(*) FROM outbox WHERE state = 'NEEDS_ATTENTION'")
    fun observeNeedsAttention(): Flow<Int>
}

@Dao
interface DirectoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRegion(region: RegionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSquare(square: SquareEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCamp(camp: CampEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUser(user: UserEntity)

    @Query("SELECT COUNT(*) FROM squares")
    fun observeSquareCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM users WHERE active = 1")
    fun observeActiveUsers(): Flow<Int>

    @Query("SELECT * FROM users WHERE active = 1 AND role <> 'CENTRAL' ORDER BY displayName")
    fun observeTeam(): Flow<List<UserEntity>>
}
