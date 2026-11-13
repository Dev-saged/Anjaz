package com.anjaz.app.ui.reports

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anjaz.app.backup.BackupManager
import com.anjaz.app.data.Clock
import com.anjaz.app.data.db.AnjazDatabase
import com.anjaz.app.domain.Outcome
import com.anjaz.app.report.PdfExporter
import com.anjaz.app.report.ReportData
import com.anjaz.app.report.XlsxExporter
import com.anjaz.app.cloud.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ReportsUi(val busy: Boolean = false, val message: String? = null)

@HiltViewModel
class ReportsViewModel @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val db: AnjazDatabase,
    private val backup: BackupManager,
    private val clock: Clock,
    private val auth: AuthRepository,
) : ViewModel() {
    val ui = MutableStateFlow(ReportsUi())

    private fun run(okText: String, block: suspend () -> String?) {
        ui.value = ReportsUi(busy = true)
        viewModelScope.launch {
            val msg = try { withContext(Dispatchers.IO) { block() } ?: okText } catch (e: Exception) { "تعذّرت العملية: ${e.message.orEmpty().take(80)}" }
            ui.value = ReportsUi(message = msg)
        }
    }

    private suspend fun table(): com.anjaz.app.report.ReportTable {
        val scope = auth.session.first()?.roleEnum?.label ?: "إنجاز"
        return ReportData.tasks(db.tasks().all(), db.directory().allUsers(), clock.now(), scope)
    }

    private fun write(uri: Uri, block: (java.io.OutputStream) -> Unit) {
        (ctx.contentResolver.openOutputStream(uri) ?: error("لا يمكن فتح الملف")).use(block)
    }

    fun exportPdf(uri: Uri) = run("حُفظ تقرير PDF") { val t = table(); write(uri) { PdfExporter.write(t, it) }; null }
    fun exportXlsx(uri: Uri) = run("حُفظ ملف Excel") { val t = table(); write(uri) { XlsxExporter.write(t, it) }; null }
    fun exportBackup(uri: Uri) = run("حُفظت النسخة الاحتياطية") { val text = backup.export(); write(uri) { it.write(text.toByteArray()) }; null }

    fun importBackup(uri: Uri) = run("") {
        val text = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: return@run "لا يمكن قراءة الملف"
        when (val r = backup.restore(text)) {
            is Outcome.Ok -> "استُعيد: ${r.value.tasks} مهمة، ${r.value.directory} عنصر دليل، ${r.value.messages} رسالة"
            is Outcome.Failure -> r.reason
        }
    }

    fun dismiss() { ui.value = ReportsUi() }
}
