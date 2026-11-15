package com.anjaz.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.anjaz.app.R

val Display = FontFamily(Font(R.font.alexandria_semibold, FontWeight.SemiBold), Font(R.font.alexandria_bold, FontWeight.Bold))

val PlexArabic = FontFamily(
    Font(R.font.plex_ar_regular, FontWeight.Normal),
    Font(R.font.plex_ar_medium, FontWeight.Medium),
    Font(R.font.plex_ar_semibold, FontWeight.SemiBold),
)

private const val TNUM = "tnum"

// الخطوط العربية لها صواعد ونوازل طويلة: ارتفاع سطر واسع بلا قصّ، وحشوة الخط مفعّلة حتى لا تُقصّ الحروف.
private val Roomy = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
private val Padded = PlatformTextStyle(includeFontPadding = true)

private fun style(family: FontFamily, weight: FontWeight, size: Int, line: Int, tnum: Boolean = false) = TextStyle(
    fontFamily = family, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp,
    fontFeatureSettings = if (tnum) TNUM else null, lineHeightStyle = Roomy, platformStyle = Padded,
)

val AnjazTypography = Typography(
    displayLarge = style(Display, FontWeight.SemiBold, 34, 50, tnum = true),
    headlineMedium = style(Display, FontWeight.SemiBold, 24, 38),
    titleLarge = style(Display, FontWeight.SemiBold, 20, 32, tnum = true),
    titleMedium = style(PlexArabic, FontWeight.SemiBold, 15, 24),
    bodyLarge = style(PlexArabic, FontWeight.Normal, 14, 23, tnum = true),
    bodyMedium = style(PlexArabic, FontWeight.Normal, 13, 21, tnum = true),
    bodySmall = style(PlexArabic, FontWeight.Normal, 12, 19, tnum = true),
    labelLarge = style(PlexArabic, FontWeight.SemiBold, 14, 21),
    labelMedium = style(PlexArabic, FontWeight.Medium, 12, 18),
    labelSmall = style(PlexArabic, FontWeight.Medium, 11, 17),
)

/** أقصى تكبير للخط داخل التطبيق: يحترم إعداد الجهاز حتى 130% ثم يثبت كي لا تنكسر التخطيطات. */
const val MAX_FONT_SCALE = 1.3f
const val MIN_FONT_SCALE = 0.85f

fun ltr(text: String): String = "⁦$text⁩"
