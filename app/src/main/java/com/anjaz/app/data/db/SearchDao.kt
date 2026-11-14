package com.anjaz.app.data.db

import androidx.room.Dao
import androidx.room.Query

@Dao
interface SearchDao {
    @Query("SELECT * FROM tasks WHERE archived = 0 AND (title LIKE :q ESCAPE '\\' OR description LIKE :q ESCAPE '\\') ORDER BY updatedAt DESC LIMIT 20")
    suspend fun tasks(q: String): List<TaskEntity>

    @Query("SELECT * FROM messages WHERE type != 'SYSTEM' AND (text LIKE :q ESCAPE '\\' OR transcript LIKE :q ESCAPE '\\') ORDER BY createdAt DESC LIMIT 20")
    suspend fun messages(q: String): List<MessageEntity>

    @Query("SELECT * FROM users WHERE deleted = 0 AND (displayName LIKE :q ESCAPE '\\' OR username LIKE :q ESCAPE '\\') ORDER BY displayName LIMIT 20")
    suspend fun users(q: String): List<UserEntity>

    @Query("SELECT * FROM squares WHERE name LIKE :q ESCAPE '\\' OR phone LIKE :q ESCAPE '\\' ORDER BY name LIMIT 20")
    suspend fun squares(q: String): List<SquareEntity>

    @Query("SELECT * FROM camps WHERE name LIKE :q ESCAPE '\\' OR managerName LIKE :q ESCAPE '\\' OR phone LIKE :q ESCAPE '\\' ORDER BY name LIMIT 20")
    suspend fun camps(q: String): List<CampEntity>
}
