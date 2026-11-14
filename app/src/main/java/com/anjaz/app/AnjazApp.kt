package com.anjaz.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class AnjazApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var updates: com.anjaz.app.update.UpdateManager

    override fun onCreate() {
        super.onCreate()
        com.anjaz.app.push.Notifications.createChannels(this)
        com.anjaz.app.update.UpdateCheckWorker.schedule(this)
        Thread { runCatching { updates.restore() } }.start()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
}
