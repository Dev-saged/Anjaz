package com.anjaz.app.ui.sos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anjaz.app.cloud.AuthRepository
import com.anjaz.app.data.SosRepository
import com.anjaz.app.data.db.SosEntity
import com.anjaz.app.domain.Outcome
import com.anjaz.app.domain.Profile
import com.anjaz.app.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SosViewModel @Inject constructor(
    private val repo: SosRepository,
    auth: AuthRepository,
    private val scheduler: SyncScheduler,
) : ViewModel() {
    val profile: StateFlow<Profile?> = auth.session.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val alerts: StateFlow<List<SosEntity>> = repo.recent().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val openCount: StateFlow<Int> = repo.openCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val message = MutableStateFlow<String?>(null)

    fun send(note: String, lat: Double?, lng: Double?) {
        val p = profile.value ?: return
        viewModelScope.launch {
            when (val r = repo.send(p, note, lat, lng)) {
                is Outcome.Ok -> { message.value = "أُرسل النداء"; scheduler.kick() }
                is Outcome.Failure -> message.value = r.reason
            }
        }
    }

    fun advance(id: String) {
        val p = profile.value ?: return
        viewModelScope.launch {
            val r = repo.advance(id, p)
            if (r is Outcome.Failure) message.value = r.reason else scheduler.kick()
        }
    }

    fun consume() { message.value = null }
}
