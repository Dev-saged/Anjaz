package com.anjaz.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "regions")
@Serializable
data class RegionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val managerId: String?,
    val phone: String?,
    val active: Boolean,
    val updatedAt: Long,
)

@Entity(tableName = "squares", indices = [Index("regionId")])
@Serializable
data class SquareEntity(
    @PrimaryKey val id: String,
    val regionId: String,
    val name: String,
    val managerId: String?,
    val idNumber: String?,
    val phone: String?,
    val updatedAt: Long,
)

@Entity(tableName = "camps", indices = [Index("squareId")])
@Serializable
data class CampEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(defaultValue = "''") val regionId: String,
    val squareId: String,
    val name: String,
    val managerName: String?,
    val idNumber: String?,
    val phone: String?,
    val updatedAt: Long,
)

@Entity(tableName = "users", indices = [Index("username", unique = true)])
@Serializable
data class UserEntity(
    @PrimaryKey val id: String,
    val username: String,
    val displayName: String,
    val role: String,
    val regionId: String?,
    val squareId: String?,
    val active: Boolean,
    val updatedAt: Long,
)

@Entity(tableName = "tasks", indices = [Index("status"), Index("deadline"), Index("assigneeId")])
@Serializable
data class TaskEntity(
    @PrimaryKey val id: String,
    val number: Long?,
    val title: String,
    val description: String,
    val createdBy: String,
    val regionId: String?,
    val squareId: String?,
    val assigneeId: String?,
    val priority: String,
    val status: String,
    val deadline: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val archived: Boolean,
)

@Entity(tableName = "task_events", indices = [Index("taskId")])
@Serializable
data class TaskEventEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val fromStatus: String?,
    val toStatus: String,
    val actorId: String,
    val note: String?,
    val at: Long,
)

@Entity(tableName = "outbox", indices = [Index("state"), Index("createdAt")])
data class OutboxEntity(
    @PrimaryKey @ColumnInfo(name = "id") val idempotencyKey: String,
    val entity: String,
    val op: String,
    val payload: String,
    val state: String,
    val attempts: Int,
    val lastError: String?,
    val createdAt: Long,
    val sentAt: Long?,
)

object OutboxState {
    const val LOCAL = "LOCAL"
    const val SENDING = "SENDING"
    const val SYNCED = "SYNCED"
    const val FAILED = "FAILED"
    const val NEEDS_ATTENTION = "NEEDS_ATTENTION"
}

@Entity(tableName = "sos", indices = [Index("createdAt")])
@Serializable
data class SosEntity(
    @PrimaryKey val id: String,
    val senderId: String,
    val senderName: String,
    val regionId: String?,
    val squareId: String?,
    val status: String,
    val note: String,
    val lat: Double?,
    val lng: Double?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "rooms", indices = [Index("lastAt")])
@Serializable
data class RoomEntity(
    @PrimaryKey val id: String,
    val type: String,
    val name: String,
    val members: String,
    val lastMessage: String,
    val lastAt: Long,
)

@Entity(tableName = "messages", indices = [Index("roomId", "createdAt")])
@Serializable
data class MessageEntity(
    @PrimaryKey val id: String,
    val roomId: String,
    val senderId: String,
    val type: String,
    val text: String,
    val createdAt: Long,
    val readBy: String,
    val state: String,
)

object MessageState {
    const val LOCAL = "LOCAL"
    const val SENT = "SENT"
}
