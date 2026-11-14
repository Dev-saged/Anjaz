package com.anjaz.app.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.anjaz.app.media.MediaRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class CleanupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val media: MediaRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = runCatching { media.cleanOrphans() }.fold({ Result.success() }, { Result.retry() })
}
