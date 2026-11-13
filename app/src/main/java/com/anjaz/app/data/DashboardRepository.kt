package com.anjaz.app.data

import com.anjaz.app.core.DayMath
import com.anjaz.app.data.db.AnjazDatabase
import com.anjaz.app.data.db.TaskEntity
import com.anjaz.app.domain.TaskStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class DashboardCounts(val total: Int, val inProgress: Int, val awaiting: Int, val overdue: Int, val done: Int) {
    val progress: Float get() = if (total == 0) 0f else done.toFloat() / total
}

@Singleton
class DashboardRepository @Inject constructor(
    private val db: AnjazDatabase,
    private val clock: Clock,
) {
    fun counts(): Flow<DashboardCounts> {
        val t = db.tasks()
        return combine(
            t.countActiveTotal(),
            t.countByStatus(TaskStatus.IN_PROGRESS.name),
            t.countByStatus(TaskStatus.AWAITING_REVIEW.name),
            t.countOverdue(clock.now()),
            t.countByStatus(TaskStatus.APPROVED.name),
        ) { total, prog, wait, over, done -> DashboardCounts(total, prog, wait, over, done) }
    }

    fun week(startOfToday: Long): Flow<List<TaskEntity>> =
        db.tasks().observeByDeadline(startOfToday, DayMath.addDays(startOfToday, 7))

    fun unsynced(): Flow<Int> = db.outbox().observeUnsynced()

    fun now(): Long = clock.now()
}
