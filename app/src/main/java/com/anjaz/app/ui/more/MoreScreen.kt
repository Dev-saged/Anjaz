package com.anjaz.app.ui.more

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.graphics.vector.ImageVector
import com.anjaz.app.BrandConfig
import com.anjaz.app.BuildConfig
import androidx.compose.foundation.clickable
import com.anjaz.app.domain.Profile
import com.anjaz.app.ui.components.AnjazCard
import com.anjaz.app.ui.components.AnjazIcon
import com.anjaz.app.ui.components.ScreenTitle
import com.anjaz.app.ui.components.Tag
import com.anjaz.app.ui.components.Tone
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.anjaz.app.ui.icons.AnjazIcons
import kotlinx.coroutines.launch
import com.anjaz.app.ui.theme.Anjaz
import com.anjaz.app.ui.theme.ltr

private data class Entry(val title: String, val icon: ImageVector)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MoreScreen(profile: Profile, onSignOut: () -> Unit, onOpenSelfTest: () -> Unit, onOpenAccounts: () -> Unit, onOpenDirectory: () -> Unit, onOpenReports: () -> Unit, onOpenDashboard: () -> Unit, onOpenAudit: () -> Unit) {
    val c = Anjaz.colors
    val haptic = LocalHapticFeedback.current
    val upcoming = listOf(
        Entry("الأمان والحساب", AnjazIcons.Lock),
    )
    Column(
        Modifier.fillMaxSize().background(c.surface).statusBarsPadding().verticalScroll(rememberScrollState()).padding(18.dp).padding(bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ScreenTitle("المزيد", Modifier.padding(bottom = 6.dp))
        AnjazCard(Modifier.fillMaxWidth()) {
            Row(Modifier.heightIn(min = 44.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                AnjazIcon(AnjazIcons.User, c.ink)
                Column(Modifier.weight(1f)) {
                    Text(profile.displayName, color = c.ink, style = MaterialTheme.typography.titleMedium)
                    Text(profile.roleEnum.label, color = c.mute, style = MaterialTheme.typography.bodySmall)
                }
                Text("خروج", color = c.redDeep, style = MaterialTheme.typography.labelLarge, modifier = Modifier.heightIn(min = 44.dp).clickable(onClick = onSignOut).padding(8.dp))
            }
        }
        AnjazCard(Modifier.fillMaxWidth(), onClick = onOpenDirectory) {
            Row(Modifier.heightIn(min = 44.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                AnjazIcon(AnjazIcons.Map, c.ink)
                Text("المربعات والمخيمات", color = c.ink, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            }
        }
        AnjazCard(Modifier.fillMaxWidth(), onClick = onOpenDashboard) {
            Row(Modifier.heightIn(min = 44.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                AnjazIcon(AnjazIcons.Shield, c.ink)
                Text(if (profile.canAssign) "لوحة المتابعة والتصعيد" else "لوحة مهامي", color = c.ink, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            }
        }
        AnjazCard(Modifier.fillMaxWidth(), onClick = onOpenReports) {
            Row(Modifier.heightIn(min = 44.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                AnjazIcon(AnjazIcons.Report, c.ink)
                Text("التقارير والنسخ الاحتياطي", color = c.ink, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            }
        }
        if (profile.roleEnum.rank >= com.anjaz.app.domain.Role.REGION_ADMIN.rank) AnjazCard(Modifier.fillMaxWidth(), onClick = onOpenAudit) {
            Row(Modifier.heightIn(min = 44.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                AnjazIcon(AnjazIcons.Log, c.ink)
                Text("سجل العمليات", color = c.ink, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            }
        }
        if (profile.canAssign) AnjazCard(Modifier.fillMaxWidth(), onClick = onOpenAccounts) {
            Row(Modifier.heightIn(min = 44.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                AnjazIcon(AnjazIcons.User, c.ink)
                Text("الحسابات", color = c.ink, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            }
        }
        UpdateEntry()
        var showLog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
        AnjazCard(Modifier.fillMaxWidth(), onClick = { showLog = true }) {
            Row(Modifier.heightIn(min = 44.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                AnjazIcon(AnjazIcons.Log, c.ink)
                Text("ما الجديد", color = c.ink, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            }
        }
        if (showLog) com.anjaz.app.ui.about.ChangelogSheet { showLog = false }
        upcoming.forEach { e ->
            AnjazCard(Modifier.fillMaxWidth()) {
                Row(Modifier.heightIn(min = 44.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    AnjazIcon(e.icon, c.mute)
                    Text(e.title, color = c.mute, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Tag("قريباً", Tone.Neutral)
                }
            }
        }
        com.anjaz.app.ui.about.AboutCard(Modifier.padding(top = 18.dp))
        Text(
            "${BrandConfig.NAME} ${ltr(BuildConfig.VERSION_NAME)}",
            color = c.mute, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp).combinedClickable(
                onClick = {}, onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onOpenSelfTest() },
            ),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@androidx.compose.runtime.Composable
private fun UpdateEntry(vm: com.anjaz.app.update.UpdateViewModel = androidx.hilt.navigation.compose.hiltViewModel()) {
    val c = Anjaz.colors
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var status by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    var busy by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    AnjazCard(Modifier.fillMaxWidth(), onClick = {
        if (busy) return@AnjazCard
        busy = true
        scope.launch {
            status = when (val r = vm.manager.check(force = true)) {
                is com.anjaz.app.update.UpdateChecker.Result.Available -> { vm.manager.showSheet(true); "نسخة ${ltr(r.release.version)} متاحة" }
                is com.anjaz.app.update.UpdateChecker.Result.UpToDate -> "أنت على أحدث إصدار"
                else -> "تعذّر الفحص، تحقق من الاتصال"
            }
            busy = false
        }
    }) {
        Row(Modifier.heightIn(min = 44.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AnjazIcon(AnjazIcons.Cloud, c.ink)
            Column(Modifier.weight(1f)) {
                Text("التحديثات", color = c.ink, style = MaterialTheme.typography.titleMedium)
                Text(if (busy) "جارٍ الفحص…" else status ?: "فحص النسخة الجديدة", color = c.mute, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
