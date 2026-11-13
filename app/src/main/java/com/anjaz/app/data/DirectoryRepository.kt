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

    private fun clean(values: Map<String, String>, key: String) = values[key]?.let(SchemaValidator::sanitize)?.takeIf { it.isNotEmpty() }

    suspend fun createRegion(actor: Profile, values: Map<String, String>): Outcome<String> {
        if (!canCreateRegion(actor)) return Outcome.Failure("ليس لديك صلاحية إضافة منطقة")
        val errors = SchemaValidator.validate(Schemas.region, values)
        if (errors.isNotEmpty()) return Outcome.Failure(errors.values.first())
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
        val region = if (actor.roleEnum == Role.CENTRAL) regionId else actor.regionId
        if (region.isNullOrEmpty()) return Outcome.Failure("اختر المنطقة")
        val errors = SchemaValidator.validate(Schemas.square, values)
        if (errors.isNotEmpty()) return Outcome.Failure(errors.values.first())
        val now = clock.now()
        val sq = SquareEntity(UUID.randomUUID().toString(), region, clean(values, "name").orEmpty(), null, clean(values, "idNumber"), clean(values, "phone"), now)
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
        )
        db.withTransaction {
            db.directory().upsertCamp(camp)
            db.outbox().insertIgnore(Outbox.op("camp", "create", EntityOp(camp.id), now))
        }
        return Outcome.Ok(camp.id)
    }
}
