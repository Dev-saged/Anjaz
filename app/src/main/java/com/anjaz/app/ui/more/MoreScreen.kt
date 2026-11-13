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
import com.anjaz.app.ui.icons.AnjazIcons
import com.anjaz.app.ui.theme.Anjaz
import com.anjaz.app.ui.theme.ltr

private data class Entry(val title: String, val icon: ImageVector)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MoreScreen(profile: Profile, onSignOut: () -> Unit, onOpenSelfTest: () -> Unit) {
    val c = Anjaz.colors
    val haptic = LocalHapticFeedback.current
    val upcoming = listOf(
        Entry("المناطق والمربعات والمخيمات", AnjazIcons.Map), Entry("التقارير والتصدير", AnjazIcons.Report),
        Entry("النسخ الاحتياطي", AnjazIcons.Cloud), Entry("سجل العمليات", AnjazIcons.Log), Entry("الأمان والحساب", AnjazIcons.Lock),
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
        upcoming.forEach { e ->
            AnjazCard(Modifier.fillMaxWidth()) {
                Row(Modifier.heightIn(min = 44.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    AnjazIcon(e.icon, c.mute)
                    Text(e.title, color = c.mute, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Tag("قريباً", Tone.Neutral)
                }
            }
        }
        Text(
            "${BrandConfig.NAME} ${ltr(BuildConfig.VERSION_NAME)}",
            color = c.mute, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth().padding(top = 18.dp).combinedClickable(
                onClick = {}, onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onOpenSelfTest() },
            ),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
