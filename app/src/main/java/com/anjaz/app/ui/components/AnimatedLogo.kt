package com.anjaz.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlinx.coroutines.launch

/** شعار إنجاز: الحلقة تُرسم دورة كاملة ثم يقفز المربع الأخضر إلى فجوتها بنبضة؛ يُعرض ثابتاً عند "إزالة الحركة". */
@Composable
fun AnimatedLogo(ring: Color, square: Color, modifier: Modifier = Modifier, animate: Boolean = true, onDone: () -> Unit = {}) {
    val sweep = remember { Animatable(if (animate) 0f else 1f) }
    val pop = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(animate) {
        if (!animate) { onDone(); return@LaunchedEffect }
        sweep.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
        pop.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
        onDone()
    }
    Canvas(modifier) {
        val u = size.minDimension / 256f
        val stroke = 32f * u
        val r = 84f * u
        val center = Offset(size.width / 2, size.height / 2)
        drawArc(ring, 26f, 288f * sweep.value, false, Offset(center.x - r, center.y - r), Size(r * 2, r * 2), style = Stroke(stroke, cap = StrokeCap.Round))
        val side = 40f * u * pop.value
        val c = Offset(center.x + 76.5f * u, center.y - 11.8f * u)
        rotate(-8f, c) { drawRoundRect(square, Offset(c.x - side / 2, c.y - side / 2), Size(side, side), CornerRadius(11f * u)) }
    }
}

/** شاشة بداية قصيرة فوق التطبيق: الشعار المتحرك ثم تلاشٍ سلس؛ تُعرض مرة لكل تشغيل. */
@Composable
fun SplashOverlay() {
    val c = com.anjaz.app.ui.theme.Anjaz.colors
    val reduce = rememberReduceMotion()
    var visible by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(true) }
    val alpha = remember { Animatable(1f) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    if (!visible) return
    androidx.compose.foundation.layout.Box(
        Modifier.fillMaxSize().androidxAlpha(alpha.value).androidxBg(c.surface), contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        androidx.compose.foundation.layout.Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
            AnimatedLogo(if (c.dark) c.ink else c.navy, c.emerald, Modifier.androidxSize(120), animate = !reduce) {
                scope.launch { kotlinx.coroutines.delay(250); alpha.animateTo(0f, tween(350)); visible = false }
            }
            androidx.compose.material3.Text(com.anjaz.app.BrandConfig.NAME, color = if (c.dark) c.ink else c.navy, fontFamily = com.anjaz.app.ui.theme.Display,
                style = androidx.compose.material3.MaterialTheme.typography.displayMedium, modifier = Modifier.androidxPad())
        }
    }
}

private fun Modifier.androidxAlpha(a: Float) = this.then(Modifier.graphicsLayer { alpha = a })
private fun Modifier.androidxBg(c: Color) = this.then(Modifier.background(c))
private fun Modifier.androidxSize(d: Int) = this.then(Modifier.size(d.dp))
private fun Modifier.androidxPad() = this.then(Modifier.padding(top = 14.dp))
