package com.anjaz.app.backup

import androidx.room.withTransaction
import com.anjaz.app.data.Clock
import com.anjaz.app.data.db.AnjazDatabase
import com.anjaz.app.data.db.CampEntity
import com.anjaz.app.data.db.MessageEntity
import com.anjaz.app.data.db.RegionEntity
import com.anjaz.app.data.db.RoomEntity
import com.anjaz.app.data.db.SosEntity
import com.anjaz.app.data.db.SquareEntity
import com.anjaz.app.data.db.TaskEntity
import com.anjaz.app.data.db.TaskEventEntity
import com.anjaz.app.data.db.UserEntity
import com.anjaz.app.domain.Outcome
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class BackupFile(
    val format: Int = FORMAT,
    val schemaVersion: Int,
    val createdAt: Long,
    val regions: List<RegionEntity> = emptyList(),
    val squares: List<SquareEntity> = emptyList(),
    val camps: List<CampEntity> = emptyList(),
    val users: List<UserEntity> = emptyList(),
    val tasks: List<TaskEntity> = emptyList(),
    val taskEvents: List<TaskEventEntity> = emptyList(),
    val sos: List<SosEntity> = emptyList(),
    val rooms: List<RoomEntity> = emptyList(),
    val messages: List<MessageEntity> = emptyList(),
) {
    companion object { const val FORMAT = 1 }
}

data class RestoreSummary(val tasks: Int, val directory: Int, val messages: Int)

// نسخة JSON محلية؛ الاستعادة دمج آمن (لا تحذف ولا تستبدل الأحدث) ولا تمسّ صندوق الصادر
@Singleton
class BackupManager @Inject constructor(private val db: AnjazDatabase, private val clock: Clock) {
    private val json = Json { prettyPrint = false; ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun export(): String = json.encodeToString(
        BackupFile(
            schemaVersion = SCHEMA_VERSION, createdAt = clock.now(),
            regions = db.directory().allRegions(), squares = db.directory().allSquares(), camps = db.directory().allCamps(),
            users = db.directory().allUsers(), tasks = db.tasks().all(), taskEvents = db.taskEvents().all(),
            sos = db.sos().all(), rooms = db.chat().allRooms(), messages = db.chat().allMessages(),
        ),
    )

    suspend fun restore(text: String): Outcome<RestoreSummary> {
        val file = runCatching { json.decodeFromString<BackupFile>(text) }.getOrNull()
            ?: return Outcome.Failure("الملف ليس نسخة احتياطية صالحة")
        if (file.format != BackupFile.FORMAT) return Outcome.Failure("صيغة النسخة غير مدعومة")
        if (file.schemaVersion > SCHEMA_VERSION) return Outcome.Failure("النسخة أحدث من هذا التطبيق")
        var tasks = 0
        var dir = 0
        var msgs = 0
        db.withTransaction {
            file.regions.forEach { if (db.directory().regionById(it.id) == null) { db.directory().upsertRegion(it); dir++ } }
            file.squares.forEach { if (db.directory().squareById(it.id) == null) { db.directory().upsertSquare(it); dir++ } }
            file.camps.forEach { if (db.directory().campById(it.id) == null) { db.directory().upsertCamp(it); dir++ } }
            val knownUsers = db.directory().allUsers().map { it.id }.toSet()
            file.users.filter { it.id !in knownUsers }.forEach { db.directory().upsertUser(it); dir++ }
            file.tasks.forEach {
                val local = db.tasks().byId(it.id)
                if (local == null || it.updatedAt > local.updatedAt) { db.tasks().upsert(it); tasks++ }
            }
            file.taskEvents.forEach { db.taskEvents().insert(it) }
            file.sos.forEach { if (db.sos().byId(it.id) == null) db.sos().upsert(it) }
            file.rooms.forEach { if (db.chat().roomById(it.id) == null) db.chat().upsertRoom(it) }
            file.messages.forEach { if (db.chat().insertMessage(it) != -1L) msgs++ }
        }
        return Outcome.Ok(RestoreSummary(tasks, dir, msgs))
    }

    companion object { const val SCHEMA_VERSION = 3 }
}
