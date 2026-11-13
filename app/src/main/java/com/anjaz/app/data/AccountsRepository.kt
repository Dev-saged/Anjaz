package com.anjaz.app.data

import com.anjaz.app.data.db.AnjazDatabase
import com.anjaz.app.data.db.RegionEntity
import com.anjaz.app.data.db.SquareEntity
import com.anjaz.app.data.db.UserEntity
import com.anjaz.app.schema.ModuleSchema
import com.anjaz.app.domain.Outcome
import com.anjaz.app.domain.Profile
import com.anjaz.app.domain.Role
import com.anjaz.app.schema.Choice
import com.anjaz.app.schema.Schemas
import com.anjaz.app.schema.SchemaValidator
import com.anjaz.app.sync.ApiClient
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

@Singleton
class AccountsRepository @Inject constructor(private val db: AnjazDatabase, private val api: ApiClient) {
    fun users(me: String): Flow<List<UserEntity>> = db.contacts().observeAll(me)

    fun creatableRoles(p: Profile): List<Choice> =
        Role.entries.filter { p.roleEnum.canCreate(it) }.map { Choice(it.name, it.label) }

    fun schema(p: Profile, regions: List<RegionEntity> = emptyList(), squares: List<SquareEntity> = emptyList()) = Schemas.account(
        creatableRoles(p),
        regions = if (p.roleEnum == Role.CENTRAL) regions.map { Choice(it.id, it.name) } else null,
        squares = if (p.roleEnum.rank >= Role.REGION_ADMIN.rank) squares.map { Choice(it.id, it.name) } else null,
    )

    fun regions(): Flow<List<RegionEntity>> = db.directory().observeRegions()
    fun squares(): Flow<List<SquareEntity>> = db.directory().observeSquares()

    suspend fun create(actor: Profile, values: Map<String, String>, schema: ModuleSchema = schema(actor)): Outcome<String> {
        val errors = SchemaValidator.validate(schema, values)
        if (errors.isNotEmpty()) return Outcome.Failure(errors.values.first())
        val role = Role.entries.firstOrNull { it.name == values["role"] } ?: return Outcome.Failure("الدور غير صالح")
        val square = values["squareId"]?.trim().takeUnless { it.isNullOrEmpty() } ?: actor.squareId
        val region = values["regionId"]?.trim().takeUnless { it.isNullOrEmpty() }
            ?: values["squareId"]?.let { db.directory().squareById(it)?.regionId } ?: actor.regionId
        val body = buildJsonObject {
            put("username", values.getValue("username").trim().lowercase())
            put("displayName", SchemaValidator.sanitize(values.getValue("displayName")))
            put("password", values.getValue("password"))
            put("role", role.name)
            put("regionId", region?.let(::JsonPrimitive) ?: kotlinx.serialization.json.JsonNull)
            put("squareId", square?.let(::JsonPrimitive) ?: kotlinx.serialization.json.JsonNull)
        }.toString()
        return call("/v1/users", body) { "ok" }
    }

    suspend fun resetPassword(uid: String): Outcome<String> =
        call("/v1/users/$uid/password", "{}") { it["password"]?.jsonPrimitive?.contentOrNull.orEmpty() }

    suspend fun setActive(uid: String, active: Boolean): Outcome<String> =
        call("/v1/users/$uid/active", """{"active":$active}""") { "ok" }

    private suspend fun call(path: String, body: String, parse: (JsonObject) -> String): Outcome<String> {
        if (!api.configured) return Outcome.Failure("الخادم غير مُعدّ أو لا يوجد اتصال")
        val res = try { api.post(path, body) } catch (e: Exception) { null } ?: return Outcome.Failure("تعذّر الاتصال بالخادم")
        val obj = runCatching { Json.parseToJsonElement(res.second).jsonObject }.getOrNull() ?: JsonObject(emptyMap())
        return if (res.first in 200..299) Outcome.Ok(parse(obj))
        else Outcome.Failure(obj["error"]?.jsonPrimitive?.contentOrNull ?: "تعذّر تنفيذ العملية (${res.first})")
    }
}
