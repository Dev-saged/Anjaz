package com.anjaz.app.data

import androidx.room.withTransaction
import com.anjaz.app.data.db.AnjazDatabase
import com.anjaz.app.data.db.CampEntity
import com.anjaz.app.data.db.RegionEntity
import com.anjaz.app.data.db.SquareEntity
import com.anjaz.app.domain.Outcome
import com.anjaz.app.domain.Profile
import com.anjaz.app.domain.Role
import com.anjaz.app.schema.SchemaValidator
import com.anjaz.app.schema.Schemas
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class DirectoryRepository @Inject constructor(private val db: AnjazDatabase, private val clock: Clock) {
    fun regions(): Flow<List<RegionEntity>> = db.directory().observeRegions()
    fun squares(): Flow<List<SquareEntity>> = db.directory().observeSquares()
    fun camps(): Flow<List<CampEntity>> = db.directory().observeCamps()

    fun canCreateRegion(p: Profile) = p.roleEnum == Role.CENTRAL
    fun canCreateSquare(p: Profile) = p.roleEnum == Role.CENTRAL || p.roleEnum == Role.REGION_MANAGER
    fun canCreateCamp(p: Profile) = p.roleEnum.rank >= Role.SQUARE_MANAGER.rank

    companion object {
        const val NO_PARENT = "__none__"

        fun squaresInScope(p: Profile, all: List<SquareEntity>): List<SquareEntity> = all.filter { it.deletedAt == null }.filter {
            when (p.roleEnum) {
                Role.CENTRAL -> true
                Role.REGION_MANAGER, Role.REGION_ADMIN -> it.regionId == p.regionId
                else -> it.id == p.squareId
            }
        }

        fun campsInScope(p: Profile, all: List<CampEntity>): List<CampEntity> = all.filter { it.deletedAt == null }.filter {
            when (p.roleEnum) {
                Role.CENTRAL -> true
                Role.REGION_MANAGER, Role.REGION_ADMIN -> it.regionId == p.regionId
                else -> it.squareId == p.squareId
            }
        }
    }

    private fun families(values: Map<String, String>): Int? =
        com.anjaz.app.domain.Username.cleanSecret(values["families"].orEmpty()).toIntOrNull()?.coerceIn(0, 100_000)

    private fun clean(values: Map<String, String>, key: String) = values[key]?.let(SchemaValidator::sanitize)?.takeIf { it.isNotEmpty() }

    suspend fun createRegion(actor: Profile, values: Map<String, String>): Outcome<String> {
        if (!canCreateRegion(actor)) return Outcome.Failure("ليس لديك صلاحية إضافة منطقة")
        val errors = SchemaValidator.validate(Schemas.region, values)
        if (errors.isNotEmpty()) return Outcome.Failure(errors.values.first())
        val name = clean(values, "name").orEmpty()
        if (db.directory().allRegions().any { it.deletedAt == null && it.name.trim().equals(name, true) }) return Outcome.Failure("توجد منطقة باسم «$name» مسبقاً")
        val now = clock.now()
        val r = RegionEntity(UUID.randomUUID().toString(), clean(values, "name").orEmpty(), null, clean(values, "phone"), true, now)
        db.withTransaction {
            db.directory().upsertRegion(r)
            db.outbox().insertIgnore(Outbox.op("region", "create", EntityOp(r.id), now))
        }
        return Outcome.Ok(r.id)
    }

    suspend fun createSquare(actor: Profile, regionId: String?, values: Map<String, String>): Outcome<String> {
        if (!canCreateSquare(actor)) return Outcome.Failure("ليس لديك صلاحية إضافة مربع")
        val parent = values["parentId"]?.takeIf { it.isNotBlank() && it != NO_PARENT }?.let { pid ->
            db.directory().squareById(pid)?.takeIf { it.deletedAt == null && it.parentId == null } ?: return Outcome.Failure("المربع الرئيسي غير صالح")
        }
        val region = parent?.regionId ?: if (actor.roleEnum == Role.CENTRAL) regionId else actor.regionId
        if (region.isNullOrEmpty()) return Outcome.Failure("اختر المنطقة")
        if (parent != null && actor.roleEnum != Role.CENTRAL && parent.regionId != actor.regionId) return Outcome.Failure("المربع الرئيسي خارج منطقتك")
        val errors = SchemaValidator.validate(Schemas.square, values)
        if (errors.isNotEmpty()) return Outcome.Failure(errors.values.first())
        val sqName = clean(values, "name").orEmpty()
        if (db.directory().allSquares().any { it.deletedAt == null && it.regionId == region && it.name.trim().equals(sqName, true) }) return Outcome.Failure("يوجد مربع باسم «$sqName» في هذه المنطقة")
        val now = clock.now()
        val sq = SquareEntity(UUID.randomUUID().toString(), region, clean(values, "name").orEmpty(), null, clean(values, "idNumber"), clean(values, "phone"), now, clean(values, "managerName"), parentId = parent?.id)
        db.withTransaction {
            db.directory().upsertSquare(sq)
            db.outbox().insertIgnore(Outbox.op("square", "create", EntityOp(sq.id), now))
        }
        return Outcome.Ok(sq.id)
    }

    suspend fun createCamp(actor: Profile, squareId: String?, values: Map<String, String>): Outcome<String> {
        if (!canCreateCamp(actor)) return Outcome.Failure("ليس لديك صلاحية إضافة مخيم")
        val square = (if (actor.roleEnum == Role.SQUARE_MANAGER) actor.squareId else squareId)?.let { db.directory().squareById(it) }
            ?: return Outcome.Failure("اختر المربع")
        if (actor.roleEnum != Role.CENTRAL && square.regionId != actor.regionId) return Outcome.Failure("المربع خارج نطاقك")
        val errors = SchemaValidator.validate(Schemas.camp, values)
        if (errors.isNotEmpty()) return Outcome.Failure(errors.values.first())
        val now = clock.now()
        val camp = CampEntity(
            UUID.randomUUID().toString(), square.regionId, square.id, clean(values, "name").orEmpty(),
            clean(values, "managerName"), clean(values, "idNumber"), clean(values, "phone"), now,
            families = families(values),
        )
        db.withTransaction {
            db.directory().upsertCamp(camp)
            db.outbox().insertIgnore(Outbox.op("camp", "create", EntityOp(camp.id), now))
        }
        return Outcome.Ok(camp.id)
    }

    suspend fun squareById(id: String) = db.directory().squareById(id)

    suspend fun usersOfSquare(id: String) = db.directory().allUsers().filter { it.squareId == id && !it.deleted }

    suspend fun updateRegion(actor: Profile, id: String, values: Map<String, String>): Outcome<Unit> {
        if (!canCreateRegion(actor)) return Outcome.Failure("تعديل المناطق للإدارة المركزية فقط")
        val cur = db.directory().regionById(id) ?: return Outcome.Failure("المنطقة غير موجودة")
        val errors = SchemaValidator.validate(Schemas.region, values)
        if (errors.isNotEmpty()) return Outcome.Failure(errors.values.first())
        val now = clock.now()
        db.withTransaction {
            db.directory().upsertRegion(cur.copy(name = clean(values, "name").orEmpty(), phone = clean(values, "phone"), updatedAt = now))
            db.outbox().insertIgnore(Outbox.op("region", "update", EntityOp(id), now))
        }
        return Outcome.Ok(Unit)
    }

    /** المنطقة تُحذف فارغة فقط: لا مربعات فيها ولا حسابات مرتبطة بها. */
    suspend fun trashRegion(actor: Profile, id: String): Outcome<Unit> {
        if (!canCreateRegion(actor)) return Outcome.Failure("حذف المناطق للإدارة المركزية فقط")
        val cur = db.directory().regionById(id) ?: return Outcome.Failure("المنطقة غير موجودة")
        val squares = db.directory().allSquares().count { it.regionId == id && it.deletedAt == null }
        if (squares > 0) return Outcome.Failure("في هذه المنطقة ${squares} مربع؛ انقلها لمنطقة أخرى أولاً ثم احذف المنطقة")
        val users = db.directory().allUsers().count { it.regionId == id && !it.deleted }
        if (users > 0) return Outcome.Failure("مرتبط بهذه المنطقة ${users} حساب؛ غيّر منطقتهم من شاشة الحسابات أولاً")
        val now = clock.now()
        db.withTransaction {
            db.directory().upsertRegion(cur.copy(deletedAt = now, updatedAt = now))
            db.outbox().insertIgnore(Outbox.op("region", "delete", EntityOp(id), now))
        }
        return Outcome.Ok(Unit)
    }

    suspend fun restoreRegion(id: String): Outcome<Unit> {
        val cur = db.directory().regionById(id) ?: return Outcome.Failure("المنطقة غير موجودة")
        val now = clock.now()
        db.withTransaction {
            db.directory().upsertRegion(cur.copy(deletedAt = null, updatedAt = now))
            db.outbox().insertIgnore(Outbox.op("region", "restore", EntityOp(id), now))
        }
        return Outcome.Ok(Unit)
    }

    suspend fun purgeRegion(id: String): Outcome<Unit> {
        val cur = db.directory().regionById(id) ?: return Outcome.Ok(Unit)
        val now = clock.now()
        db.withTransaction {
            db.directory().upsertRegion(cur.copy(deletedAt = Trash.EXPIRED, updatedAt = now))
            db.outbox().insertIgnore(Outbox.op("region", "purge", EntityOp(id), now))
        }
        return Outcome.Ok(Unit)
    }

    /** منطقة أُنشئت بالخطأ وهي في الحقيقة مربع: يُنشأ مربع باسمها في المنطقة المختارة وتُنقل هي إلى السلة. */
    /**
     * منطقة هي في الحقيقة مربع: تصبح مربعاً في المنطقة المختارة. إن كان فيها مربعات تصبح مربعاتٍ فرعية
     * داخله (مع مخيماتها). [intoSquare] لدمجها تحت مربع موجود بدل إنشاء مربع جديد.
     */
    suspend fun convertRegionToSquare(actor: Profile, regionId: String, targetRegion: String?, values: Map<String, String>, intoSquare: String? = null): Outcome<String> {
        if (actor.roleEnum != Role.CENTRAL) return Outcome.Failure("التحويل للإدارة المركزية فقط")
        val inside = db.directory().allSquares().filter { it.regionId == regionId && it.deletedAt == null }
        val parent: SquareEntity = if (intoSquare != null) {
            val p = db.directory().squareById(intoSquare)?.takeIf { it.deletedAt == null } ?: return Outcome.Failure("المربع المختار غير موجود")
            if (p.regionId == regionId) return Outcome.Failure("اختر مربعاً خارج هذه المنطقة")
            if (p.parentId != null) return Outcome.Failure("اختر مربعاً رئيسياً لا فرعياً")
            p
        } else {
            if (targetRegion == null || targetRegion == regionId) return Outcome.Failure("اختر المنطقة التي سيتبعها المربع")
            when (val r = createSquare(actor, targetRegion, values)) {
                is Outcome.Ok -> db.directory().squareById(r.value) ?: return Outcome.Failure("تعذّر إنشاء المربع")
                is Outcome.Failure -> return r
            }
        }
        val now = clock.now()
        db.withTransaction {
            inside.filter { it.id != parent.id }.forEach { sub ->
                db.directory().upsertSquare(sub.copy(regionId = parent.regionId, parentId = parent.id, updatedAt = now))
                db.outbox().insertIgnore(Outbox.op("square", "update", EntityOp(sub.id), now))
                // مربع كان رئيسياً وله فرعية: تنتقل فرعياته إلى الرئيسي الجديد (مستوى واحد فقط)
                db.directory().allSquares().filter { it.parentId == sub.id && it.deletedAt == null }.forEach { kid ->
                    db.directory().upsertSquare(kid.copy(regionId = parent.regionId, parentId = parent.id, updatedAt = now))
                    db.outbox().insertIgnore(Outbox.op("square", "update", EntityOp(kid.id), now))
                }
            }
            db.directory().allCamps().filter { it.regionId == regionId && it.deletedAt == null }.forEach {
                db.directory().upsertCamp(it.copy(regionId = parent.regionId, updatedAt = now))
                db.outbox().insertIgnore(Outbox.op("camp", "update", EntityOp(it.id), now))
            }
        }
        val users = db.directory().allUsers().count { it.regionId == regionId && !it.deleted }
        if (users == 0) trashRegion(actor, regionId)
        return Outcome.Ok(parent.id)
    }

    suspend fun updateSquare(actor: Profile, id: String, values: Map<String, String>): Outcome<Unit> {
        if (!canCreateSquare(actor)) return Outcome.Failure("ليس لديك صلاحية تعديل المربع")
        val cur = db.directory().squareById(id) ?: return Outcome.Failure("المربع غير موجود")
        if (squaresInScope(actor, listOf(cur)).isEmpty()) return Outcome.Failure("المربع خارج نطاقك")
        val errors = SchemaValidator.validate(Schemas.square, values)
        if (errors.isNotEmpty()) return Outcome.Failure(errors.values.first())
        val now = clock.now()
        val all = db.directory().allSquares()
        // التبعية لمربع رئيسي: المنطقة تتبع الرئيسي، ومستوى واحد فقط
        val parentKey = values["parentId"]
        val parent = parentKey?.takeIf { it.isNotBlank() && it != NO_PARENT }?.let { pid -> all.firstOrNull { it.id == pid && it.deletedAt == null } ?: return Outcome.Failure("المربع الرئيسي غير موجود") }
        if (parent != null) {
            if (parent.id == id) return Outcome.Failure("لا يتبع المربع نفسه")
            if (parent.parentId != null) return Outcome.Failure("المربع المختار فرعي؛ اختر مربعاً رئيسياً")
            if (all.any { it.parentId == id && it.deletedAt == null }) return Outcome.Failure("لهذا المربع مربعات فرعية فلا يصير فرعياً")
        }
        val newParent = when { parentKey == null -> cur.parentId; parent == null -> null; else -> parent.id }
        val target = parent?.regionId ?: values["regionId"]?.takeIf { it.isNotBlank() } ?: cur.regionId
        if (target != cur.regionId) {
            if (actor.roleEnum != Role.CENTRAL) return Outcome.Failure("نقل المربع بين المناطق للإدارة المركزية فقط")
            if (db.directory().regionById(target)?.deletedAt != null) return Outcome.Failure("المنطقة المختارة غير متاحة")
        }
        val next = cur.copy(
            name = clean(values, "name").orEmpty(), managerName = clean(values, "managerName"),
            idNumber = clean(values, "idNumber"), phone = clean(values, "phone"), updatedAt = now, regionId = target, parentId = newParent,
        )
        db.withTransaction {
            db.directory().upsertSquare(next)
            db.outbox().insertIgnore(Outbox.op("square", "update", EntityOp(id), now))
            // المخيمات تتبع مربعها إلى المنطقة الجديدة
            if (target != cur.regionId) {
                val family = setOf(id) + all.filter { it.parentId == id && it.deletedAt == null }.map { it.id }
                all.filter { it.parentId == id && it.deletedAt == null }.forEach {
                    db.directory().upsertSquare(it.copy(regionId = target, updatedAt = now))
                    db.outbox().insertIgnore(Outbox.op("square", "update", EntityOp(it.id), now))
                }
                db.directory().allCamps().filter { it.squareId in family }.forEach {
                    db.directory().upsertCamp(it.copy(regionId = target, updatedAt = now))
                    db.outbox().insertIgnore(Outbox.op("camp", "update", EntityOp(it.id), now))
                }
            }
        }
        return Outcome.Ok(Unit)
    }

    suspend fun updateCamp(actor: Profile, id: String, values: Map<String, String>): Outcome<Unit> {
        if (!canCreateCamp(actor)) return Outcome.Failure("ليس لديك صلاحية تعديل المخيم")
        val cur = db.directory().campById(id) ?: return Outcome.Failure("المخيم غير موجود")
        if (campsInScope(actor, listOf(cur)).isEmpty()) return Outcome.Failure("المخيم خارج نطاقك")
        val errors = SchemaValidator.validate(Schemas.camp, values)
        if (errors.isNotEmpty()) return Outcome.Failure(errors.values.first())
        val now = clock.now()
        val newSquare = values["squareId"]?.takeIf { it.isNotBlank() && it != cur.squareId }?.let { db.directory().squareById(it) }
        if (newSquare != null && squaresInScope(actor, listOf(newSquare)).isEmpty()) return Outcome.Failure("المربع المختار خارج نطاقك")
        if (newSquare != null && actor.roleEnum != Role.CENTRAL && newSquare.regionId != cur.regionId) return Outcome.Failure("نقل المخيم لمنطقة أخرى للإدارة المركزية فقط")
        val next = cur.copy(
            name = clean(values, "name").orEmpty(), managerName = clean(values, "managerName"),
            idNumber = clean(values, "idNumber"), phone = clean(values, "phone"), updatedAt = now,
            families = families(values),
            squareId = newSquare?.id ?: cur.squareId, regionId = newSquare?.regionId ?: cur.regionId,
        )
        db.withTransaction {
            db.directory().upsertCamp(next)
            db.outbox().insertIgnore(Outbox.op("camp", "update", EntityOp(id), now))
        }
        return Outcome.Ok(Unit)
    }

    /** حذف ناعم: المربع ومخيماته يدخلون السلة معاً بالطابع نفسه ليعودوا معاً. */
    suspend fun trashSquare(actor: Profile, id: String): Outcome<Unit> {
        if (!canCreateSquare(actor)) return Outcome.Failure("ليس لديك صلاحية حذف المربع")
        val sq = db.directory().squareById(id) ?: return Outcome.Failure("المربع غير موجود")
        if (squaresInScope(actor, listOf(sq)).isEmpty()) return Outcome.Failure("المربع خارج نطاقك")
        val now = clock.now()
        db.withTransaction {
            val family = listOf(sq) + db.directory().allSquares().filter { it.parentId == id && it.deletedAt == null }
            val ids = family.map { it.id }.toSet()
            db.directory().allCamps().filter { it.squareId in ids && it.deletedAt == null }.forEach {
                db.directory().upsertCamp(it.copy(deletedAt = now, updatedAt = now))
                db.outbox().insertIgnore(Outbox.op("camp", "delete", EntityOp(it.id), now))
            }
            family.forEach {
                db.directory().upsertSquare(it.copy(deletedAt = now, updatedAt = now))
                db.outbox().insertIgnore(Outbox.op("square", "delete", EntityOp(it.id), now))
            }
        }
        return Outcome.Ok(Unit)
    }

    suspend fun trashCamp(actor: Profile, id: String): Outcome<Unit> {
        if (!canCreateCamp(actor)) return Outcome.Failure("ليس لديك صلاحية حذف المخيم")
        val camp = db.directory().campById(id) ?: return Outcome.Failure("المخيم غير موجود")
        if (campsInScope(actor, listOf(camp)).isEmpty()) return Outcome.Failure("المخيم خارج نطاقك")
        val now = clock.now()
        db.withTransaction {
            db.directory().upsertCamp(camp.copy(deletedAt = now, updatedAt = now))
            db.outbox().insertIgnore(Outbox.op("camp", "delete", EntityOp(id), now))
        }
        return Outcome.Ok(Unit)
    }

    suspend fun restoreSquare(id: String): Outcome<Unit> {
        val sq = db.directory().squareById(id) ?: return Outcome.Failure("المربع غير موجود")
        val stamp = sq.deletedAt ?: return Outcome.Ok(Unit)
        val now = clock.now()
        db.withTransaction {
            db.directory().upsertSquare(sq.copy(deletedAt = null, updatedAt = now))
            db.outbox().insertIgnore(Outbox.op("square", "restore", EntityOp(id), now))
            val kids = db.directory().allSquares().filter { it.parentId == id && it.deletedAt == stamp }
            kids.forEach {
                db.directory().upsertSquare(it.copy(deletedAt = null, updatedAt = now))
                db.outbox().insertIgnore(Outbox.op("square", "restore", EntityOp(it.id), now))
            }
            val ids = setOf(id) + kids.map { it.id }
            db.directory().allCamps().filter { it.squareId in ids && it.deletedAt == stamp }.forEach {
                db.directory().upsertCamp(it.copy(deletedAt = null, updatedAt = now))
                db.outbox().insertIgnore(Outbox.op("camp", "restore", EntityOp(it.id), now))
            }
        }
        return Outcome.Ok(Unit)
    }

    suspend fun restoreCamp(id: String): Outcome<Unit> {
        val camp = db.directory().campById(id) ?: return Outcome.Failure("المخيم غير موجود")
        val parent = db.directory().squareById(camp.squareId)
        if (parent?.deletedAt != null) return Outcome.Failure("استعد المربع أولاً")
        val now = clock.now()
        db.withTransaction {
            db.directory().upsertCamp(camp.copy(deletedAt = null, updatedAt = now))
            db.outbox().insertIgnore(Outbox.op("camp", "restore", EntityOp(id), now))
        }
        return Outcome.Ok(Unit)
    }

    suspend fun purgeSquare(id: String): Outcome<Unit> {
        val sq = db.directory().squareById(id) ?: return Outcome.Ok(Unit)
        val now = clock.now()
        db.withTransaction {
            db.directory().allCamps().filter { it.squareId == id && it.deletedAt != null }.forEach {
                db.directory().upsertCamp(it.copy(deletedAt = Trash.EXPIRED, updatedAt = now))
                db.outbox().insertIgnore(Outbox.op("camp", "purge", EntityOp(it.id), now))
            }
            db.directory().upsertSquare(sq.copy(deletedAt = Trash.EXPIRED, updatedAt = now))
            db.outbox().insertIgnore(Outbox.op("square", "purge", EntityOp(id), now))
        }
        return Outcome.Ok(Unit)
    }

    suspend fun purgeCamp(id: String): Outcome<Unit> {
        val camp = db.directory().campById(id) ?: return Outcome.Ok(Unit)
        val now = clock.now()
        db.withTransaction {
            db.directory().upsertCamp(camp.copy(deletedAt = Trash.EXPIRED, updatedAt = now))
            db.outbox().insertIgnore(Outbox.op("camp", "purge", EntityOp(id), now))
        }
        return Outcome.Ok(Unit)
    }
}
