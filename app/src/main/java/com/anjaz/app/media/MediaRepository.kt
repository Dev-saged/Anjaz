package com.anjaz.app.media

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room.withTransaction
import com.anjaz.app.data.Clock
import com.anjaz.app.data.EntityOp
import com.anjaz.app.data.Outbox
import com.anjaz.app.data.db.AnjazDatabase
import com.anjaz.app.data.db.MediaState
import com.anjaz.app.data.db.MessageEntity
import com.anjaz.app.data.db.MessageState
import com.anjaz.app.domain.Outcome
import com.anjaz.app.domain.Profile
import com.anjaz.app.update.AppFolder
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** نص معاينة الرسالة في قائمة المحادثات. */
fun previewOf(m: MessageEntity): String = when (m.type) {
    "image" -> "صورة"
    "voice" -> "رسالة صوتية"
    "file" -> "ملف: " + m.mediaName.take(60)
    "location" -> "موقع"
    else -> m.text.take(200)
}

fun extensionFor(mime: String, name: String): String = when {
    mime == "image/jpeg" -> "jpg"
    mime == "audio/mp4" -> "m4a"
    name.contains('.') -> name.substringAfterLast('.').take(8).filter { it.isLetterOrDigit() }.ifEmpty { "bin" }
    else -> "bin"
}

