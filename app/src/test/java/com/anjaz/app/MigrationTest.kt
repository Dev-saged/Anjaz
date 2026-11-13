package com.anjaz.app

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.anjaz.app.data.db.AnjazDatabase
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// يبني قاعدة v1 من المخطط المُصدَّر فعلياً ثم يفتحها عبر Room v2 (يتحقق من الترحيل والمخطط)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class MigrationTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    private fun createV1(name: String) {
        val schema = JSONObject(File("schemas/com.anjaz.app.data.db.AnjazDatabase/1.json").readText()).getJSONObject("database")
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(ctx).name(name).callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    val entities = schema.getJSONArray("entities")
                    for (i in 0 until entities.length()) {
                        val e = entities.getJSONObject(i)
                        val table = e.getString("tableName")
                        db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", table))
                        val idx = e.optJSONArray("indices")
                        if (idx != null) for (j in 0 until idx.length()) db.execSQL(idx.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                    }
                    db.execSQL(
                        "INSERT INTO tasks (id, number, title, description, createdBy, regionId, squareId, assigneeId, priority, status, deadline, createdAt, updatedAt, archived) " +
                            "VALUES ('t1', NULL, 'مهمة قديمة', '', 'u', 'A', 'A1', NULL, 'NORMAL', 'NEW', NULL, 1, 1, 0)",
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build(),
        )
        helper.writableDatabase.close()
        helper.close()
    }

    @Test fun migrate1To2KeepsDataAndAddsTables() = runBlocking {
        createV1("mig.db")
        val db = Room.databaseBuilder(ctx, AnjazDatabase::class.java, "mig.db").addMigrations(*AnjazDatabase.MIGRATIONS).build()
        assertEquals("مهمة قديمة", db.tasks().byId("t1")?.title)
        assertEquals(0, db.sos().observeOpenCountNow())
        assertTrue(db.chat().roomById("none") == null)
        db.close()
    }
}

private suspend fun com.anjaz.app.data.db.SosDao.observeOpenCountNow(): Int = observeOpenCount().first()
