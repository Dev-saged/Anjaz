package com.anjaz.app.ui.test

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.hilt.navigation.compose.hiltViewModel
import com.anjaz.app.core.Check
import com.anjaz.app.core.SelfTest
import com.anjaz.app.data.DemoSeeder
import com.anjaz.app.ui.components.AnjazCard
import com.anjaz.app.ui.components.PrimaryButton
import com.anjaz.app.ui.components.QuietButton
import com.anjaz.app.ui.components.ScreenTitle
import com.anjaz.app.ui.components.Tag
import com.anjaz.app.ui.components.Tone
import com.anjaz.app.ui.theme.Anjaz
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class SeedViewModel @Inject constructor(private val seeder: DemoSeeder) : ViewModel() {
    var result by mutableStateOf<String?>(null)
        private set

    fun seed() {
        viewModelScope.launch { result = "أُضيفت ${seeder.seed()} مهام تجريبية" }
    }
}

@Composable
fun SelfTestScreen(vm: SeedViewModel = hiltViewModel()) {
    val c = Anjaz.colors
    val ctx = LocalContext.current
    var checks by remember { mutableStateOf<List<Check>?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { checks = SelfTest.run(ctx) }
    Column(Modifier.fillMaxSize().background(c.surface).statusBarsPadding().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenTitle("اختبار ذاتي")
        val list = checks
        if (list == null) Text("جارٍ التشغيل…", color = c.mute, style = MaterialTheme.typography.bodyMedium)
        else {
            val passed = list.count { it.ok }
            Text("$passed / ${list.size} ناجح", color = if (passed == list.size) c.emeraldDeep else c.redDeep, style = MaterialTheme.typography.titleMedium)
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list) { ch ->
                    AnjazCard(Modifier.fillMaxWidth()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Tag(if (ch.ok) "نجح" else "فشل", if (ch.ok) Tone.Success else Tone.Danger)
                            Column(Modifier.weight(1f)) {
                                Text(ch.name, color = c.ink, style = MaterialTheme.typography.bodyMedium)
                                if (ch.detail.isNotEmpty()) Text(ch.detail, color = c.mute, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
        vm.result?.let { Text(it, color = c.emeraldDeep, style = MaterialTheme.typography.bodyMedium) }
        QuietButton("إضافة بيانات تجريبية") { vm.seed() }
        PrimaryButton("إعادة التشغيل", { scope.launch { checks = null; checks = SelfTest.run(ctx) } })
    }
}
