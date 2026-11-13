package com.anjaz.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.anjaz.app.BrandConfig.Palette

@Immutable
data class AnjazColors(
    val navy: Color, val navyHigh: Color, val emerald: Color, val emeraldDeep: Color,
    val amber: Color, val amberDeep: Color, val red: Color, val redDeep: Color,
    val surface: Color, val card: Color, val ink: Color, val mute: Color, val line: Color,
    val onNavy: Color, val onEmerald: Color, val dark: Boolean,
) {
    fun tint(base: Color, alpha: Float = 0.14f): Color = base.copy(alpha = alpha)
}

private fun colors(dark: Boolean): AnjazColors {
    fun pick(p: com.anjaz.app.BrandConfig.Pair): Color = (if (dark) p.dark else p.light).toColor()
    return AnjazColors(
        navy = pick(Palette.navy), navyHigh = pick(Palette.navyHigh), emerald = pick(Palette.emerald),
        emeraldDeep = pick(Palette.emeraldDeep), amber = pick(Palette.amber), amberDeep = pick(Palette.amberDeep),
        red = pick(Palette.red), redDeep = pick(Palette.redDeep), surface = pick(Palette.surface),
        card = pick(Palette.card), ink = pick(Palette.ink), mute = pick(Palette.mute), line = pick(Palette.line),
        onNavy = pick(Palette.onNavy), onEmerald = pick(Palette.onEmerald), dark = dark,
    )
}

val LocalAnjazColors = staticCompositionLocalOf { colors(false) }

object Anjaz {
    val colors: AnjazColors
        @Composable get() = LocalAnjazColors.current
}

@Composable
fun AnjazTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val c = colors(dark)
    val scheme = if (dark) {
        darkColorScheme(primary = c.emerald, onPrimary = c.onEmerald, background = c.surface, surface = c.surface,
            onSurface = c.ink, surfaceContainer = c.card, surfaceContainerHigh = c.card, error = c.red)
    } else {
        lightColorScheme(primary = c.navy, onPrimary = c.onNavy, background = c.surface, surface = c.surface,
            onSurface = c.ink, surfaceContainer = c.card, surfaceContainerHigh = c.card, error = c.red)
    }
    CompositionLocalProvider(
        LocalAnjazColors provides c,
        LocalLayoutDirection provides LayoutDirection.Rtl,
    ) {
        MaterialTheme(colorScheme = scheme, typography = AnjazTypography, content = content)
    }
}

