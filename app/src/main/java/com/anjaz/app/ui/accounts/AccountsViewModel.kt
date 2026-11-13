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
    val revealedPassword: Pair<String, String>? = null,
)

@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val repo: AccountsRepository,
    auth: AuthRepository,
    private val scheduler: SyncScheduler,
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
        val errors = SchemaValidator.validate(sch, cur.values)
        if (errors.isNotEmpty()) { ui.update { it.copy(errors = errors) }; return }
        ui.update { it.copy(busy = true) }
        viewModelScope.launch {
            when (val r = repo.create(me, cur.values, sch)) {
                is Outcome.Ok -> {
                    val name = cur.values["displayName"].orEmpty()
                    ui.update { AccountsUi(message = "أُنشئ حساب $name", revealedPassword = cur.values["username"].orEmpty() to cur.values["password"].orEmpty()) }
                    scheduler.kick()
                }
                is Outcome.Failure -> ui.update { it.copy(busy = false, message = r.reason) }
            }
        }
    }

    fun reset(u: UserEntity) = viewModelScope.launch {
        when (val r = repo.resetPassword(u.id)) {
            is Outcome.Ok -> ui.update { it.copy(revealedPassword = u.username to r.value) }
            is Outcome.Failure -> ui.update { it.copy(message = r.reason) }
        }
    }

    fun toggle(u: UserEntity) = viewModelScope.launch {
        when (val r = repo.setActive(u.id, !u.active)) {
            is Outcome.Ok -> scheduler.kick()
            is Outcome.Failure -> ui.update { it.copy(message = r.reason) }
        }
    }
}
