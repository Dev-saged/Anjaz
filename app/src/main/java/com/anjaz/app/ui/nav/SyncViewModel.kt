package com.anjaz.app.ui.nav

import androidx.lifecycle.ViewModel
import com.anjaz.app.domain.Profile
import com.anjaz.app.sync.SyncEngine
import com.anjaz.app.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@HiltViewModel
class SyncViewModel @Inject constructor(
    private val engine: SyncEngine,
    private val scheduler: SyncScheduler,
    private val tokens: com.anjaz.app.push.DeviceTokens,
) : ViewModel() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var jobs: List<Job> = emptyList()

    fun start(profile: Profile) {
        jobs.forEach { it.cancel() }
        jobs = engine.listen(scope, profile)
        scheduler.schedulePeriodic()
        scheduler.kick()
        scope.launch { tokens.register() }
    }

    override fun onCleared() { scope.cancel() }
}
