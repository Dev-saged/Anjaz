package com.anjaz.app.data

import androidx.room.withTransaction
import com.anjaz.app.data.db.AnjazDatabase
import com.anjaz.app.data.db.OutboxEntity
import com.anjaz.app.data.db.OutboxState
import com.anjaz.app.data.db.TaskEntity
import com.anjaz.app.data.db.TaskEventEntity
import com.anjaz.app.domain.Outcome
import com.anjaz.app.domain.Priority
import com.anjaz.app.domain.TaskStatus
import com.anjaz.app.schema.SchemaValidator
import com.anjaz.app.schema.Schemas
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class TaskOpPayload(val taskId: String, val status: String, val note: String? = null)

@Singleton
class TaskRepository @Inject constructor(
    private val db: AnjazDatabase,
    private val clock: Clock,
) {
    private val json = Json { encodeDefaults = true }

    suspend fun create(values: Map<String, String>, actorId: String): Outcome<String> {
        val errors = SchemaValidator.validate(Schemas.task, values)
        if (errors.isNotEmpty()) return Outcome.Failure(errors.values.first())
        val clean = SchemaValidator.clean(Schemas.task, values)
        val now = clock.now()
        val id = UUID.randomUUID().toString()
        val task = TaskEntity(
            id = id, number = null, title = clean.getValue("title"), description = clean.getValue("description"),
            createdBy = actorId, regionId = null, squareId = null, assigneeId = null,
            priority = Priority.parse(clean.getValue("priority")).name,
            status = TaskStatus.NEW.name, deadline = deadlineFor(clean.getValue("deadline"), now),
            createdAt = now, updatedAt = now, archived = false,
        )
        return runCatching {
            db.withTransaction {
                db.tasks().insert(task)
                db.taskEvents().insert(event(id, null, TaskStatus.NEW, actorId, null, now))
                db.outbox().insertIgnore(outbox("task", "create", TaskOpPayload(id, TaskStatus.NEW.name), now))
            }
            Outcome.Ok(id)
        }.getOrElse { Outcome.Failure("تعذّر الحفظ محلياً") }
    }

    suspend fun transition(taskId: String, to: TaskStatus, actorId: String, note: String? = null): Outcome<Unit> {
        val now = clock.now()
        return runCatching {
            db.withTransaction {
                val task = db.tasks().byId(taskId) ?: return@withTransaction Outcome.Failure("المهمة غير موجودة")
                val from = TaskStatus.parse(task.status)
                if (!from.canMoveTo(to)) return@withTransaction Outcome.Failure("انتقال غير مسموح")
                db.tasks().update(task.copy(status = to.name, updatedAt = now))
                db.taskEvents().insert(event(taskId, from, to, actorId, note, now))
                db.outbox().insertIgnore(outbox("task", "status", TaskOpPayload(taskId, to.name, note), now))
                Outcome.Ok(Unit)
            }
        }.getOrElse { Outcome.Failure("تعذّر الحفظ محلياً") }
    }

    private fun event(taskId: String, from: TaskStatus?, to: TaskStatus, actor: String, note: String?, at: Long) =
        TaskEventEntity(UUID.randomUUID().toString(), taskId, from?.name, to.name, actor, note, at)

    private fun outbox(entity: String, op: String, payload: TaskOpPayload, now: Long) = OutboxEntity(
        idempotencyKey = UUID.randomUUID().toString(), entity = entity, op = op,
        payload = json.encodeToString(payload), state = OutboxState.LOCAL, attempts = 0,
        lastError = null, createdAt = now, sentAt = null,
    )

    private fun deadlineFor(key: String, now: Long): Long? {
        val days = when (key) { "today" -> 0; "tomorrow" -> 1; "3d" -> 3; else -> return null }
        return Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, days)
            set(Calendar.HOUR_OF_DAY, 15); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
