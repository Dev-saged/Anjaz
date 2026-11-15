package com.anjaz.app.ui.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.anjaz.app.ui.components.AnjazIcon
import com.anjaz.app.ui.components.rememberReduceMotion
import com.anjaz.app.ui.icons.AnjazIcons
import com.anjaz.app.ui.theme.Anjaz
import com.anjaz.app.ui.theme.Display
import com.anjaz.app.ui.theme.ltr

/** بطاقة الخريطة الحيّة: شبكة خرائطية بنبضات تتحرك، وضغطة واحدة تفتح الخريطة. */
@Composable
fun MapCard(onClick: () -> Unit) {
    val c = Anjaz.colors
    val reduce = rememberReduceMotion()
    val t by rememberInfiniteTransition(label = "map").animateFloat(
        0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart), label = "t",
    )
    val shape = RoundedCornerShape(26.dp)
    Box(
        Modifier.fillMaxWidth().heightIn(min = 132.dp).clip(shape)
            .background(Brush.linearGradient(listOf(c.navy, c.navyHigh)))
            .border(BorderStroke(1.dp, c.emerald.copy(alpha = .45f)), shape)
            .clickable(onClick = onClick)
            .semantics { role = Role.Button; contentDescription = "فتح الخريطة الحيّة" },
    ) {
        Canvas(Modifier.matchParentSize()) {
            val step = 28.dp.toPx()
            var x = 0f
            while (x < size.width) { drawLine(c.onNavy.copy(alpha = .06f), Offset(x, 0f), Offset(x, size.height), 1f); x += step }
            var y = 0f
            while (y < size.height) { drawLine(c.onNavy.copy(alpha = .06f), Offset(0f, y), Offset(size.width, y), 1f); y += step }
            val dots = listOf(.18f to .30f, .42f to .72f, .30f to .52f)
            dots.forEachIndexed { i, (fx, fy) ->
                val p = if (reduce) .4f else (t + i / 3f) % 1f
                val o = Offset(size.width * fx, size.height * fy)
                drawCircle(c.emerald.copy(alpha = (1f - p) * .55f), 6.dp.toPx() + p * 26.dp.toPx(), o, style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
                drawCircle(c.emerald, 4.dp.toPx(), o)
            }
        }
        Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("الخريطة الحيّة", color = c.onNavy, fontFamily = Display, style = MaterialTheme.typography.headlineMedium)
                Text("الأشخاص والنقاط والمساكن والبلاغات والإنجازات لحظة بلحظة", color = c.onNavy.copy(.8f), style = MaterialTheme.typography.bodySmall)
            }
            Box(Modifier.size(56.dp).clip(CircleShape).background(c.emerald), contentAlignment = Alignment.Center) {
                AnjazIcon(AnjazIcons.Map, c.onEmerald, 28.dp)
            }
        }
    }
}

/** بطاقتان بارزتان للساحات والمخيمات بعدّاد حي، تفتحان الدليل. */
@Composable
fun PlacesRow(squares: Int, camps: Int, onOpen: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PlaceTile("المخيمات", camps, AnjazIcons.Camp, onOpen, Modifier.weight(1f))
        PlaceTile("الساحات", squares, AnjazIcons.Plot, onOpen, Modifier.weight(1f))
    }
}

@Composable
private fun PlaceTile(label: String, n: Int, icon: ImageVector, onClick: () -> Unit, modifier: Modifier) {
    val c = Anjaz.colors
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier.fillMaxHeight().heightIn(min = 76.dp).clip(shape).background(c.card).border(BorderStroke(1.5.dp, c.emerald.copy(alpha = .55f)), shape)
            .clickable(onClick = onClick).semantics { role = Role.Button }.padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(c.emerald.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
            AnjazIcon(icon, c.emeraldDeep, 26.dp)
        }
        Column(Modifier.weight(1f)) {
            Text(ltr(n.toString()), color = if (c.dark) c.ink else c.navy, fontFamily = Display, style = MaterialTheme.typography.headlineMedium)
            Text(label, color = c.mute, style = MaterialTheme.typography.labelMedium)
        }
    }
}
