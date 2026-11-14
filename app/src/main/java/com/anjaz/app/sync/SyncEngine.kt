package com.anjaz.app.sync

import com.anjaz.app.data.Clock
import com.anjaz.app.data.Outbox
import com.anjaz.app.data.csv
import com.anjaz.app.data.db.MessageEntity
import com.anjaz.app.data.db.MessageState
import com.anjaz.app.data.db.RoomEntity
import com.anjaz.app.data.db.SosEntity
import com.anjaz.app.data.db.AnjazDatabase
import com.anjaz.app.data.db.OutboxEntity
import com.anjaz.app.data.db.OutboxState
import com.anjaz.app.data.db.TaskEntity
import com.anjaz.app.data.TaskOpPayload
import com.anjaz.app.domain.Profile
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

data class PushResult(val sent: Int, val retryLater: Boolean)

@Singleton
class SyncEngine @Inject constructor(
    private val db: AnjazDatabase,
    private val remote: RemoteStore,
    private val notifier: Notifier,
    private val clock: Clock,
    private val media: com.anjaz.app.media.MediaTransport = com.anjaz.app.media.NoMedia,
) {
    private val json = Json { ignoreUnknownKeys = true }

    val enabled: Boolean get() = remote.enabled

    suspend fun pushPending(): PushResult {
        if (!remote.enabled) return PushResult(0, false)
        db.outbox().recoverSending()
        var sent = 0
        for (op in db.outbox().pending()) {
            db.outbox().mark(op.idempotencyKey, OutboxState.SENDING, 0, null, null)
            val result = runCatching { dispatch(op) }
            if (result.isSuccess) {
                db.outbox().mark(op.idempotencyKey, OutboxState.SYNCED, 1, null, clock.now())
                sent++
                runCatching { notifyFor(op) }
            } else {
                val e = result.exceptionOrNull()
                val terminal = e is RemoteDenied || op.attempts + 1 >= MAX_ATTEMPTS
                db.outbox().mark(op.idempotencyKey, if (terminal) OutboxState.NEEDS_ATTENTION else OutboxState.FAILED, 1, e?.message?.take(200), null)
                if (!terminal) return PushResult(sent, true)
            }
        }
        return PushResult(sent, false)
    }

    private suspend fun dispatch(op: OutboxEntity) {
        when (op.entity) {
            "task" -> dispatchTask(op)
            "sos" -> {
                val p = Outbox.parse(op.payload)
                val s = db.sos().byId(p.id) ?: return
                if (op.op == "create") remote.createSos(s.copy(status = "OPEN", updatedAt = s.createdAt))
                else remote.updateSosStatus(s.id, p.status ?: s.status, p.by, p.at ?: s.updatedAt)
            }
            "region" -> db.directory().regionById(Outbox.parse(op.payload).id)?.let { remote.createRegion(it) }
            "square" -> db.directory().squareById(Outbox.parse(op.payload).id)?.let { remote.createSquare(it) }
            "camp" -> db.directory().campById(Outbox.parse(op.payload).id)?.let { remote.createCamp(it) }
            "room" -> db.chat().roomById(Outbox.parse(op.payload).id)?.let { remote.createRoom(it) }
            "message" -> {
                val p = Outbox.parse(op.payload)
                if (op.op == "send") {
                    val m = db.chat().messageById(p.id) ?: return
                    if (m.mediaId.isNotEmpty() && m.localPath.isNotEmpty()) media.upload(m.roomId, m.mediaId, java.io.File(m.localPath), m.mediaMime)
                    remote.sendMessage(m, com.anjaz.app.media.previewOf(m))
                    db.chat().setState(m.id, MessageState.SENT)
                } else {
                    remote.markRead(p.roomId.orEmpty(), p.id, p.by.orEmpty())
                }
            }
        }
    }

    private suspend fun dispatchTask(op: OutboxEntity) {
        val p = json.decodeFromString<TaskOpPayload>(op.payload)
        val event = p.eventId?.let { db.taskEvents().byId(it) }
        when (op.op) {
            // الإنشاء يُرسل بحالته الأولى ثم تلحقه العمليات بالترتيب، وإلا ترفضه القواعد
            "create" -> db.tasks().byId(p.taskId)?.let { remote.createTask(it.copy(status = "NEW", updatedAt = it.createdAt)) }
            "status" -> db.tasks().byId(p.taskId)?.let { remote.updateTaskStatus(it.id, p.status, event?.at ?: it.updatedAt) }
        }
        event?.let { remote.appendEvent(it) }
    }

    private suspend fun notifyFor(op: OutboxEntity) {
        when {
            op.entity == "task" -> notifier.notify("task", json.decodeFromString<TaskOpPayload>(op.payload).taskId)
            op.entity == "sos" && op.op == "create" -> notifier.notify("sos", Outbox.parse(op.payload).id)
            op.entity == "message" && op.op == "send" -> notifier.notify("chat", Outbox.parse(op.payload).roomId.orEmpty())
        }
    }

    suspend fun applySos(list: List<SosEntity>) {
        list.forEach { r ->
            val local = db.sos().byId(r.id)
            if (local == null || (db.outbox().unsyncedFor(r.id) == 0 && r.updatedAt >= local.updatedAt)) db.sos().upsert(r)
        }
    }

    suspend fun applyRooms(list: List<RoomEntity>) {
        list.forEach { r ->
            val local = db.chat().roomById(r.id)
            val name = local?.name?.ifEmpty { r.name } ?: r.name
            if (local == null || r.lastAt >= local.lastAt) db.chat().upsertRoom(r.copy(name = name)) else if (local.name.isEmpty()) db.chat().upsertRoom(local.copy(name = name))
        }
    }

    suspend fun applyMessages(list: List<MessageEntity>) {
        list.forEach { r ->
            val local = db.chat().messageById(r.id)
            val merged = ((local?.readBy.orEmpty().csv()) + r.readBy.csv()).distinct().joinToString(",")
            val keep = if (local != null && local.mediaId.isNotEmpty()) {
                r.copy(localPath = local.localPath, mediaState = local.mediaState, transcript = local.transcript)
            } else r
            db.chat().upsertMessage(keep.copy(readBy = merged, state = MessageState.SENT))
            db.chat().touchRoom(r.roomId, com.anjaz.app.media.previewOf(r), r.createdAt)
        }
    }

    fun listenRoom(scope: CoroutineScope, roomId: String): Job? {
        if (!remote.enabled) return null
        return scope.launch { remote.messages(roomId).catch { }.collect { applyMessages(it) } }
    }

    suspend fun applyTasks(remoteTasks: List<TaskEntity>) {
        remoteTasks.forEach { r ->
            val local = db.tasks().byId(r.id)
            val pending = local != null && db.outbox().unsyncedFor(r.id) > 0
            if (local == null || (!pending && r.updatedAt > local.updatedAt)) db.tasks().upsert(r.copy(number = r.number ?: local?.number))
        }
    }

    suspend fun applyUsers(users: List<com.anjaz.app.data.db.UserEntity>) {
        users.forEach { db.directory().upsertUser(it) }
    }

    fun listen(scope: CoroutineScope, profile: Profile): List<Job> {
        if (!remote.enabled) return emptyList()
        return listOf(
            scope.launch { remote.tasks(profile).catch { }.collect { applyTasks(it) } },
            scope.launch { remote.users(profile).catch { }.collect { applyUsers(it) } },
            scope.launch { remote.regions(profile).catch { }.collect { r -> r.forEach { db.directory().upsertRegion(it) } } },
            scope.launch { remote.squares(profile).catch { }.collect { r -> r.forEach { db.directory().upsertSquare(it) } } },
            scope.launch { remote.camps(profile).catch { }.collect { r -> r.forEach { db.directory().upsertCamp(it) } } },
            scope.launch { remote.sos(profile).catch { }.collect { applySos(it) } },
            scope.launch { remote.rooms(profile).catch { }.collect { applyRooms(it) } },
        )
    }

    companion object { const val MAX_ATTEMPTS = 8 }
}
