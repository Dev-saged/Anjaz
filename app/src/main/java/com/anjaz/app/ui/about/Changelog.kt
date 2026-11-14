package com.anjaz.app.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anjaz.app.ui.theme.Anjaz
import com.anjaz.app.ui.theme.ltr

private data class Release(val version: String, val title: String, val lines: List<String>)

private val releases = listOf(
    Release("0.6.0", "جاهزية الإطلاق", listOf("تنظيف يومي تلقائي للملفات المؤقتة", "سجل التغييرات داخل التطبيق", "حالات فارغة موحّدة بأيقونة وشرح في كل القوائم")),
    Release("0.5.0", "الصوت والفيديو", listOf("إملاء صوتي بـ Whisper على الجهاز", "فيديو بضغط ذكي", "تسجيل صوتي بنمط واتساب", "أسماء مستخدمين ببريد كامل", "الطابور لا يتوقف بفشل رسالة")),
    Release("0.4.0", "الوسائط والمجموعات", listOf("صور وصوت وملفات في المحادثات", "مجموعات للقادة", "لوحات الأدوار والتصعيد")),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangelogSheet(onDismiss: () -> Unit) {
    val c = Anjaz.colors
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("ما الجديد", color = c.ink, style = MaterialTheme.typography.titleLarge)
            releases.forEach { r ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${ltr("v" + r.version)}  ·  ${r.title}", color = c.emeraldDeep, style = MaterialTheme.typography.titleSmall)
                    r.lines.forEach { Text("• $it", color = c.ink, style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
    }
}
