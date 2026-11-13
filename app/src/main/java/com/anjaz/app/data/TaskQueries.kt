package com.anjaz.app.data

import com.anjaz.app.data.db.AnjazDatabase
import com.anjaz.app.data.db.TaskEntity
import com.anjaz.app.data.db.TaskEventEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class TaskQueries @Inject constructor(private val db: AnjazDatabase, private val clock: Clock) {
    fun all(): Flow<List<TaskEntity>> = db.tasks().observeAll()
    fun events(taskId: String): Flow<List<TaskEventEntity>> = db.taskEvents().observeFor(taskId)
    fun team(): Flow<List<com.anjaz.app.data.db.UserEntity>> = db.directory().observeTeam()
    fun now(): Long = clock.now()
}
