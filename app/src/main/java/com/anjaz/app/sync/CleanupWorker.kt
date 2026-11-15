package com.anjaz.app.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.anjaz.app.data.Clock
import com.anjaz.app.data.Trash
import com.anjaz.app.data.db.AnjazDatabase
import com.anjaz.app.media.MediaRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File

@HiltWorker
class CleanupWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val media: MediaRepository,
    private val db: AnjazDatabase,
    private val clock: Clock,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = runCatching {
        media.cleanOrphans()
        purgeTrash()
        purgeCache()
        db.outbox().pruneSynced(clock.now() - 7L * 24 * 3_600_000)
    }.fold({ Result.success() }, { Result.retry() })

    /** ما مضى على حذفه 30 يوماً (أو حُذف نهائياً) يُزال محلياً؛ المربع لا يُزال قبل مخيماته. */
    private suspend fun purgeTrash() {
        val cutoff = Trash.cutoff(clock.now())
        db.tasks().purgeBefore(cutoff)
        db.directory().purgeCamps(cutoff)
        db.directory().purgeSquares(cutoff)
    }

    private fun purgeCache() {
        val stale = clock.now() - 7L * 24 * 3_600_000
        context.cacheDir.walkTopDown().filter { it.isFile && it.lastModified() < stale }.forEach { it.delete() }
    }
}
