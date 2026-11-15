package com.anjaz.app.pack

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/** ينزّل حزمة المكوّنات تلقائياً على شبكة غير محدودة، ويعيد المحاولة عند الفشل. */
@HiltWorker
class PackPrefetchWorker @AssistedInject constructor(
    @Assisted ctx: Context, @Assisted params: WorkerParameters, private val packs: NativePacks,
) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = if (packs.ensure().isSuccess) Result.success() else Result.retry()

    companion object {
        fun schedule(ctx: Context) {
            val req = OneTimeWorkRequestBuilder<PackPrefetchWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(ctx).enqueueUniqueWork("pack-prefetch", ExistingWorkPolicy.KEEP, req)
        }
    }
}
