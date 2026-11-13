package com.anjaz.app.ui.components

import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun rememberReduceMotion(): Boolean {
    val ctx = LocalContext.current
    return remember {
        Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

@Composable
fun Stagger(index: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val reduce = rememberReduceMotion()
    var shown by remember { mutableStateOf(reduce) }
    LaunchedEffect(Unit) { if (!reduce) delay(index * 70L); shown = true }
    val a by animateFloatAsState(
        if (shown) 1f else 0f, tween(if (reduce) 0 else 460, easing = FastOutSlowInEasing), label = "stagger",
    )
    Box(modifier.graphicsLayer { alpha = a; translationY = (1f - a) * 26.dp.toPx() }) { content() }
}

@Composable
fun AmbientBackground(a: Color, b: Color, modifier: Modifier = Modifier.fillMaxSize()) {
    val reduce = rememberReduceMotion()
    val t = rememberInfiniteTransition(label = "ambient")
    val phase by if (reduce) remember { mutableStateOf(0f) } else t.animateFloat(
        0f, 1f, infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Reverse), label = "phase",
    )
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawCircle(
            Brush.radialGradient(listOf(a, Color.Transparent), Offset(w * (.15f + .25f * phase), h * .08f), w * .7f),
            radius = w * .7f, center = Offset(w * (.15f + .25f * phase), h * .08f),
        )
        drawCircle(
            Brush.radialGradient(listOf(b, Color.Transparent), Offset(w * (.95f - .2f * phase), h * .32f), w * .6f),
            radius = w * .6f, center = Offset(w * (.95f - .2f * phase), h * .32f),
        )
    }
}
