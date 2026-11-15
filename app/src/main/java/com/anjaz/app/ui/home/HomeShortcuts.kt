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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
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

/** بطاقة الخريطة الحيّة: رادار يدور ومسار متقطع متحرك ونبضات، وتتفاعل مع اللمس. */
@Composable
fun MapCard(onClick: () -> Unit) {
    val c = Anjaz.colors
    val reduce = rememberReduceMotion()
    val tr = rememberInfiniteTransition(label = "map")
    val t by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(2800, easing = LinearEasing), RepeatMode.Restart), label = "t")
    val spin by tr.animateFloat(0f, 360f, infiniteRepeatable(tween(5200, easing = LinearEasing), RepeatMode.Restart), label = "spin")
    val live by tr.animateFloat(.35f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "live")
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pressed) .97f else 1f, spring(.6f, 500f), label = "press")
    val shape = RoundedCornerShape(26.dp)
    Box(
        Modifier.fillMaxWidth().heightIn(min = 148.dp).graphicsLayer { scaleX = sc; scaleY = sc }.clip(shape)
            .background(Brush.linearGradient(listOf(c.navy, c.navyHigh)))
            .border(BorderStroke(1.5.dp, Brush.linearGradient(listOf(c.emerald, c.emerald.copy(alpha = .15f), c.emerald))), shape)
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .semantics { role = Role.Button; contentDescription = "فتح الخريطة الحيّة" },
    ) {
        Canvas(Modifier.matchParentSize()) {
            val step = 26.dp.toPx()
            var x = 0f
            while (x < size.width) { drawLine(c.onNavy.copy(alpha = .06f), Offset(x, 0f), Offset(x, size.height), 1f); x += step }
            var y = 0f
            while (y < size.height) { drawLine(c.onNavy.copy(alpha = .06f), Offset(0f, y), Offset(size.width, y), 1f); y += step }
            val pts = listOf(Offset(size.width * .12f, size.height * .72f), Offset(size.width * .30f, size.height * .38f), Offset(size.width * .46f, size.height * .70f), Offset(size.width * .60f, size.height * .30f))
            val route = Path().apply {
                moveTo(pts[0].x, pts[0].y)
                for (i in 1 until pts.size) {
                    val a = pts[i - 1]; val b = pts[i]
                    cubicTo(a.x + (b.x - a.x) * .5f, a.y, a.x + (b.x - a.x) * .5f, b.y, b.x, b.y)
                }
            }
            drawPath(route, c.emerald.copy(alpha = .55f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 8.dp.toPx()), if (reduce) 0f else -t * 36.dp.toPx())))
            val hub = Offset(size.width * .30f, size.height * .38f)
            if (!reduce) rotate(spin, hub) {
                drawArc(
                    Brush.sweepGradient(listOf(Color.Transparent, c.emerald.copy(alpha = .35f)), hub), -50f, 50f, true,
                    Offset(hub.x - 90.dp.toPx(), hub.y - 90.dp.toPx()), Size(180.dp.toPx(), 180.dp.toPx()),
                )
            }
            pts.forEachIndexed { i, o ->
                val p = if (reduce) .4f else (t + i * .25f) % 1f
                drawCircle(c.emerald.copy(alpha = (1f - p) * .55f), 6.dp.toPx() + p * 28.dp.toPx(), o, style = Stroke(2f))
                drawCircle(if (i == 3) c.amber else c.emerald, 4.5.dp.toPx(), o)
            }
        }
        Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    Modifier.clip(CircleShape).background(c.emerald.copy(alpha = .18f)).padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(c.emerald.copy(alpha = if (reduce) 1f else live)))
                    Text("مباشر", color = c.emerald, style = MaterialTheme.typography.labelMedium)
                }
                Text("الخريطة الحيّة", color = c.onNavy, fontFamily = Display, style = MaterialTheme.typography.headlineMedium)
                Text("الأشخاص والنقاط والمساكن والبلاغات والإنجازات لحظة بلحظة", color = c.onNavy.copy(.82f), style = MaterialTheme.typography.bodySmall)
            }
            Box(Modifier.size(58.dp).clip(CircleShape).background(c.emerald), contentAlignment = Alignment.Center) {
                AnjazIcon(AnjazIcons.Map, c.onEmerald, 30.dp)
            }
        }
    }
}

/** بطاقة المخيمات الواحدة: العدّاد الكبير، وتحته عدد الساحات؛ تفتح الدليل. */
@Composable
fun CampsCard(camps: Int, squares: Int, families: Int, onOpen: () -> Unit) {
    val c = Anjaz.colors
    val shape = RoundedCornerShape(22.dp)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 84.dp).clip(shape).background(c.card)
            .border(BorderStroke(1.5.dp, Brush.horizontalGradient(listOf(c.emerald, c.emerald.copy(alpha = .25f)))), shape)
            .clickable(onClick = onOpen).semantics { role = Role.Button; contentDescription = "إحصائيات المنطقة" }.padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(Brush.linearGradient(listOf(c.emerald.copy(alpha = .28f), c.emerald.copy(alpha = .1f)))), contentAlignment = Alignment.Center) {
            AnjazIcon(AnjazIcons.Camp, c.emeraldDeep, 32.dp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("المخيمات والأسر", color = c.ink, style = MaterialTheme.typography.titleLarge)
            Text(
                "في " + com.anjaz.app.domain.Plural.of(squares, com.anjaz.app.domain.Plural.square) { ltr(it.toString()) } +
                    " · " + com.anjaz.app.domain.Plural.of(families, com.anjaz.app.domain.Plural.family) { ltr(com.anjaz.app.domain.Plural.grouped(it)) },
                color = c.mute, style = MaterialTheme.typography.bodySmall,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            com.anjaz.app.ui.components.FitText(ltr(camps.toString()), if (c.dark) c.ink else c.navy, MaterialTheme.typography.displayLarge.copy(fontFamily = Display), Modifier.widthIn(max = 96.dp))
            Text("مخيم", color = c.mute, style = MaterialTheme.typography.labelSmall)
        }
    }
}
