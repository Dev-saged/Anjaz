package com.anjaz.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.anjaz.app.ui.theme.Anjaz
import com.anjaz.app.ui.theme.ReemKufi
import kotlin.math.cos
import kotlin.math.sin

enum class Tone { Neutral, Success, Warning, Danger }

@Composable
fun toneColors(tone: Tone): Pair<Color, Color> {
    val c = Anjaz.colors
    return when (tone) {
        Tone.Neutral -> c.mute.copy(alpha = .14f) to c.mute
        Tone.Success -> c.emerald.copy(alpha = .18f) to c.emeraldDeep
        Tone.Warning -> c.amber.copy(alpha = .22f) to c.amberDeep
        Tone.Danger -> c.red.copy(alpha = .16f) to c.redDeep
    }
}

@Composable
fun Tag(text: String, tone: Tone = Tone.Neutral, modifier: Modifier = Modifier) {
    val (bg, fg) = toneColors(tone)
    Text(
        text, color = fg, style = MaterialTheme.typography.labelSmall,
        modifier = modifier.clip(RoundedCornerShape(10.dp)).background(bg).padding(horizontal = 10.dp, vertical = 2.dp),
    )
}

@Composable
fun AnjazCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val c = Anjaz.colors
    val base = modifier.clip(RoundedCornerShape(18.dp)).background(c.card)
        .border(BorderStroke(1.dp, c.line), RoundedCornerShape(18.dp))
    Box(if (onClick != null) base.clickable(onClick = onClick) else base) {
        Box(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) { content() }
    }
}

@Composable
fun AnjazIcon(icon: ImageVector, tint: Color, size: Dp = 24.dp, description: String? = null) {
    Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(size))
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, emerald: Boolean = true, enabled: Boolean = true) {
    val c = Anjaz.colors
    val bg = if (emerald) c.emerald else c.navy
    val fg = if (emerald) c.onEmerald else c.onNavy
    Box(
        modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(18.dp))
            .background(if (enabled) bg else c.line)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { role = Role.Button },
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (enabled) fg else c.mute, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun QuietButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = Anjaz.colors
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier.fillMaxWidth().height(48.dp).clip(shape).border(BorderStroke(1.dp, c.line), shape)
            .clickable(onClick = onClick).semantics { role = Role.Button },
        contentAlignment = Alignment.Center,
    ) { Text(text, color = c.ink, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun KpiCard(value: String, label: String, tone: Tone, modifier: Modifier = Modifier) {
    val c = Anjaz.colors
    val color = when (tone) { Tone.Warning -> c.amberDeep; Tone.Danger -> c.redDeep; else -> if (c.dark) c.ink else c.navy }
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(c.card).border(BorderStroke(1.dp, c.line), RoundedCornerShape(16.dp))
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, color = color, style = MaterialTheme.typography.titleLarge)
        Text(label, color = c.mute, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
    }
}

@Composable
fun ProgressRing(progress: Float, modifier: Modifier = Modifier, stroke: Dp = 12.dp, track: Color, ring: Color, square: Color) {
    Canvas(modifier) {
        val w = stroke.toPx()
        val inset = w / 2 + 6.dp.toPx()
        val sz = Size(size.width - inset * 2, size.height - inset * 2)
        drawArc(track, -90f, 360f, false, Offset(inset, inset), sz, style = Stroke(w))
        val sweep = 360f * progress.coerceIn(0f, 1f)
        if (sweep > 0f) drawArc(ring, -90f, sweep, false, Offset(inset, inset), sz, style = Stroke(w, cap = StrokeCap.Round))
        val r = sz.width / 2
        val a = Math.toRadians((-90f + sweep).toDouble())
        val cx = size.width / 2 + (r * cos(a)).toFloat()
        val cy = size.height / 2 + (r * sin(a)).toFloat()
        val s = w * 1.3f
        rotate(-8f, Offset(cx, cy)) {
            drawRoundRect(square, Offset(cx - s / 2, cy - s / 2), Size(s, s), androidx.compose.ui.geometry.CornerRadius(s * .28f))
        }
    }
}

data class NavItem(val route: String, val label: String, val icon: ImageVector, val badge: Int = 0)

@Composable
fun GlassNavBar(items: List<NavItem>, selected: String, onSelect: (String) -> Unit, onFab: () -> Unit, fabIcon: ImageVector, fabLabel: String, modifier: Modifier = Modifier, fabColor: Color? = null) {
    val c = Anjaz.colors
    Box(modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp).padding(bottom = 10.dp), contentAlignment = Alignment.BottomCenter) {
        val shape = RoundedCornerShape(26.dp)
        Row(
            Modifier.fillMaxWidth().height(64.dp).shadow(14.dp, shape, ambientColor = c.navy.copy(.25f), spotColor = c.navy.copy(.25f))
                .clip(shape).background(c.card.copy(alpha = .94f)).border(BorderStroke(1.dp, c.line), shape).padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically,
        ) {
            val half = items.size / 2
            items.forEachIndexed { i, item ->
                if (i == half) Spacer(Modifier.width(60.dp))
                NavButton(item, item.route == selected) { onSelect(item.route) }
            }
        }
        Box(
            Modifier.padding(bottom = 26.dp).size(64.dp).shadow(14.dp, RoundedCornerShape(22.dp), ambientColor = c.emerald, spotColor = c.emerald)
                .clip(RoundedCornerShape(22.dp)).background(fabColor ?: c.emerald).clickable(onClick = onFab)
                .semantics { contentDescription = fabLabel; role = Role.Button },
            contentAlignment = Alignment.Center,
        ) { AnjazIcon(fabIcon, c.onEmerald, 28.dp) }
    }
}

@Composable
private fun NavButton(item: NavItem, selected: Boolean, onClick: () -> Unit) {
    val c = Anjaz.colors
    val tint = if (selected) (if (c.dark) c.ink else c.navy) else c.mute
    Column(
        Modifier.width(58.dp).height(56.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick)
            .semantics { role = Role.Tab; contentDescription = item.label },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Box {
            AnjazIcon(item.icon, tint)
            if (item.badge > 0) {
                Text(
                    item.badge.toString(), color = Color.White, style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.TopEnd).padding(end = 0.dp).clip(RoundedCornerShape(8.dp)).background(c.red).padding(horizontal = 4.dp),
                )
            }
        }
        Text(item.label, color = tint, style = MaterialTheme.typography.labelSmall, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
        Box(Modifier.padding(top = 1.dp).width(if (selected) 18.dp else 0.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(c.emerald))
    }
}

@Composable
fun SectionTitle(title: String, trailing: String? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        Text(title, color = Anjaz.colors.ink, style = MaterialTheme.typography.titleMedium)
        if (trailing != null) Text(trailing, color = Anjaz.colors.mute, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, color = Anjaz.colors.navy.let { if (Anjaz.colors.dark) Anjaz.colors.ink else it }, fontFamily = ReemKufi, style = MaterialTheme.typography.titleLarge, modifier = modifier)
}
