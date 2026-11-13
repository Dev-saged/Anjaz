package com.anjaz.app.data

import androidx.room.withTransaction
import com.anjaz.app.data.db.AnjazDatabase
import com.anjaz.app.data.db.SosEntity
import com.anjaz.app.domain.Outcome
import com.anjaz.app.domain.Profile
import com.anjaz.app.schema.SchemaValidator
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

object SosStatus {
    const val OPEN = "OPEN"
    const val ACK = "ACK"
    const val RESOLVED = "RESOLVED"
    fun next(s: String): String? = when (s) { OPEN -> ACK; ACK -> RESOLVED; else -> null }
}

@Singleton
class SosRepository @Inject constructor(private val db: AnjazDatabase, private val clock: Clock) {
    fun recent(): Flow<List<SosEntity>> = db.sos().observeRecent()
    fun openCount(): Flow<Int> = db.sos().observeOpenCount()

    suspend fun send(profile: Profile, note: String, lat: Double?, lng: Double?): Outcome<String> {
        val clean = SchemaValidator.sanitize(note)
        if (clean.length > 500) return Outcome.Failure("الملاحظة طويلة")
        val now = clock.now()
        val sos = SosEntity(
            id = UUID.randomUUID().toString(), senderId = profile.uid, senderName = profile.displayName,
            regionId = profile.regionId, squareId = profile.squareId, status = SosStatus.OPEN, note = clean,
            lat = lat, lng = lng, createdAt = now, updatedAt = now,
        )
        return runCatching {
            db.withTransaction {
                db.sos().upsert(sos)
                db.outbox().insertIgnore(Outbox.op("sos", "create", EntityOp(sos.id), now))
            }
            Outcome.Ok(sos.id)
        }.getOrElse { Outcome.Failure("تعذّر الحفظ محلياً") }
    }

    suspend fun advance(id: String, by: Profile): Outcome<Unit> {
        if (!by.canAssign) return Outcome.Failure("غير مسموح")
        val now = clock.now()
        return runCatching {
            db.withTransaction {
                val s = db.sos().byId(id) ?: return@withTransaction Outcome.Failure("غير موجود")
                val next = SosStatus.next(s.status) ?: return@withTransaction Outcome.Failure("الحالة نهائية")
                db.sos().upsert(s.copy(status = next, updatedAt = now))
                db.outbox().insertIgnore(Outbox.op("sos", "status", EntityOp(id, by = by.uid, status = next, at = now), now))
                Outcome.Ok(Unit)
            }
        }.getOrElse { Outcome.Failure("تعذّر الحفظ محلياً") }
    }
}
