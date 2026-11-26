@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.anjaz.app.ui.stats

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anjaz.app.domain.Plural
import com.anjaz.app.domain.Weights
import com.anjaz.app.ui.components.AnjazIcon
import com.anjaz.app.ui.components.EmptyState
import com.anjaz.app.ui.components.FitText
import com.anjaz.app.ui.components.QuietButton
import com.anjaz.app.ui.components.ScreenTitle
import com.anjaz.app.ui.components.rememberReduceMotion
import com.anjaz.app.ui.icons.AnjazIcons
import com.anjaz.app.ui.theme.Anjaz
import com.anjaz.app.ui.theme.Display
import com.anjaz.app.ui.theme.ltr

private fun num(n: Int) = ltr(Plural.grouped(n))

@Composable
fun StatsScreen(onBack: () -> Unit, onManage: () -> Unit, onDistribute: () -> Unit = {}, vm: StatsViewModel = hiltViewModel()) {
    val c = Anjaz.colors
    val ui by vm.ui.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf<SquareStat?>(null) }
    Column(Modifier.fillMaxSize().background(c.surface).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onBack).semantics { contentDescription = "رجوع" }, contentAlignment = Alignment.Center) {
                AnjazIcon(AnjazIcons.Back, c.ink)
            }
            ScreenTitle("إحصائيات المنطقة", Modifier.weight(1f))
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 158.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 40.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (ui.regions.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ui.regions, key = { it.id ?: "all" }) { r ->
                        val on = r.id == ui.region
                        Text(
                            r.name, color = if (on) c.onNavy else c.ink, style = MaterialTheme.typography.labelLarge, maxLines = 1,
                            modifier = Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(14.dp)).background(if (on) c.navy else c.card)
                                .border(BorderStroke(1.dp, if (on) c.navy else c.line), RoundedCornerShape(14.dp))
                                .clickable { vm.pickRegion(r.id) }.padding(horizontal = 16.dp, vertical = 11.dp),
                        )
                    }
                }
            }
            item {
                val tile = com.anjaz.app.BrandConfig.Palette.emeraldTile
                HeroTile("إجمالي المخيمات", num(ui.camps), "في " + Plural.of(ui.squares.size, Plural.square) { ltr(it.toString()) }, AnjazIcons.Camp, listOf(tile.light.toColor(), tile.dark.toColor()), Color.White)
            }
            item { HeroTile("إجمالي الأسر", num(ui.families), if (ui.camps > 0) "متوسط ${num(ui.average)} لكل مخيم" else "—", AnjazIcons.Users, listOf(c.navyHigh, c.navy), Color.White) }
            if (ui.families > 0) item(span = { GridItemSpan(maxLineSpan) }) {
                com.anjaz.app.ui.components.PrimaryButton("توزيع كمية واردة حسب الأوزان", onDistribute)
            }
            if (ui.missing > 0) item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "${Plural.of(ui.missing, Plural.camp) { ltr(it.toString()) }} بلا عدد أسر مسجّل — اضغط على المخيم في المربعات لإكماله",
                    color = c.amberDeep, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.amber.copy(alpha = .14f)).clickable(onClick = onManage).padding(12.dp),
                )
            }
            if (ui.loaded && ui.squares.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyState(AnjazIcons.Map, "لا توجد مربعات بعد", "أضف المربعات والمخيمات لتظهر إحصائياتها هنا")
            }
            items(ui.squares, key = { it.square.id }) { s -> SquareTile(s, ui.maxFamilies, ui.families) { open = s } }
            item(span = { GridItemSpan(maxLineSpan) }) { QuietButton("إدارة المربعات والمخيمات", onClick = onManage) }
        }
    }
    open?.let { s -> CampsSheet(s) { open = null } }
}

