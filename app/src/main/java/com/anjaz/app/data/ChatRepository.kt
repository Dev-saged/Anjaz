package com.anjaz.app.data

import androidx.room.withTransaction
import com.anjaz.app.data.db.AnjazDatabase
import com.anjaz.app.data.db.MessageEntity
import com.anjaz.app.data.db.MessageState
import com.anjaz.app.data.db.RoomEntity
import com.anjaz.app.data.db.UserEntity
import com.anjaz.app.domain.Outcome
import com.anjaz.app.domain.Profile
import com.anjaz.app.schema.SchemaValidator
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

fun directRoomId(a: String, b: String): String = "d_" + listOf(a, b).sorted().joinToString("_")

fun String.csv(): List<String> = if (isEmpty()) emptyList() else split(',')

@Singleton
class ChatRepository @Inject constructor(private val db: AnjazDatabase, private val clock: Clock) {
    fun rooms(): Flow<List<RoomEntity>> = db.chat().observeRooms()
    fun messages(roomId: String): Flow<List<MessageEntity>> = db.chat().observeMessages(roomId)
    fun contacts(me: String): Flow<List<UserEntity>> = db.contacts().observeContacts(me)

    suspend fun openDirect(me: Profile, other: UserEntity): String {
        val id = directRoomId(me.uid, other.id)
        if (db.chat().roomById(id) != null) return id
        val now = clock.now()
        db.withTransaction {
            db.chat().upsertRoom(RoomEntity(id, "direct", other.displayName, listOf(me.uid, other.id).sorted().joinToString(","), "", now))
            db.outbox().insertIgnore(Outbox.op("room", "create", EntityOp(id), now))
        }
        return id
    }

    suspend fun send(roomId: String, me: Profile, text: String): Outcome<String> {
        val clean = SchemaValidator.sanitize(text)
        if (clean.isEmpty()) return Outcome.Failure("الرسالة فارغة")
        if (clean.length > 4000) return Outcome.Failure("الرسالة طويلة")
        if (db.chat().roomById(roomId) == null) return Outcome.Failure("المحادثة غير موجودة")
        val now = clock.now()
        val m = MessageEntity(UUID.randomUUID().toString(), roomId, me.uid, "text", clean, now, me.uid, MessageState.LOCAL)
        return runCatching {
            db.withTransaction {
                db.chat().insertMessage(m)
                db.chat().touchRoom(roomId, clean.take(200), now)
                db.outbox().insertIgnore(Outbox.op("message", "send", EntityOp(m.id, roomId), now))
            }
            Outcome.Ok(m.id)
        }.getOrElse { Outcome.Failure("تعذّر الحفظ محلياً") }
    }

    suspend fun markRead(roomId: String, me: Profile) {
        val unread = db.chat().unreadFor(roomId, me.uid)
        if (unread.isEmpty()) return
        val now = clock.now()
        db.withTransaction {
            unread.forEach { m ->
                db.chat().setReadBy(m.id, (m.readBy.csv() + me.uid).distinct().joinToString(","))
                db.outbox().insertIgnore(Outbox.op("message", "read", EntityOp(m.id, roomId, me.uid), now))
            }
        }
    }
}
