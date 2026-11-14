package com.anjaz.app.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anjaz.app.cloud.AuthRepository
import com.anjaz.app.domain.Outcome
import com.anjaz.app.domain.Profile
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface SessionState {
    data object Loading : SessionState
    data object LoggedOut : SessionState
    data class LoggedIn(val profile: Profile) : SessionState
}

data class LoginUiState(val username: String = "", val password: String = "", val busy: Boolean = false, val error: String? = null)

@HiltViewModel
class AppSessionViewModel @Inject constructor(private val auth: AuthRepository) : ViewModel() {
    private val restored = MutableStateFlow(false)

    val state: StateFlow<SessionState> = combine(auth.session, restored) { p, r ->
        when {
            !r -> SessionState.Loading
            p != null -> SessionState.LoggedIn(p)
            else -> SessionState.LoggedOut
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SessionState.Loading)

    init {
        viewModelScope.launch {
            if (auth.restore() == null) auth.signOut()
            restored.value = true
        }
    }

    fun signOut() { viewModelScope.launch { auth.signOut() } }
}

@HiltViewModel
class LoginViewModel @Inject constructor(private val auth: AuthRepository) : ViewModel() {
    private val _ui = MutableStateFlow(LoginUiState())
    val ui: StateFlow<LoginUiState> = _ui

    fun username(v: String) = _ui.update { it.copy(username = v.take(80), error = null) }
    fun password(v: String) = _ui.update { it.copy(password = v.take(128), error = null) }

    fun submit() {
        val cur = _ui.value
        if (cur.busy) return
        _ui.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val r = auth.signIn(cur.username, cur.password)
            _ui.update { it.copy(busy = false, error = (r as? Outcome.Failure)?.reason, password = if (r is Outcome.Failure) "" else it.password) }
        }
    }
}
