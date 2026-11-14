package com.anjaz.app

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.anjaz.app.data.Clock
import com.anjaz.app.data.EntityOp
import com.anjaz.app.data.Outbox
import com.anjaz.app.data.db.AnjazDatabase
import com.anjaz.app.data.db.MediaState
import com.anjaz.app.data.db.MessageEntity
import com.anjaz.app.data.db.MessageState
import com.anjaz.app.data.db.RoomEntity
import com.anjaz.app.media.Download
import com.anjaz.app.media.MediaCompressor
import com.anjaz.app.media.MediaTransport
import com.anjaz.app.sync.Notifier
import com.anjaz.app.sync.SyncEngine
import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class FakeMedia : MediaTransport {
    val uploaded = mutableListOf<String>()
    override suspend fun upload(roomId: String, mediaId: String, file: File, mime: String) { uploaded += "$roomId/$mediaId" }
    override suspend fun download(mediaId: String, dest: File): Download = Download.Gone
    override suspend fun ack(mediaId: String) = Unit
}

private class BareRemote : com.anjaz.app.sync.RemoteStore {
    override val enabled = true
    override suspend fun createTask(task: com.anjaz.app.data.db.TaskEntity) = Unit
    override suspend fun updateTaskStatus(taskId: String, status: String, updatedAt: Long) = Unit
    override suspend fun appendEvent(event: com.anjaz.app.data.db.TaskEventEntity) = Unit
    override fun tasks(profile: com.anjaz.app.domain.Profile) = kotlinx.coroutines.flow.emptyFlow<List<com.anjaz.app.data.db.TaskEntity>>()
    override fun users(profile: com.anjaz.app.domain.Profile) = kotlinx.coroutines.flow.emptyFlow<List<com.anjaz.app.data.db.UserEntity>>()
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class MediaTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test fun fitKeepsSmallAndScalesLargeProportionally() {
        assertEquals(800 to 600, MediaCompressor.fit(800, 600))
        assertEquals(1600 to 1200, MediaCompressor.fit(4000, 3000))
        assertEquals(900 to 1600, MediaCompressor.fit(2250, 4000))
        assertEquals(1 to 1600, MediaCompressor.fit(1, 100_000))
    }

    @Test fun sampleSizeStaysAtLeastTargetResolution() {
        assertEquals(1, MediaCompressor.sampleSize(1600, 900))
        assertEquals(2, MediaCompressor.sampleSize(4000, 3000))
        assertEquals(4, MediaCompressor.sampleSize(8000, 6000))
    }

    @Test fun migrate3To4KeepsMessagesAndDefaultsMedia() = runBlocking {
        val schema = JSONObject(File("schemas/com.anjaz.app.data.db.AnjazDatabase/3.json").readText()).getJSONObject("database")
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(ctx).name("m34.db").callback(object : SupportSQLiteOpenHelper.Callback(3) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    val es = schema.getJSONArray("entities")
                    for (i in 0 until es.length()) {
                        val e = es.getJSONObject(i); val t = e.getString("tableName")
                        db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", t))
                        val idx = e.optJSONArray("indices")
                        if (idx != null) for (j in 0 until idx.length()) db.execSQL(idx.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", t))
                    }
                    db.execSQL("INSERT INTO messages (id, roomId, senderId, type, text, createdAt, readBy, state) VALUES ('m1','r1','u1','text','مرحبا',5,'u1','SENT')")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, o: Int, n: Int) = Unit
            }).build(),
        )
        helper.writableDatabase.close(); helper.close()
        val db = Room.databaseBuilder(ctx, AnjazDatabase::class.java, "m34.db").addMigrations(*AnjazDatabase.MIGRATIONS).build()
        val m = db.chat().messageById("m1")!!
        assertEquals("مرحبا", m.text)
        assertEquals(MediaState.NONE, m.mediaState)
        assertEquals("", m.mediaId)
        assertEquals(null, m.lat)
        db.close()
    }

    @Test fun pushUploadsMediaBeforeMessage() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ctx, AnjazDatabase::class.java).build()
        val tr = FakeMedia()
        val engine = SyncEngine(db, BareRemote(), Notifier { _, _ -> }, Clock { 10 }, tr)
        val f = File.createTempFile("img", ".jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val m = MessageEntity("m1", "r1", "u1", "image", "", 10, "u1", MessageState.LOCAL, mediaId = "x1", mediaMime = "image/jpeg", mediaName = "x1.jpg", mediaSize = 3, localPath = f.path, mediaState = MediaState.LOCAL)
        db.chat().upsertRoom(RoomEntity("r1", "DIRECT", "", "u1,u2", "", 0))
        db.chat().insertMessage(m)
        db.outbox().insertIgnore(Outbox.op("message", "send", EntityOp("m1", "r1"), 10))
        val r = engine.pushPending()
        assertEquals(1, r.sent)
        assertEquals(listOf("r1/x1"), tr.uploaded)
        db.close()
    }

    @Test fun incomingMessageKeepsLocalFileFields() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ctx, AnjazDatabase::class.java).build()
        val engine = SyncEngine(db, BareRemote(), Notifier { _, _ -> }, Clock { 10 }, FakeMedia())
        val local = MessageEntity("m1", "r1", "u2", "voice", "", 10, "u2", MessageState.SENT, mediaId = "a1", mediaMime = "audio/mp4", localPath = "/x/a1.m4a", mediaState = MediaState.LOCAL, transcript = "نص")
        db.chat().upsertRoom(RoomEntity("r1", "DIRECT", "", "u1,u2", "", 0))
        db.chat().insertMessage(local)
        engine.applyMessages(listOf(local.copy(localPath = "", mediaState = MediaState.REMOTE, transcript = "", readBy = "u2,u1")))
        val after = db.chat().messageById("m1")!!
        assertEquals("/x/a1.m4a", after.localPath)
        assertEquals(MediaState.LOCAL, after.mediaState)
        assertEquals("نص", after.transcript)
        assertTrue(after.readBy.contains("u1"))
        db.close()
    }

    @Test fun pulledTaskEventsAreIdempotent() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ctx, AnjazDatabase::class.java).build()
        val engine = SyncEngine(db, BareRemote(), Notifier { _, _ -> }, Clock { 10 }, FakeMedia())
        val e = com.anjaz.app.data.db.TaskEventEntity("e1", "t1", "NEW", "IN_PROGRESS", "u2", null, 5)
        engine.applyEvents(listOf(e)); engine.applyEvents(listOf(e, e.copy(id = "e2", at = 6)))
        assertEquals(2, db.taskEvents().countAll())
        db.close()
    }
}
