package com.anjaz.app.core

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import com.anjaz.app.BrandConfig
import com.anjaz.app.data.Clock
import com.anjaz.app.data.TaskRepository
import com.anjaz.app.data.db.AnjazDatabase
import com.anjaz.app.data.db.OutboxEntity
import com.anjaz.app.data.db.OutboxState
import com.anjaz.app.domain.Outcome
import com.anjaz.app.domain.Role
import com.anjaz.app.domain.TaskStatus
import com.anjaz.app.schema.SchemaValidator
import com.anjaz.app.schema.Schemas

data class Check(val name: String, val ok: Boolean, val detail: String = "")

object SelfTest {
    suspend fun run(context: Context): List<Check> {
        val out = mutableListOf<Check>()
        fun check(name: String, block: () -> Pair<Boolean, String>) {
            out += runCatching { block() }.fold({ Check(name, it.first, it.second) }, { Check(name, false, it.message.orEmpty()) })
        }

        check("صلاحيات الأدوار") {
            val ok = Role.CENTRAL.canCreate(Role.REGION_MANAGER) &&
                Role.REGION_MANAGER.canCreate(Role.SQUARE_MANAGER) &&
                !Role.SQUARE_MANAGER.canCreate(Role.REGION_ADMIN) &&
                !Role.FIELD_STAFF.canCreate(Role.FIELD_STAFF)
            ok to ""
        }
        check("آلة حالات المهمة") {
            val ok = !TaskStatus.NEW.canMoveTo(TaskStatus.APPROVED) &&
                TaskStatus.AWAITING_REVIEW.canMoveTo(TaskStatus.APPROVED) &&
                !TaskStatus.APPROVED.canMoveTo(TaskStatus.IN_PROGRESS) &&
                TaskStatus.IN_PROGRESS.canMoveTo(TaskStatus.CANCELLED)
            ok to ""
        }
        check("التحقق من المدخلات") {
            val bad = SchemaValidator.validate(Schemas.task, mapOf("title" to "  ", "priority" to "X", "deadline" to "today"))
            val phone = SchemaValidator.validate(Schemas.square, mapOf("name" to "مربع", "phone" to "abc"))
            val clean = SchemaValidator.sanitize("مهمة\u0007   جديدة")
            (bad.containsKey("title") && bad.containsKey("priority") && phone.containsKey("phone") && clean == "مهمة جديدة") to ""
        }
        check("صيغ اسم المستخدم") {
            val ok = com.anjaz.app.domain.Username.isValid("sajed") && com.anjaz.app.domain.Username.isValid("sajed@anjaz.ps") &&
                !com.anjaz.app.domain.Username.isValid("a b") && !com.anjaz.app.domain.Username.isValid("x@y") &&
                com.anjaz.app.domain.Username.emailFor("sajed@anjaz.ps", "d.local") == "sajed@anjaz.ps"
            ok to ""
        }
        check("هوية الألوان (كحلي #102E52)") {
            val hex = BrandConfig.Palette.navy.light.toColor().toHex()
            (hex.equals("#102e52", true)) to hex
        }

        val db = Room.inMemoryDatabaseBuilder(context, AnjazDatabase::class.java).build()
        try {
            val clock = Clock { 1_760_000_000_000L }
            val repo = TaskRepository(db, clock)
            val id = (repo.create(mapOf("title" to "مهمة اختبار", "description" to "", "priority" to "HIGH", "deadline" to "today"), "t") as? Outcome.Ok)?.value

            out += Check("إنشاء مهمة محلياً", id != null && db.tasks().byId(id) != null)
            out += Check("الصندوق الصادر يستلم العملية", db.outbox().countAll() == 1)

            val moved = id?.let { repo.transition(it, TaskStatus.IN_PROGRESS, "t") }
            out += Check("انتقال حالة مسموح", moved is Outcome.Ok && db.tasks().byId(id!!)?.status == TaskStatus.IN_PROGRESS.name)
            val blocked = id?.let { repo.transition(it, TaskStatus.APPROVED, "t") }
            out += Check("انتقال غير مسموح يُرفض", blocked is Outcome.Failure && db.tasks().byId(id!!)?.status == TaskStatus.IN_PROGRESS.name)
            val cur = db.openHelper.readableDatabase.version
            out += Check("إصدار قاعدة البيانات v$cur", cur >= 4)
            out += Check("سجل أحداث المهمة", db.taskEvents().countAll() == 2)

            val op = OutboxEntity("key-1", "task", "create", "{}", OutboxState.LOCAL, 0, null, 1L, null)
            val first = db.outbox().insertIgnore(op)
            val second = db.outbox().insertIgnore(op)
            out += Check("منع تكرار العملية (idempotency)", first != -1L && second == -1L)

            val before = db.tasks().countAll()
            runCatching {
                db.withTransaction {
                    repo.create(mapOf("title" to "لن تُحفظ", "description" to "", "priority" to "NORMAL", "deadline" to "none"), "t")
                    error("فشل متعمّد")
                }
            }
            out += Check("التراجع عن المعاملة عند الفشل", db.tasks().countAll() == before)
        } catch (e: Exception) {
            out += Check("قاعدة البيانات", false, e.message.orEmpty())
        } finally {
            db.close()
        }
        return out
    }
}
