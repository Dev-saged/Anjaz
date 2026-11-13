package com.anjaz.app.di

import android.content.Context
import androidx.room.Room
import com.anjaz.app.data.Clock
import com.anjaz.app.data.SystemClock
import com.anjaz.app.data.db.AnjazDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides @Singleton
    fun database(@ApplicationContext context: Context): AnjazDatabase =
        Room.databaseBuilder(context, AnjazDatabase::class.java, AnjazDatabase.NAME)
            .addMigrations(*AnjazDatabase.MIGRATIONS)
            .build()

    @Provides fun taskDao(db: AnjazDatabase) = db.tasks()
    @Provides fun outboxDao(db: AnjazDatabase) = db.outbox()
    @Provides fun directoryDao(db: AnjazDatabase) = db.directory()
    @Provides fun clock(): Clock = SystemClock
}
