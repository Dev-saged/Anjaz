package com.anjaz.app.ui.reports

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anjaz.app.ui.components.AnjazCard
import com.anjaz.app.ui.components.AnjazIcon
import com.anjaz.app.ui.components.PrimaryButton
import com.anjaz.app.ui.components.QuietButton
import com.anjaz.app.ui.components.ScreenTitle
import com.anjaz.app.ui.icons.AnjazIcons
import com.anjaz.app.ui.theme.Anjaz

@Composable
fun ReportsScreen(onBack: () -> Unit, vm: ReportsViewModel = hiltViewModel()) {
    val c = Anjaz.colors
    val ui by vm.ui.collectAsStateWithLifecycle()
    val pdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { it?.let(vm::exportPdf) }
    val xlsx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { it?.let(vm::exportXlsx) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(vm::exportBackup) }
    val load = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::importBackup) }
    Column(Modifier.fillMaxSize().background(c.surface).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onBack).semantics { contentDescription = "رجوع" }, contentAlignment = Alignment.Center) {
                AnjazIcon(AnjazIcons.Back, c.ink)
            }
            ScreenTitle("التقارير والنسخ الاحتياطي")
        }
        ui.message?.let { Text(it, color = c.ink, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth().clickable(onClick = vm::dismiss).background(c.card).padding(14.dp)) }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AnjazCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("تقرير المهام", color = c.ink, style = MaterialTheme.typography.titleMedium)
                    Text("كل المهام ضمن نطاقك مع ملخص بالحالات.", color = c.mute, style = MaterialTheme.typography.bodySmall)
                    PrimaryButton("تصدير PDF", onClick = { pdf.launch("anjaz-tasks.pdf") }, enabled = !ui.busy)
                    QuietButton("تصدير Excel") { xlsx.launch("anjaz-tasks.xlsx") }
                }
            }
            AnjazCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("النسخ الاحتياطي", color = c.ink, style = MaterialTheme.typography.titleMedium)
                    Text("ملف JSON بكل بيانات هذا الجهاز. الاستعادة تدمج دون حذف ودون استبدال الأحدث.", color = c.mute, style = MaterialTheme.typography.bodySmall)
                    QuietButton("حفظ نسخة احتياطية") { save.launch("anjaz-backup.json") }
                    QuietButton("استعادة من ملف") { load.launch(arrayOf("application/json", "text/plain", "*/*")) }
                }
            }
        }
    }
}
