package com.anjaz.app.update

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.anjaz.app.BrandConfig
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/** فحص دوري في الخلفية: عند وجود نسخة أحدث يصل إشعار مرة واحدة لكل نسخة. */
@HiltWorker
class UpdateCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val manager: UpdateManager,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        manager.restore()
        manager.check(force = true)
        val s = manager.state.value
        val prefs = applicationContext.getSharedPreferences("update", Context.MODE_PRIVATE)
        when (s) {
            is UpdateManager.State.Available -> notifyOnce(prefs, s.release.version) { UpdateNotifications.available(applicationContext, s.release.version) }
            is UpdateManager.State.Ready -> notifyOnce(prefs, "ready-${s.release.version}") { UpdateNotifications.ready(applicationContext, s.release.version) }
            else -> Unit
        }
        return Result.success()
    }

    private fun notifyOnce(prefs: android.content.SharedPreferences, key: String, post: () -> Unit) {
        if (prefs.getString("notified", null) == key) return
        post()
        prefs.edit().putString("notified", key).apply()
    }

    companion object {
        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<UpdateCheckWorker>(BrandConfig.UPDATE_CHECK_HOURS, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("update-check", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
