package com.anjaz.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anjaz.app.BrandConfig
import com.anjaz.app.data.db.TaskEntity
import com.anjaz.app.ui.components.AmbientBackground
import com.anjaz.app.ui.components.AnjazCard
import com.anjaz.app.ui.components.AnjazIcon
import com.anjaz.app.ui.components.DayStrip
import com.anjaz.app.ui.components.KpiCard
import com.anjaz.app.ui.components.ProgressRing
import com.anjaz.app.ui.components.SectionTitle
import com.anjaz.app.ui.components.Stagger
import com.anjaz.app.ui.components.TaskCard
import com.anjaz.app.ui.components.Tone
import com.anjaz.app.ui.icons.AnjazIcons
import com.anjaz.app.ui.theme.Anjaz
import com.anjaz.app.ui.theme.ReemKufi
import com.anjaz.app.ui.theme.ltr

@Composable
fun HomeScreen(onOpenTask: (TaskEntity) -> Unit, onNewTask: () -> Unit, vm: HomeViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val c = Anjaz.colors
    Box(Modifier.fillMaxSize().background(c.surface)) {
        AmbientBackground(c.emerald.copy(alpha = .16f), c.navyHigh.copy(alpha = .14f))
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { Stagger(0) { Header(s.unsynced) } }
            item { Stagger(1) { Hero(s.counts.done, s.counts.total, s.counts.progress) } }
            item {
                Stagger(2) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        KpiCard(ltr(s.counts.inProgress.toString()), "قيد المتابعة", Tone.Neutral, Modifier.weight(1f))
                        KpiCard(ltr(s.counts.awaiting.toString()), "بانتظار الاعتماد", Tone.Warning, Modifier.weight(1f))
                        KpiCard(ltr(s.counts.overdue.toString()), "متأخرة", Tone.Danger, Modifier.weight(1f))
                    }
                }
            }
            item { Stagger(3) { DayStrip(s.days, s.selectedDay, vm::select) } }
            item { SectionTitle(if (s.selectedDay == 0) "مهام اليوم" else "مهام اليوم المحدد", ltr(s.dayTasks.size.toString())) }
            if (s.loading) {
                items(2) { Skeleton() }
            } else if (s.dayTasks.isEmpty()) {
                item { EmptyDay(s.counts.total == 0, onNewTask) }
            } else {
                items(s.dayTasks, key = { it.id }) { t -> TaskCard(t, s.now, { onOpenTask(t) }) }
            }
        }
    }
}

@Composable
private fun Header(unsynced: Int) {
    val c = Anjaz.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text("مرحباً بك", color = c.mute, style = MaterialTheme.typography.bodySmall)
            Text(BrandConfig.NAME, color = if (c.dark) c.ink else c.navy, fontFamily = ReemKufi, style = MaterialTheme.typography.headlineMedium)
        }
        Box(Modifier.size(44.dp).clip(CircleShape).background(c.card), contentAlignment = Alignment.Center) {
            AnjazIcon(AnjazIcons.Bell, c.ink, description = "الإشعارات")
            if (unsynced > 0) Box(Modifier.align(Alignment.TopEnd).padding(10.dp).size(9.dp).clip(CircleShape).background(c.amber))
        }
    }
}

@Composable
private fun Hero(done: Int, total: Int, progress: Float) {
    val c = Anjaz.colors
    val shape = RoundedCornerShape(26.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(Brush.linearGradient(listOf(c.navy, c.navyHigh)))
            .padding(18.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
            ProgressRing(progress, Modifier.size(112.dp), track = c.onNavy.copy(.16f), ring = c.emerald, square = c.emerald)
            Text(ltr("${(progress * 100).toInt()}%"), color = c.onNavy, fontFamily = ReemKufi, style = MaterialTheme.typography.titleLarge)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("إنجاز المهام", color = c.onNavy.copy(.78f), style = MaterialTheme.typography.bodySmall)
            Text("${ltr(done.toString())} من ${ltr(total.toString())}", color = c.onNavy, fontFamily = ReemKufi, style = MaterialTheme.typography.headlineMedium)
            Text(if (total == 0) "لا توجد مهام بعد" else "مهمة معتمدة", color = c.onNavy.copy(.78f), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun EmptyDay(noTasksAtAll: Boolean, onNewTask: () -> Unit) {
    val c = Anjaz.colors
    AnjazCard(Modifier.fillMaxWidth(), if (noTasksAtAll) onNewTask else null) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AnjazIcon(AnjazIcons.Tasks, c.mute, 32.dp)
            Text(if (noTasksAtAll) "ابدأ بإنشاء أول مهمة" else "لا مهام في هذا اليوم", color = c.ink, style = MaterialTheme.typography.titleMedium)
            Text(
                if (noTasksAtAll) "اضغط على الزر الأخضر في الأسفل" else "اختر يوماً آخر من الشريط أعلاه",
                color = c.mute, style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun Skeleton() {
    Box(Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(18.dp)).background(Anjaz.colors.line.copy(.6f)))
}
