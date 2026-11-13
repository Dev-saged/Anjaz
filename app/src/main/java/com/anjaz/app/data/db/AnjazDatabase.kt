package com.anjaz.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(
    entities = [
        RegionEntity::class, SquareEntity::class, CampEntity::class, UserEntity::class,
        TaskEntity::class, TaskEventEntity::class, OutboxEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AnjazDatabase : RoomDatabase() {
    abstract fun tasks(): TaskDao
    abstract fun taskEvents(): TaskEventDao
    abstract fun outbox(): OutboxDao
    abstract fun directory(): DirectoryDao

    companion object {
        const val NAME = "anjaz.db"

        // ترحيلات مرقّمة للإضافة فقط، لا يُعدَّل القديم
        val MIGRATIONS: Array<Migration> = emptyArray()
    }
}
