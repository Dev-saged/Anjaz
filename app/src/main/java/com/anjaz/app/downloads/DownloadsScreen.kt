package com.anjaz.app.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anjaz.app.ui.components.AnjazCard
import com.anjaz.app.ui.components.AnjazIcon
import com.anjaz.app.ui.components.PrimaryButton
import com.anjaz.app.ui.components.QuietButton
import com.anjaz.app.ui.components.ScreenTitle
import com.anjaz.app.ui.icons.AnjazIcons
import com.anjaz.app.ui.theme.Anjaz
import com.anjaz.app.ui.theme.ltr
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(val center: DownloadCenter) : ViewModel()

@Composable
fun DownloadsScreen(onBack: () -> Unit, vm: DownloadsViewModel = hiltViewModel()) {
    val c = Anjaz.colors
    val items by vm.center.items.collectAsStateWithLifecycle(emptyList())
    var variant by remember { mutableStateOf(vm.center.variant) }
    Column(Modifier.fillMaxSize().background(c.surface).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onBack).semantics { contentDescription = "رجوع" }, contentAlignment = Alignment.Center) { AnjazIcon(AnjazIcons.Back, c.ink) }
            ScreenTitle("التنزيلات والمكوّنات", Modifier.weight(1f))
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp, 8.dp, 18.dp, 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("هذه المكوّنات تُنزَّل بعد التثبيت لتبقى حزمة التطبيق صغيرة. تُنزَّل تلقائياً على الواي فاي، ويمكنك التنزيل أو إعادة المحاولة هنا.", color = c.mute, style = MaterialTheme.typography.bodySmall) }
            items(items, key = { it.id }) { it ->
                AnjazCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(it.title, color = c.ink, style = MaterialTheme.typography.titleMedium)
                                Text(it.desc, color = c.mute, style = MaterialTheme.typography.bodySmall)
                            }
                            Text(
                                when (it.status) { DlStatus.READY -> "جاهز"; DlStatus.DOWNLOADING -> ltr("${it.percent}") + "٪"; DlStatus.FAILED -> "فشل"; DlStatus.MISSING -> "غير منزَّل" },
                                color = when (it.status) { DlStatus.READY -> c.emeraldDeep; DlStatus.FAILED -> c.redDeep; else -> c.mute },
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                        if (it.status == DlStatus.DOWNLOADING) LinearProgressIndicator(progress = { it.percent / 100f }, Modifier.fillMaxWidth().height(8.dp).clip(CircleShape), color = c.emerald, trackColor = c.line)
                        if (it.status == DlStatus.FAILED) Text(it.error ?: "تعذّر التنزيل", color = c.redDeep, style = MaterialTheme.typography.bodySmall)
                        if (it.id == DownloadCenter.MODEL && it.status != DlStatus.DOWNLOADING) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                vm.center.variants.forEach { v ->
                                    val on = v.key == variant
                                    Column(
                                        Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(if (on) c.emerald.copy(alpha = .18f) else c.line.copy(alpha = .5f))
                                            .clickable { vm.center.selectVariant(v.key); variant = v.key }.padding(vertical = 10.dp, horizontal = 6.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Text(v.label, color = if (on) c.emeraldDeep else c.ink, style = MaterialTheme.typography.labelLarge)
                                        Text(ltr("≈${v.approxMb}") + " MB", color = c.mute, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                            Text(vm.center.variants.first { v -> v.key == variant }.note, color = c.mute, style = MaterialTheme.typography.bodySmall)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            when (it.status) {
                                DlStatus.MISSING -> PrimaryButton("تنزيل", { vm.center.start(it.id) })
                                DlStatus.FAILED -> PrimaryButton("إعادة المحاولة", { vm.center.start(it.id) })
                                DlStatus.READY -> QuietButton("حذف لتوفير المساحة") { vm.center.remove(it.id) }
                                DlStatus.DOWNLOADING -> {}
                            }
                        }
                    }
                }
            }
        }
    }
}
