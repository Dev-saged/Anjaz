package com.anjaz.app.update

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import javax.inject.Inject

/** يستضيف التنزيل في المقدمة ليستمر بعد إغلاق التطبيق، ويعرض النسبة ثم «تم التحميل». */
@AndroidEntryPoint
class UpdateDownloadService : Service() {
    @Inject lateinit var manager: UpdateManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val first = manager.state.value as? UpdateManager.State.Downloading
        ServiceCompat.startForeground(
            this, UpdateNotifications.PROGRESS_ID,
            UpdateNotifications.progress(this, first?.release?.version.orEmpty(), first?.percent ?: 0),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
        if (job?.isActive == true) return START_NOT_STICKY
        job = scope.launch {
            launch {
                manager.state.takeWhile { it is UpdateManager.State.Downloading }.collect { s ->
                    s as UpdateManager.State.Downloading
                    runCatching {
                        getSystemService(NotificationManager::class.java)
                            .notify(UpdateNotifications.PROGRESS_ID, UpdateNotifications.progress(this@UpdateDownloadService, s.release.version, s.percent))
                    }
                }
            }
            manager.download()
            when (val end = manager.state.value) {
                is UpdateManager.State.Ready -> UpdateNotifications.ready(this@UpdateDownloadService, end.release.version)
                is UpdateManager.State.Failed -> UpdateNotifications.failed(this@UpdateDownloadService)
                else -> Unit
            }
            ServiceCompat.stopForeground(this@UpdateDownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
