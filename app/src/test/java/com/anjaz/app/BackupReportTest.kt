package com.anjaz.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.anjaz.app.backup.BackupManager
import com.anjaz.app.data.Clock
import com.anjaz.app.data.db.AnjazDatabase
import com.anjaz.app.data.db.SquareEntity
import com.anjaz.app.data.db.TaskEntity
import com.anjaz.app.data.db.UserEntity
import com.anjaz.app.domain.Outcome
import com.anjaz.app.report.PdfLayout
import com.anjaz.app.report.ReportData
import com.anjaz.app.report.XlsxExporter
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class BackupReportTest {
    private lateinit var db: AnjazDatabase
    private lateinit var backup: BackupManager
    private val clock = Clock { 7_000L }

    private fun task(id: String, updated: Long, title: String = "توزيع المياه") = TaskEntity(
        id, 1, title, "", "u", "R1", "S1", "u2", "HIGH", "NEW", 1_700_000_000_000, 1, updated, false,
    )

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AnjazDatabase::class.java).build()
        backup = BackupManager(db, clock)
    }

    @After fun tearDown() = db.close()

    @Test fun exportThenRestoreIntoEmptyDatabaseKeepsEverything() = runBlocking {
        db.tasks().upsert(task("t1", 5))
        db.directory().upsertSquare(SquareEntity("S1", "R1", "مربع الشمال", null, null, null, 1))
        val text = backup.export()
        withContext(Dispatchers.IO) { db.clearAllTables() }
        val r = backup.restore(text)
        assertTrue(r is Outcome.Ok)
        assertEquals("توزيع المياه", db.tasks().byId("t1")!!.title)
        assertEquals("مربع الشمال", db.directory().squareById("S1")!!.name)
    }

    @Test fun restoreNeverOverwritesNewerLocalData() = runBlocking {
        db.tasks().upsert(task("t1", 5, "قديمة"))
        val text = backup.export()
        db.tasks().upsert(task("t1", 9, "أحدث محلياً"))
        backup.restore(text)
        assertEquals("أحدث محلياً", db.tasks().byId("t1")!!.title)
        assertEquals(0, db.outbox().countAll())
    }

    @Test fun restoreRejectsGarbageAndNewerFormats() = runBlocking {
        assertTrue(backup.restore("not json") is Outcome.Failure)
        assertTrue(backup.restore("""{"format":9,"schemaVersion":3,"createdAt":1}""") is Outcome.Failure)
        assertTrue(backup.restore("""{"format":1,"schemaVersion":99,"createdAt":1}""") is Outcome.Failure)
    }

    @Test fun xlsxContainsRtlSheetEscapedValuesAndSummary() {
        val table = ReportData.tasks(
            listOf(task("t1", 5, "تنظيف <المخيم> & الممرات")), listOf(UserEntity("u2", "u2", "أحمد", "FIELD_STAFF", "R1", "S1", true, 1)), 1_700_000_000_000, "المربع 1",
        )
        val out = ByteArrayOutputStream().also { XlsxExporter.write(table, it) }
        val files = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { z ->
            generateSequence { z.nextEntry }.forEach { files[it.name] = z.readBytes().toString(Charsets.UTF_8) }
        }
        assertEquals(setOf("[Content_Types].xml", "_rels/.rels", "xl/workbook.xml", "xl/_rels/workbook.xml.rels", "xl/worksheets/sheet1.xml"), files.keys)
        val sheet = files.getValue("xl/worksheets/sheet1.xml")
        assertTrue(sheet.contains("rightToLeft=\"1\""))
        assertTrue(sheet.contains("تنظيف &lt;المخيم&gt; &amp; الممرات"))
        assertTrue(sheet.contains("أحمد"))
        assertEquals("A", XlsxExporter.col(0)); assertEquals("AA", XlsxExporter.col(26))
    }

    @Test fun pdfLayoutPaginatesRightToLeftAndNumbersPages() {
        val tasks = (1..120).map { task("t$it", it.toLong()) }
        val table = ReportData.tasks(tasks, emptyList(), 1_700_000_000_000, "الكل")
        val pages = PdfLayout.build(table) { s, _ -> s.length * 5f }
        assertTrue(pages.size >= 4)
        assertEquals((1..pages.size).toList(), pages.map { it.number })
        val header = pages[1].texts.filter { it.bold }.sortedBy { it.y }.first().y
        val firstRow = pages[1].texts.filter { !it.bold && it.y > header }.minBy { it.y }
        assertTrue(firstRow.x > PdfLayout.W / 2)
        val headerXs = pages[0].texts.filter { it.bold && it.size < 18f && it.y < 120f }.map { it.x }
        assertEquals(headerXs.sortedDescending(), headerXs)
        assertTrue(pages.flatMap { it.texts }.none { it.x < 0 || it.y > PdfLayout.H })
        assertTrue(pages.last().texts.any { it.text == "إجمالي المهام" })
    }

    @Test fun pdfLayoutClipsLongCells() {
        val long = task("t1", 1, "ع".repeat(200))
        val pages = PdfLayout.build(ReportData.tasks(listOf(long), emptyList(), 1, "x")) { s, _ -> s.length * 5f }
        assertTrue(pages[0].texts.any { it.text.endsWith("…") && it.text.length < 30 })
    }
}
