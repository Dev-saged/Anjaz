package com.anjaz.app.sync

import com.anjaz.app.data.Clock
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
        if (op.entity != "task") return
        val p = json.decodeFromString<TaskOpPayload>(op.payload)
        when (op.op) {
            "create" -> db.tasks().byId(p.taskId)?.let { remote.createTask(it) }
            "status" -> db.tasks().byId(p.taskId)?.let { remote.updateTaskStatus(it.id, p.status, it.updatedAt) }
        }
        p.eventId?.let { id -> db.taskEvents().byId(id)?.let { remote.appendEvent(it) } }
    }

    private suspend fun notifyFor(op: OutboxEntity) {
        if (op.entity == "task") notifier.notify("task", json.decodeFromString<TaskOpPayload>(op.payload).taskId)
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
        )
    }

    companion object { const val MAX_ATTEMPTS = 8 }
}