@Singleton
class MediaRepository @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val db: AnjazDatabase,
    private val clock: Clock,
    private val transport: MediaTransport,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gate = Semaphore(2)
    private val inFlight = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    fun roomDir(roomId: String): File = File(AppFolder.root(ctx), "media/${roomId.filter { it.isLetterOrDigit() || it == '_' || it == '-' }}").apply { mkdirs() }

    private suspend fun insert(me: Profile, roomId: String, m: MessageEntity): Outcome<String> {
        if (db.chat().roomById(roomId) == null) return Outcome.Failure("المحادثة غير موجودة")
        return runCatching {
            db.withTransaction {
                db.chat().insertMessage(m)
                db.chat().touchRoom(roomId, previewOf(m), m.createdAt)
                db.outbox().insertIgnore(Outbox.op("message", "send", EntityOp(m.id, roomId), m.createdAt))
            }
            Outcome.Ok(m.id)
        }.getOrElse { Outcome.Failure("تعذّر الحفظ محلياً") }
    }

    private fun base(roomId: String, me: Profile, type: String, caption: String = "") = MessageEntity(
        UUID.randomUUID().toString(), roomId, me.uid, type, caption, clock.now(), me.uid, MessageState.LOCAL,
    )

    suspend fun sendImage(roomId: String, me: Profile, uri: Uri): Outcome<String> = withContext(Dispatchers.IO) {
        runCatching {
            val c = MediaCompressor.image(ctx, uri)
            val id = UUID.randomUUID().toString()
            val f = File(roomDir(roomId), "$id.jpg").apply { writeBytes(c.bytes) }
            insert(me, roomId, base(roomId, me, "image").copy(mediaId = id, mediaMime = "image/jpeg", mediaName = "$id.jpg", mediaSize = f.length(), width = c.width, height = c.height, localPath = f.path, mediaState = MediaState.LOCAL))
        }.getOrElse { Outcome.Failure(it.message ?: "تعذّر معالجة الصورة") }
    }

    suspend fun sendFile(roomId: String, me: Profile, uri: Uri): Outcome<String> = withContext(Dispatchers.IO) {
        runCatching {
            var name = "ملف"; var size = -1L
            ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { name = c.getString(it) ?: name }
                    c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 }?.let { size = c.getLong(it) }
                }
            }
            if (size > MediaCompressor.FILE_MAX) return@runCatching Outcome.Failure("الحد الأقصى للملف 6 ميغابايت")
            val mime = (ctx.contentResolver.getType(uri) ?: "application/octet-stream").let { if (it in ALLOWED) it else "application/octet-stream" }
            val id = UUID.randomUUID().toString()
            val f = File(roomDir(roomId), "$id.${extensionFor(mime, name)}")
            ctx.contentResolver.openInputStream(uri)?.use { i -> f.outputStream().use { o -> i.copyTo(o) } } ?: error("تعذّر قراءة الملف")
            if (f.length() > MediaCompressor.FILE_MAX) { f.delete(); return@runCatching Outcome.Failure("الحد الأقصى للملف 6 ميغابايت") }
            insert(me, roomId, base(roomId, me, "file").copy(mediaId = id, mediaMime = mime, mediaName = name.take(120), mediaSize = f.length(), localPath = f.path, mediaState = MediaState.LOCAL))
        }.getOrElse { Outcome.Failure(it.message ?: "تعذّر قراءة الملف") }
    }

    suspend fun sendVoice(roomId: String, me: Profile, file: File, durMs: Long): Outcome<String> = withContext(Dispatchers.IO) {
        if (durMs < 700 || file.length() == 0L) { file.delete(); return@withContext Outcome.Failure("التسجيل قصير جدًا") }
        val id = UUID.randomUUID().toString()
        val f = File(roomDir(roomId), "$id.m4a")
        if (!file.renameTo(f)) { file.copyTo(f, true); file.delete() }
        insert(me, roomId, base(roomId, me, "voice").copy(mediaId = id, mediaMime = "audio/mp4", mediaName = "$id.m4a", mediaSize = f.length(), durMs = durMs, localPath = f.path, mediaState = MediaState.LOCAL))
    }

    suspend fun sendLocation(roomId: String, me: Profile, lat: Double, lng: Double): Outcome<String> =
        insert(me, roomId, base(roomId, me, "location").copy(lat = lat, lng = lng))

    /** تنزيل ملف رسالة واردة إلى مجلد التطبيق ثم تأكيد الاستلام للـ Worker ليبدأ العدّ التنازلي للحذف. */
    fun fetch(messageId: String) {
        if (!inFlight.add(messageId)) return
        scope.launch {
            try {
                gate.withPermit {
                    val m = db.chat().messageById(messageId) ?: return@withPermit
                    if (m.mediaId.isEmpty() || m.mediaState == MediaState.LOCAL || m.mediaState == MediaState.EXPIRED) return@withPermit
                    db.chat().setMedia(m.id, "", MediaState.DOWNLOADING)
                    val dest = File(roomDir(m.roomId), "${m.mediaId}.${extensionFor(m.mediaMime, m.mediaName)}")
                    val part = File(dest.path + ".part")
                    when (runCatching { transport.download(m.mediaId, part) }.getOrNull()) {
                        Download.Done -> {
                            dest.delete(); part.renameTo(dest)
                            db.chat().setMedia(m.id, dest.path, MediaState.LOCAL)
                            runCatching { transport.ack(m.mediaId) }
                        }
                        Download.Gone -> { part.delete(); db.chat().setMedia(m.id, "", MediaState.EXPIRED) }
                        null -> { part.delete(); db.chat().setMedia(m.id, "", MediaState.REMOTE) }
                    }
                }
            } finally { inFlight.remove(messageId) }
        }
    }

    /** صور وصوت بحجم صغير تُنزَّل تلقائيًا عند فتح المحادثة؛ الملفات الكبيرة بالضغط. */
    suspend fun autoDownload(roomId: String, me: String) {
        db.chat().pendingDownloads(roomId, me).filter { it.type == "image" || it.type == "voice" || it.mediaSize <= 1_000_000 }.forEach { fetch(it.id) }
    }

    suspend fun saveTranscript(id: String, text: String) = db.chat().setTranscript(id, text.take(2000))

    /** مسح الملفات المحلية اليتيمة (لا رسالة تشير إليها) لتوفير المساحة. */
    suspend fun cleanOrphans(): Int = withContext(Dispatchers.IO) {
        val live = db.chat().localMedia().map { File(it.localPath).absolutePath }.toSet()
        val root = File(AppFolder.root(ctx), "media")
        var n = 0
        root.walkTopDown().filter { it.isFile }.forEach { if (it.absolutePath !in live || it.name.endsWith(".part")) { it.delete(); n++ } }
        n
    }

    companion object {
        val ALLOWED = setOf(
            "application/pdf", "application/zip", "text/plain", "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        )
    }
}
