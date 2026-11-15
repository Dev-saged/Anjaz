package com.anjaz.app.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anjaz.app.cloud.AuthRepository
import com.anjaz.app.data.AccountsRepository
import com.anjaz.app.data.db.UserEntity
import com.anjaz.app.domain.Outcome
import com.anjaz.app.domain.Profile
import com.anjaz.app.schema.ModuleSchema
import com.anjaz.app.schema.SchemaValidator
import com.anjaz.app.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AccountsUi(
    val formOpen: Boolean = false,
    val values: Map<String, String> = emptyMap(),
    val errors: Map<String, String> = emptyMap(),
    val busy: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
    val revealedPassword: Pair<String, String>? = null,
    val edit: UserEntity? = null,
    val deleting: UserEntity? = null,
)

@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val repo: AccountsRepository,
    auth: AuthRepository,
    private val scheduler: SyncScheduler,
    private val directory: com.anjaz.app.data.DirectoryRepository,
) : ViewModel() {
    private val profile: StateFlow<Profile?> = auth.session.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val ui = MutableStateFlow(AccountsUi())

    val users: StateFlow<List<UserEntity>> = profile.filterNotNull().flatMapLatest { repo.users(it.uid) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val schema: StateFlow<ModuleSchema?> = combine(profile, repo.regions(), repo.squares()) { p, r, s -> p?.let { repo.schema(it, r, s) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun openForm() = ui.update { AccountsUi(formOpen = true, values = mapOf("role" to repo.creatableRoles(profile.value ?: return).firstOrNull()?.key.orEmpty())) }
    fun closeForm() = ui.update { it.copy(formOpen = false, errors = emptyMap(), busy = false) }
    fun change(key: String, v: String) = ui.update { it.copy(values = it.values + (key to v), errors = it.errors - key) }
    fun dismissMessage() = ui.update { it.copy(message = null) }
    fun dismissPassword() = ui.update { it.copy(revealedPassword = null) }

    fun generatePassword() = change("password", SchemaValidator.randomPassword())

    fun submit() {
        val me = profile.value ?: return
        val cur = ui.value
        val sch = schema.value ?: return
        val domain = com.anjaz.app.BrandConfig.ACCOUNT_DOMAIN
        val values = cur.values.toMutableMap()
        values["password"] = com.anjaz.app.domain.Username.cleanSecret(values["password"].orEmpty())
        val errors = SchemaValidator.validate(sch, values).toMutableMap()
        com.anjaz.app.domain.Username.problem(values["username"].orEmpty())?.let { errors["username"] = it }
        val user = com.anjaz.app.domain.Username.forAccount(values["username"].orEmpty(), domain)
        val pw = values["password"].orEmpty()
        if (pw.isEmpty()) errors["password"] = "اكتب كلمة مرور (أي طول)"
        val role = values["role"].orEmpty()
        val regionPicked = !values["regionId"].isNullOrBlank()
        val squarePicked = !values["squareId"].isNullOrBlank()
        val hasRegionField = sch.fields.any { it.key == "regionId" }
        val hasSquareField = sch.fields.any { it.key == "squareId" }
        when (role) {
            "REGION_MANAGER", "REGION_ADMIN" -> if (hasRegionField && !regionPicked) errors["regionId"] = "اختر المنطقة لهذا الدور"
            "SQUARE_MANAGER", "FIELD_STAFF" -> if (hasSquareField && !squarePicked) errors["squareId"] = "اختر المربع لهذا الدور"
        }
        if (errors.isNotEmpty()) {
            val reasons = errors.values.distinct().joinToString("\n") { "• $it" }
            ui.update { it.copy(errors = errors, message = reasons, isError = true) }
            return
        }
        values["username"] = user!!
        if (role in setOf("REGION_MANAGER", "REGION_ADMIN")) values.remove("squareId")
        ui.update { it.copy(busy = true) }
        viewModelScope.launch {
            when (val r = repo.create(me, values, sch)) {
                is Outcome.Ok -> {
                    val name = values["displayName"].orEmpty().trim()
                    if (role == "SQUARE_MANAGER") values["squareId"]?.let { linkManager(me, it, name) }
                    ui.update { AccountsUi(message = "أُنشئ حساب $name", isError = false, revealedPassword = user to pw) }
                    scheduler.kick()
                }
                is Outcome.Failure -> ui.update { it.copy(busy = false, message = r.reason, isError = true) }
            }
        }
    }

    /** مدير المربع الجديد يصبح مسؤول المربع المعروض إن لم يُسجَّل له مسؤول. */
    private suspend fun linkManager(me: Profile, squareId: String, name: String) {
        val sq = directory.squareById(squareId) ?: return
        if (!sq.managerName.isNullOrBlank()) return
        directory.updateSquare(me, squareId, mapOf("name" to sq.name, "managerName" to name, "idNumber" to sq.idNumber.orEmpty(), "phone" to sq.phone.orEmpty()))
    }

    fun reset(u: UserEntity) = viewModelScope.launch {
        when (val r = repo.resetPassword(u.id)) {
            is Outcome.Ok -> ui.update { it.copy(revealedPassword = u.username to r.value) }
            is Outcome.Failure -> ui.update { it.copy(message = r.reason, isError = true) }
        }
    }

    fun openEdit(u: UserEntity) = ui.update {
        AccountsUi(edit = u, values = mapOf("displayName" to u.displayName, "role" to u.role, "regionId" to u.regionId.orEmpty(), "squareId" to u.squareId.orEmpty()))
    }
    fun closeEdit() = ui.update { it.copy(edit = null, errors = emptyMap(), busy = false) }

    fun submitEdit() {
        val cur = ui.value
        val target = cur.edit ?: return
        if (cur.values["displayName"].orEmpty().isBlank()) { ui.update { it.copy(errors = mapOf("displayName" to "الاسم مطلوب")) }; return }
        ui.update { it.copy(busy = true) }
        viewModelScope.launch {
            when (val r = repo.update(target.id, cur.values)) {
                is Outcome.Ok -> { ui.update { AccountsUi(message = "حُدّثت بيانات ${cur.values["displayName"].orEmpty()}", isError = false) }; scheduler.kick() }
                is Outcome.Failure -> ui.update { it.copy(busy = false, message = r.reason, isError = true) }
            }
        }
    }

    fun askDelete(u: UserEntity) = ui.update { it.copy(deleting = u) }
    fun cancelDelete() = ui.update { it.copy(deleting = null) }
    fun confirmDelete() {
        val u = ui.value.deleting ?: return
        ui.update { it.copy(deleting = null) }
        viewModelScope.launch {
            when (val r = repo.delete(u.id)) {
                is Outcome.Ok -> { ui.update { it.copy(message = "حُذف حساب ${u.displayName}", isError = false) }; scheduler.kick() }
                is Outcome.Failure -> ui.update { it.copy(message = r.reason, isError = true) }
            }
        }
    }

    fun toggle(u: UserEntity) = viewModelScope.launch {
        when (val r = repo.setActive(u.id, !u.active)) {
            is Outcome.Ok -> scheduler.kick()
            is Outcome.Failure -> ui.update { it.copy(message = r.reason, isError = true) }
        }
    }
}
