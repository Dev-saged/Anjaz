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

    @Query("SELECT * FROM tasks")
    suspend fun all(): List<TaskEntity>
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

    @Query("SELECT * FROM task_events")
    suspend fun all(): List<TaskEventEntity>
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

    @Query("SELECT * FROM regions ORDER BY name")
    fun observeRegions(): Flow<List<RegionEntity>>

    @Query("SELECT * FROM squares ORDER BY name")
    fun observeSquares(): Flow<List<SquareEntity>>

    @Query("SELECT * FROM camps ORDER BY name")
    fun observeCamps(): Flow<List<CampEntity>>

    @Query("SELECT * FROM regions WHERE id = :id")
    suspend fun regionById(id: String): RegionEntity?

    @Query("SELECT * FROM squares WHERE id = :id")
    suspend fun squareById(id: String): SquareEntity?

    @Query("SELECT * FROM camps WHERE id = :id")
    suspend fun campById(id: String): CampEntity?

    @Query("SELECT * FROM regions")
    suspend fun allRegions(): List<RegionEntity>

    @Query("SELECT * FROM squares")
    suspend fun allSquares(): List<SquareEntity>

    @Query("SELECT * FROM camps")
    suspend fun allCamps(): List<CampEntity>

    @Query("SELECT * FROM users")
    suspend fun allUsers(): List<UserEntity>

    @Query("SELECT COUNT(*) FROM squares")
    fun observeSquareCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM users WHERE active = 1")
    fun observeActiveUsers(): Flow<Int>

    @Query("SELECT * FROM users WHERE active = 1 AND role <> 'CENTRAL' ORDER BY displayName")
    fun observeTeam(): Flow<List<UserEntity>>
}

@Dao
interface SosDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(sos: SosEntity)

    @Query("SELECT * FROM sos WHERE id = :id")
    suspend fun byId(id: String): SosEntity?

    @Query("SELECT * FROM sos ORDER BY createdAt DESC LIMIT 50")
    fun observeRecent(): Flow<List<SosEntity>>

    @Query("SELECT * FROM sos")
    suspend fun all(): List<SosEntity>

    @Query("SELECT COUNT(*) FROM sos WHERE status <> 'RESOLVED'")
    fun observeOpenCount(): Flow<Int>
}

@Dao
interface ChatDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRoom(room: RoomEntity)

    @Query("SELECT * FROM rooms WHERE id = :id")
    suspend fun roomById(id: String): RoomEntity?

    @Query("SELECT * FROM rooms ORDER BY lastAt DESC")
    fun observeRooms(): Flow<List<RoomEntity>>

    @Query("SELECT * FROM rooms")
    suspend fun allRooms(): List<RoomEntity>

    @Query("SELECT * FROM messages")
    suspend fun allMessages(): List<MessageEntity>

    @Query("UPDATE rooms SET lastMessage = :text, lastAt = :at WHERE id = :roomId AND lastAt <= :at")
    suspend fun touchRoom(roomId: String, text: String, at: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMessage(m: MessageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessage(m: MessageEntity)

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun messageById(id: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE roomId = :roomId ORDER BY createdAt")
    fun observeMessages(roomId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE roomId = :roomId AND senderId <> :me AND readBy NOT LIKE '%' || :me || '%'")
    suspend fun unreadFor(roomId: String, me: String): List<MessageEntity>

    @Query("UPDATE messages SET readBy = :readBy WHERE id = :id")
    suspend fun setReadBy(id: String, readBy: String)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteMessage(id: String)

    @Query("UPDATE messages SET localPath = :path, mediaState = :state WHERE id = :id")
    suspend fun setMedia(id: String, path: String, state: String)

    @Query("UPDATE messages SET transcript = :text WHERE id = :id")
    suspend fun setTranscript(id: String, text: String)

    @Query("SELECT * FROM messages WHERE roomId = :roomId AND senderId <> :me AND mediaState = 'REMOTE'")
    suspend fun pendingDownloads(roomId: String, me: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE mediaState = 'LOCAL' AND localPath <> ''")
    suspend fun localMedia(): List<MessageEntity>

    @Query("UPDATE messages SET state = :state WHERE id = :id")
    suspend fun setState(id: String, state: String)
}

@Dao
interface ContactDao {
    @Query("SELECT * FROM users WHERE active = 1 AND id <> :me ORDER BY displayName")
    fun observeContacts(me: String): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE id <> :me ORDER BY active DESC, displayName")
    fun observeAll(me: String): Flow<List<UserEntity>>
}
