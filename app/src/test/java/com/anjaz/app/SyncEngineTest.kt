package com.anjaz.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.anjaz.app.data.Clock
import com.anjaz.app.data.TaskRepository
import com.anjaz.app.data.db.AnjazDatabase
import com.anjaz.app.data.db.OutboxState
import com.anjaz.app.data.db.TaskEntity
import com.anjaz.app.data.db.TaskEventEntity
import com.anjaz.app.data.db.UserEntity
import com.anjaz.app.domain.Outcome
import com.anjaz.app.domain.Profile
import com.anjaz.app.domain.TaskStatus
import com.anjaz.app.sync.Notifier
import com.anjaz.app.sync.RemoteDenied
import com.anjaz.app.sync.RemoteStore
import com.anjaz.app.sync.SyncEngine
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class FakeRemote : RemoteStore {
    override val enabled = true
    val created = mutableListOf<String>()
    val statuses = mutableListOf<Pair<String, String>>()
    val events = mutableListOf<String>()
    var failWith: Exception? = null
    var denyTaskId: String? = null

    override suspend fun createTask(task: TaskEntity) {
        failWith?.let { throw it }
        if (task.id == denyTaskId) throw RemoteDenied("denied")
        created += task.id
    }

    override suspend fun updateTaskStatus(taskId: String, status: String, updatedAt: Long) {
        failWith?.let { throw it }
        if (taskId == denyTaskId) throw RemoteDenied("denied")
        statuses += taskId to status
    }

    override suspend fun appendEvent(event: TaskEventEntity) { events += event.id }
    override fun tasks(profile: Profile): Flow<List<TaskEntity>> = emptyFlow()
    override fun users(profile: Profile): Flow<List<UserEntity>> = emptyFlow()
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class SyncEngineTest {
    private lateinit var db: AnjazDatabase
    private lateinit var remote: FakeRemote
    private lateinit var engine: SyncEngine
    private lateinit var repo: TaskRepository
    private val notified = mutableListOf<String>()
    private var now = 1_000L
    private val clock = Clock { now }
    private val values = mapOf("title" to "مهمة", "description" to "", "priority" to "NORMAL", "deadline" to "none")

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AnjazDatabase::class.java).build()
        remote = FakeRemote()
        engine = SyncEngine(db, remote, Notifier { k, id -> notified += "$k:$id" }, clock)
        repo = TaskRepository(db, clock)
    }

    @After fun tearDown() = db.close()

    private fun create() = runBlocking { (repo.create(values, "u1", "A", "A1", "u2") as Outcome.Ok).value }
    private fun states() = runBlocking { db.outbox().pending().map { it.state } }

    @Test fun pushSendsTaskAndEventAndMarksSynced() = runBlocking {
        val id = create()
        val r = engine.pushPending()
        assertEquals(1, r.sent)
        assertFalse(r.retryLater)
        assertEquals(listOf(id), remote.created)
        assertEquals(1, remote.events.size)
        assertTrue(db.outbox().pending().isEmpty())
        assertEquals(listOf("task:$id"), notified)
    }

    @Test fun statusChangePushesOnlyStatusFields() = runBlocking {
        val id = create()
        repo.transition(id, TaskStatus.IN_PROGRESS, "u1")
        engine.pushPending()
        assertEquals(listOf(id to "IN_PROGRESS"), remote.statuses)
        assertEquals(2, remote.events.size)
    }

    @Test fun networkFailureKeepsOrderAndRetries() = runBlocking {
        val id = create()
        repo.transition(id, TaskStatus.IN_PROGRESS, "u1")
        remote.failWith = IOException("offline")
        val r1 = engine.pushPending()
        assertTrue(r1.retryLater)
        assertEquals(0, r1.sent)
        assertEquals(2, db.outbox().pending().size)
        assertTrue(remote.statuses.isEmpty())
        remote.failWith = null
        val r2 = engine.pushPending()
        assertEquals(2, r2.sent)
        assertEquals(listOf(id), remote.created)
        assertEquals(listOf(id to "IN_PROGRESS"), remote.statuses)
    }

    @Test fun deniedOpNeedsAttentionAndDoesNotBlockOthers() = runBlocking {
        val bad = create()
        val good = create()
        remote.denyTaskId = bad
        val r = engine.pushPending()
        assertEquals(1, r.sent)
        assertFalse(r.retryLater)
        assertEquals(listOf(good), remote.created)
        assertEquals(1, db.outbox().observeNeedsAttention().first())
    }

    @Test fun attemptsCapMovesToNeedsAttention() = runBlocking {
        create()
        remote.failWith = IOException("x")
        repeat(SyncEngine.MAX_ATTEMPTS) { engine.pushPending() }
        assertEquals(1, db.outbox().observeNeedsAttention().first())
        assertTrue(db.outbox().pending().isEmpty())
    }

    @Test fun stuckSendingIsRecovered() = runBlocking {
        create()
        val key = db.outbox().pending().first().idempotencyKey
        db.outbox().mark(key, OutboxState.SENDING, 0, null, null)
        assertTrue(db.outbox().pending().isEmpty())
        engine.pushPending()
        assertTrue(db.outbox().pending().isEmpty())
        assertEquals(1, remote.created.size)
    }

    @Test fun remoteNewerWinsWhenNothingPending() = runBlocking {
        val id = create()
        engine.pushPending()
        val local = db.tasks().byId(id)!!
        engine.applyTasks(listOf(local.copy(status = "IN_PROGRESS", updatedAt = local.updatedAt + 10)))
        assertEquals("IN_PROGRESS", db.tasks().byId(id)!!.status)
        engine.applyTasks(listOf(local.copy(status = "SEEN", updatedAt = local.updatedAt + 5)))
        assertEquals("IN_PROGRESS", db.tasks().byId(id)!!.status)
    }

    @Test fun pendingLocalChangeIsNotOverwritten() = runBlocking {
        val id = create()
        val local = db.tasks().byId(id)!!
        engine.applyTasks(listOf(local.copy(title = "من الخادم", updatedAt = local.updatedAt + 99)))
        assertEquals("مهمة", db.tasks().byId(id)!!.title)
    }

    @Test fun unknownRemoteTaskIsInserted() = runBlocking {
        val t = TaskEntity("r1", null, "بعيدة", "", "u9", "A", "A1", "u2", "HIGH", "NEW", null, 1, 1, false)
        engine.applyTasks(listOf(t))
        assertEquals("بعيدة", db.tasks().byId("r1")!!.title)
    }
}
