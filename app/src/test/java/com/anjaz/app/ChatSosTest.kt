package com.anjaz.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.anjaz.app.data.ChatRepository
import com.anjaz.app.data.Clock
import com.anjaz.app.data.SosRepository
import com.anjaz.app.data.SosStatus
import com.anjaz.app.data.db.AnjazDatabase
import com.anjaz.app.data.db.MessageEntity
import com.anjaz.app.data.db.MessageState
import com.anjaz.app.data.db.RoomEntity
import com.anjaz.app.data.db.SosEntity
import com.anjaz.app.data.db.TaskEntity
import com.anjaz.app.data.db.UserEntity
import com.anjaz.app.data.directRoomId
import com.anjaz.app.domain.Outcome
import com.anjaz.app.domain.Profile
import com.anjaz.app.sync.Notifier
import com.anjaz.app.sync.RemoteStore
import com.anjaz.app.sync.SyncEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class Remote2 : RemoteStore {
    override val enabled = true
    val log = mutableListOf<String>()
    override suspend fun createTask(task: TaskEntity) = Unit
    override suspend fun updateTaskStatus(taskId: String, status: String, updatedAt: Long) = Unit
    override suspend fun appendEvent(event: com.anjaz.app.data.db.TaskEventEntity) = Unit
    override fun tasks(profile: Profile): Flow<List<TaskEntity>> = emptyFlow()
    override fun users(profile: Profile): Flow<List<UserEntity>> = emptyFlow()
    override suspend fun createSos(sos: SosEntity) { log += "sos:${sos.status}" }
    override suspend fun updateSosStatus(id: String, status: String, by: String?, updatedAt: Long) { log += "sos->$status by $by" }
    override suspend fun createRoom(room: RoomEntity) { log += "room:${room.members}" }
    override suspend fun sendMessage(message: MessageEntity, roomLast: String) { log += "msg:${message.text}" }
    override suspend fun markRead(roomId: String, messageId: String, uid: String) { log += "read:$uid" }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ChatSosTest {
    private lateinit var db: AnjazDatabase
    private lateinit var remote: Remote2
    private lateinit var engine: SyncEngine
    private lateinit var sos: SosRepository
    private lateinit var chat: ChatRepository
    private val notified = mutableListOf<String>()
    private var now = 5_000L
    private val clock = Clock { now++ }
    private val staff = Profile("s1", "s1", "علي", "FIELD_STAFF", "A", "A1")
    private val lead = Profile("m1", "m1", "مدير", "SQUARE_MANAGER", "A", "A1")
    private val other = UserEntity("m1", "m1", "مدير", "SQUARE_MANAGER", "A", "A1", true, 1)

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AnjazDatabase::class.java).build()
        remote = Remote2()
        engine = SyncEngine(db, remote, Notifier { k, id -> notified += "$k:$id" }, clock)
        sos = SosRepository(db, clock)
        chat = ChatRepository(db, clock)
    }

    @After fun tearDown() = db.close()

    @Test fun sosFlowOpenAckResolve() = runBlocking {
        val id = (sos.send(staff, "  تعطل المركبة   ", 31.5, 34.4) as Outcome.Ok).value
        assertEquals("تعطل المركبة", db.sos().byId(id)!!.note)
        assertTrue(sos.advance(id, staff) is Outcome.Failure)
        assertTrue(sos.advance(id, lead) is Outcome.Ok)
        assertEquals(SosStatus.ACK, db.sos().byId(id)!!.status)
        assertTrue(sos.advance(id, lead) is Outcome.Ok)
        assertTrue(sos.advance(id, lead) is Outcome.Failure)
        engine.pushPending()
        assertEquals(listOf("sos:OPEN", "sos->ACK by m1", "sos->RESOLVED by m1"), remote.log)
        assertEquals(listOf("sos:$id"), notified)
    }

    @Test fun sosRejectsLongNote() = runBlocking {
        assertTrue(sos.send(staff, "ا".repeat(501), null, null) is Outcome.Failure)
    }

    @Test fun directRoomIsDeterministicAndCreatedOnce() = runBlocking {
        assertEquals(directRoomId("a", "b"), directRoomId("b", "a"))
        val id1 = chat.openDirect(staff, other)
        val id2 = chat.openDirect(staff, other)
        assertEquals(id1, id2)
        assertEquals(1, db.outbox().countAll())
    }

    @Test fun sendMessageIsLocalFirstThenSent() = runBlocking {
        val room = chat.openDirect(staff, other)
        assertTrue(chat.send(room, staff, "   ") is Outcome.Failure)
        val id = (chat.send(room, staff, "وصلت إلى الموقع") as Outcome.Ok).value
        assertEquals(MessageState.LOCAL, db.chat().messageById(id)!!.state)
        assertEquals("وصلت إلى الموقع", db.chat().roomById(room)!!.lastMessage)
        engine.pushPending()
        assertEquals(MessageState.SENT, db.chat().messageById(id)!!.state)
        assertEquals(listOf("room:m1,s1", "msg:وصلت إلى الموقع"), remote.log)
        assertEquals(listOf("chat:$room"), notified)
    }

    @Test fun markReadOnlyOthersMessagesAndOnce() = runBlocking {
        val room = chat.openDirect(staff, other)
        chat.send(room, staff, "مرحبا")
        db.chat().upsertMessage(MessageEntity("in1", room, "m1", "text", "أهلاً", 9_000, "m1", "SENT"))
        chat.markRead(room, staff)
        assertEquals("m1,s1", db.chat().messageById("in1")!!.readBy)
        chat.markRead(room, staff)
        engine.pushPending()
        assertEquals(1, remote.log.count { it.startsWith("read:") })
    }

    @Test fun incomingMessagesMergeReadByAndUpdateRoom() = runBlocking {
        val room = chat.openDirect(staff, other)
        val local = MessageEntity("x1", room, "s1", "text", "مرحبا", 6_000, "s1", "SENT")
        db.chat().upsertMessage(local)
        engine.applyMessages(listOf(local.copy(readBy = "m1")))
        assertEquals("s1,m1", db.chat().messageById("x1")!!.readBy)
        assertEquals("مرحبا", db.chat().roomById(room)!!.lastMessage)
    }
}