@Composable
private fun HeroTile(label: String, value: String, sub: String, icon: androidx.compose.ui.graphics.vector.ImageVector, colors: List<Color>, on: Color) {
    Column(
        Modifier.fillMaxWidth().heightIn(min = 128.dp).clip(RoundedCornerShape(22.dp)).background(Brush.linearGradient(colors)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AnjazIcon(icon, on, 22.dp)
            FitText(label, on.copy(alpha = .9f), MaterialTheme.typography.labelLarge, Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Start)
        }
        FitText(value, on, MaterialTheme.typography.displayLarge.copy(fontFamily = Display), Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Start)
        Text(sub, color = on.copy(alpha = .85f), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SquareTile(s: SquareStat, max: Int, total: Int, onClick: () -> Unit) {
    val c = Anjaz.colors
    val reduce = rememberReduceMotion()
    val share = remember { Animatable(0f) }
    val target = if (max == 0) 0f else s.families.toFloat() / max
    LaunchedEffect(target) { if (reduce) share.snapTo(target) else share.animateTo(target, tween(700)) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.card).border(BorderStroke(1.dp, c.line), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick).semantics { role = Role.Button; contentDescription = "${s.square.name}، اضغط لعرض المخيمات" }.padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(s.square.name, color = if (c.dark) c.ink else c.navy, style = MaterialTheme.typography.titleLarge, maxLines = 2)
        s.square.managerName?.takeIf { it.isNotBlank() }?.let { Text(it, color = c.mute, style = MaterialTheme.typography.bodySmall, maxLines = 1) }
        if (s.subs.isNotEmpty()) Stat("مربعات فرعية", num(s.subs.size))
        Stat("المخيمات", num(s.camps.size))
        Stat("الأسر", num(s.families))
        Canvas(Modifier.fillMaxWidth().height(8.dp)) {
            val r = CornerRadius(size.height / 2)
            drawRoundRect(c.line, cornerRadius = r)
            drawRoundRect(c.emerald, size = Size(size.width * share.value, size.height), cornerRadius = r,
                topLeft = androidx.compose.ui.geometry.Offset(size.width * (1 - share.value), 0f))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("الوزن النسبي", color = c.mute, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            Text(ltr(Weights.percent(s.families, total)), color = c.emeraldDeep, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    val c = Anjaz.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = c.ink, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, color = c.ink, style = MaterialTheme.typography.titleMedium, maxLines = 1)
    }
}

@Composable
private fun CampsSheet(s: SquareStat, onDismiss: () -> Unit) {
    val c = Anjaz.colors
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.surface) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ScreenTitle(s.square.name)
            Text(Plural.of(s.camps.size, Plural.camp) { ltr(it.toString()) } + " · " + Plural.of(s.families, Plural.family) { num(it) }, color = c.mute, style = MaterialTheme.typography.bodyMedium)
            if (s.camps.isEmpty()) Text("لا مخيمات في هذا المربع بعد", color = c.mute, style = MaterialTheme.typography.bodyMedium)
            val max = s.camps.maxOfOrNull { it.families ?: 0 }?.coerceAtLeast(1) ?: 1
            s.own.forEach { camp -> CampBar(camp, max) }
            s.subs.forEach { sub ->
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("مربع فرعي: " + sub.square.name, color = c.ink, style = MaterialTheme.typography.titleMedium)
                        Text(Plural.of(sub.camps.size, Plural.camp) { ltr(it.toString()) } + " · " + Plural.of(sub.families, Plural.family) { num(it) }, color = c.mute, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(ltr(Weights.percent(sub.families, s.families)), color = c.emeraldDeep, style = MaterialTheme.typography.titleMedium)
                }
                sub.camps.sortedByDescending { it.families ?: -1 }.forEach { camp -> CampBar(camp, max) }
            }
        }
    }
}

@Composable
private fun CampBar(camp: com.anjaz.app.data.db.CampEntity, max: Int) {
    val c = Anjaz.colors
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.card).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AnjazIcon(AnjazIcons.Camp, c.emeraldDeep, 20.dp)
            Column(Modifier.weight(1f)) {
                Text(camp.name, color = c.ink, style = MaterialTheme.typography.titleMedium)
                camp.managerName?.takeIf { it.isNotBlank() }?.let { Text(it, color = c.mute, style = MaterialTheme.typography.bodySmall) }
            }
            Text(camp.families?.let { num(it) + " أسرة" } ?: "غير مسجّل", color = if (camp.families == null) c.amberDeep else c.ink, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
        val f = (camp.families ?: 0).toFloat() / max
        Canvas(Modifier.fillMaxWidth().height(6.dp)) {
            val r = CornerRadius(size.height / 2)
            drawRoundRect(c.line, cornerRadius = r)
            drawRoundRect(c.emerald, size = Size(size.width * f, size.height), cornerRadius = r, topLeft = androidx.compose.ui.geometry.Offset(size.width * (1 - f), 0f))
        }
    }
}
