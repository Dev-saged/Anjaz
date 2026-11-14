package com.anjaz.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anjaz.app.cloud.AuthRepository
import com.anjaz.app.data.Clock
import com.anjaz.app.data.db.AnjazDatabase
import com.anjaz.app.domain.Dashboard
import com.anjaz.app.domain.DashboardBuilder
import com.anjaz.app.sync.Notifier
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class DashboardViewModel @Inject constructor(
    db: AnjazDatabase,
    auth: AuthRepository,
    private val clock: Clock,
    private val notifier: Notifier,
) : ViewModel() {
    private val me = auth.session.filterNotNull()
    val reminded = MutableStateFlow(setOf<String>())
    val canRemind = me.combine(MutableStateFlow(Unit)) { p, _ -> p.canAssign }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val ui: StateFlow<Dashboard?> = combine(me, db.tasks().observeAll(), db.directory().observeRegions(), db.directory().observeSquares(), db.directory().observeTeam()) { p, tasks, regions, squares, team ->
        DashboardBuilder.build(p, tasks, regions.associate { it.id to it.name }, squares.associate { it.id to it.name }, team.associate { it.id to it.displayName }, clock.now())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** تذكير المعنيّين بالمهمة المتأخرة عبر إشعار الـ Worker. */
    fun remind(taskId: String) {
        viewModelScope.launch {
            runCatching { notifier.notify("task", taskId) }
            reminded.value = reminded.value + taskId
        }
    }
}
