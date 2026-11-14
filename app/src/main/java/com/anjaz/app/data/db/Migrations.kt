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

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `camps` ADD COLUMN `regionId` TEXT NOT NULL DEFAULT ''")
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            listOf(
                "`mediaId` TEXT NOT NULL DEFAULT ''", "`mediaMime` TEXT NOT NULL DEFAULT ''", "`mediaName` TEXT NOT NULL DEFAULT ''",
                "`mediaSize` INTEGER NOT NULL DEFAULT 0", "`durMs` INTEGER NOT NULL DEFAULT 0", "`width` INTEGER NOT NULL DEFAULT 0",
                "`height` INTEGER NOT NULL DEFAULT 0", "`lat` REAL", "`lng` REAL", "`localPath` TEXT NOT NULL DEFAULT ''",
                "`mediaState` TEXT NOT NULL DEFAULT 'NONE'", "`transcript` TEXT NOT NULL DEFAULT ''",
            ).forEach { db.execSQL("ALTER TABLE `messages` ADD COLUMN $it") }
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `users` ADD COLUMN `deleted` INTEGER NOT NULL DEFAULT 0")
        }
    }
}
