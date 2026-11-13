package com.anjaz.app.sync

import com.anjaz.app.data.db.MessageEntity
import com.anjaz.app.data.db.RoomEntity
import com.anjaz.app.data.db.SosEntity
import com.anjaz.app.data.db.TaskEntity
import com.anjaz.app.data.db.TaskEventEntity
import com.anjaz.app.data.db.UserEntity
import com.anjaz.app.domain.Profile
import kotlinx.coroutines.flow.Flow

class RemoteDenied(message: String) : Exception(message)

interface RemoteStore {
    val enabled: Boolean
    suspend fun createTask(task: TaskEntity)
    suspend fun updateTaskStatus(taskId: String, status: String, updatedAt: Long)
    suspend fun appendEvent(event: TaskEventEntity)
    fun tasks(profile: Profile): Flow<List<TaskEntity>>
    fun users(profile: Profile): Flow<List<UserEntity>>

    suspend fun createSos(sos: SosEntity) = Unit
    suspend fun updateSosStatus(id: String, status: String, by: String?, updatedAt: Long) = Unit
    suspend fun createRoom(room: RoomEntity) = Unit
    suspend fun sendMessage(message: MessageEntity, roomLast: String) = Unit
    suspend fun markRead(roomId: String, messageId: String, uid: String) = Unit
    fun sos(profile: Profile): Flow<List<SosEntity>> = kotlinx.coroutines.flow.emptyFlow()
    fun rooms(profile: Profile): Flow<List<RoomEntity>> = kotlinx.coroutines.flow.emptyFlow()
    fun messages(roomId: String): Flow<List<MessageEntity>> = kotlinx.coroutines.flow.emptyFlow()
}

object NoRemoteStore : RemoteStore {
    override val enabled = false
    override suspend fun createTask(task: TaskEntity) = Unit
    override suspend fun updateTaskStatus(taskId: String, status: String, updatedAt: Long) = Unit
    override suspend fun appendEvent(event: TaskEventEntity) = Unit
    override fun tasks(profile: Profile): Flow<List<TaskEntity>> = kotlinx.coroutines.flow.emptyFlow()
    override fun users(profile: Profile): Flow<List<UserEntity>> = kotlinx.coroutines.flow.emptyFlow()
}

fun interface Notifier {
    suspend fun notify(kind: String, id: String)
}
