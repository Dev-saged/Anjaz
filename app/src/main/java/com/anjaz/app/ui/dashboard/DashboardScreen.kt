package com.anjaz.app.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anjaz.app.domain.Dashboard
import com.anjaz.app.domain.Slice
import com.anjaz.app.ui.components.AnjazCard
import com.anjaz.app.ui.components.AnjazIcon
import com.anjaz.app.ui.components.ScreenTitle
import com.anjaz.app.ui.icons.AnjazIcons
import com.anjaz.app.ui.theme.Anjaz
import com.anjaz.app.ui.theme.ltr

@Composable
fun DashboardScreen(onBack: () -> Unit, vm: DashboardViewModel = hiltViewModel()) {
    val c = Anjaz.colors
    val ui by vm.ui.collectAsStateWithLifecycle()
    val reminded by vm.reminded.collectAsStateWithLifecycle()
    val canRemind by vm.canRemind.collectAsStateWithLifecycle()
    val d = ui
    Column(Modifier.fillMaxSize().background(c.surface).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onBack).semantics { contentDescription = "رجوع" }, contentAlignment = Alignment.Center) { AnjazIcon(AnjazIcons.Back, c.ink) }
            ScreenTitle(d?.title ?: "لوحة المتابعة")
        }
        if (d == null) return@Column
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp, 4.dp, 18.dp, 120.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Summary(d.totals) }
            if (d.slices.isEmpty()) item { Text("لا توجد مهام ضمن نطاقك بعد", color = c.mute, style = MaterialTheme.typography.bodyMedium) }
            items(d.slices, key = { it.id }) { s -> SliceCard(s) }
            if (d.overdue.isNotEmpty()) {
                item { Text("متأخرة تحتاج تصعيدًا", color = c.redDeep, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
                items(d.overdue.take(30), key = { "o" + it.id }) { t ->
                    AnjazCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(t.title, color = c.ink, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                                Text(com.anjaz.app.core.DayMath.dueLabel(t.deadline, System.currentTimeMillis()) ?: "", color = c.redDeep, style = MaterialTheme.typography.bodySmall)
                            }
                            if (canRemind) Text(
                                if (t.id in reminded) "تم التذكير" else "تذكير",
                                color = if (t.id in reminded) c.mute else c.emeraldDeep, style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.clip(CircleShape).clickable(enabled = t.id !in reminded) { vm.remind(t.id) }.padding(horizontal = 12.dp, vertical = 12.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Summary(t: Slice) {
    val c = Anjaz.colors
    AnjazCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Ring(t.rate, c.emerald, 72.dp)
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween) {
                Kpi("مفتوحة", t.open, c.ink); Kpi("متأخرة", t.overdue, if (t.overdue > 0) c.redDeep else c.mute)
                Kpi("اعتماد", t.review, c.amber); Kpi("منجزة", t.done, c.emeraldDeep)
            }
        }
    }
}

@Composable
private fun SliceCard(s: Slice) {
    val c = Anjaz.colors
    AnjazCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(s.name, color = c.ink, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(ltr("${(s.rate * 100).toInt()}") + "٪", color = c.mute, style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"))
            }
            Bar(s)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Kpi("مفتوحة", s.open, c.ink); Kpi("متأخرة", s.overdue, if (s.overdue > 0) c.redDeep else c.mute)
                Kpi("اعتماد", s.review, c.amber); Kpi("منجزة", s.done, c.emeraldDeep)
            }
        }
    }
}

@Composable
private fun Kpi(label: String, n: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(n.toString(), color = color, style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"))
        Text(label, color = Anjaz.colors.mute, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun Bar(s: Slice) {
    val c = Anjaz.colors
    Canvas(Modifier.fillMaxWidth().height(8.dp).semantics { contentDescription = "نسبة الإنجاز" }) {
        val h = size.height; val total = maxOf(1, s.total).toFloat()
        drawRoundRect(c.line, size = Size(size.width, h), cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2))
        var x = 0f
        listOf(s.done to c.emerald, s.review to c.amber, s.open to c.navy).forEach { (n, col) ->
            val w = size.width * n / total
            if (w > 0) { drawRoundRect(col, Offset(x, 0f), Size(w, h), androidx.compose.ui.geometry.CornerRadius(h / 2)); x += w }
        }
    }
}

@Composable
private fun Ring(progress: Float, color: Color, size: androidx.compose.ui.unit.Dp) {
    val c = Anjaz.colors
    Box(Modifier.size(size).semantics { contentDescription = "نسبة الإنجاز الكلية" }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val st = Stroke(8.dp.toPx(), cap = StrokeCap.Round)
            drawArc(c.line, 0f, 360f, false, style = st)
            drawArc(color, -90f, 360f * progress, false, style = st)
        }
        Text("${(progress * 100).toInt()}", color = c.ink, style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"))
    }
}
