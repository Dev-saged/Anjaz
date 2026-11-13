package com.anjaz.app.data

import com.anjaz.app.data.db.OutboxEntity
import com.anjaz.app.data.db.OutboxState
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class EntityOp(val id: String, val roomId: String? = null, val by: String? = null, val status: String? = null, val at: Long? = null)

object Outbox {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    fun op(entity: String, op: String, payload: EntityOp, now: Long) = OutboxEntity(
        idempotencyKey = UUID.randomUUID().toString(), entity = entity, op = op,
        payload = json.encodeToString(payload), state = OutboxState.LOCAL, attempts = 0,
        lastError = null, createdAt = now, sentAt = null,
    )

    fun parse(payload: String): EntityOp = json.decodeFromString(payload)
}
