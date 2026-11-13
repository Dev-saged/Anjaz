package com.anjaz.app.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// ترحيلات للإضافة فقط: لا يُعدَّل ترحيل صدر سابقاً
object Migrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `sos` (`id` TEXT NOT NULL, `senderId` TEXT NOT NULL, `senderName` TEXT NOT NULL, `regionId` TEXT, `squareId` TEXT, `status` TEXT NOT NULL, `note` TEXT NOT NULL, `lat` REAL, `lng` REAL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_sos_createdAt` ON `sos` (`createdAt`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `rooms` (`id` TEXT NOT NULL, `type` TEXT NOT NULL, `name` TEXT NOT NULL, `members` TEXT NOT NULL, `lastMessage` TEXT NOT NULL, `lastAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_rooms_lastAt` ON `rooms` (`lastAt`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `messages` (`id` TEXT NOT NULL, `roomId` TEXT NOT NULL, `senderId` TEXT NOT NULL, `type` TEXT NOT NULL, `text` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `readBy` TEXT NOT NULL, `state` TEXT NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_roomId_createdAt` ON `messages` (`roomId`, `createdAt`)")
        }
    }
}
