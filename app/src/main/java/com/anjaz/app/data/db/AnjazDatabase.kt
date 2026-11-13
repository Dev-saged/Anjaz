package com.anjaz.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(
    entities = [
        RegionEntity::class, SquareEntity::class, CampEntity::class, UserEntity::class,
        TaskEntity::class, TaskEventEntity::class, OutboxEntity::class,
        SosEntity::class, RoomEntity::class, MessageEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class AnjazDatabase : RoomDatabase() {
    abstract fun tasks(): TaskDao
    abstract fun taskEvents(): TaskEventDao
    abstract fun outbox(): OutboxDao
    abstract fun directory(): DirectoryDao
    abstract fun sos(): SosDao
    abstract fun chat(): ChatDao
    abstract fun contacts(): ContactDao

    companion object {
        const val NAME = "anjaz.db"

        // ترحيلات مرقّمة للإضافة فقط، لا يُعدَّل القديم
        val MIGRATIONS: Array<Migration> = arrayOf(Migrations.MIGRATION_1_2, Migrations.MIGRATION_2_3)
    }
}
