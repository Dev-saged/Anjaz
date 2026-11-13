package com.anjaz.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
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

val AnjazTypography = Typography(
    displayLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, lineHeight = 36.sp, fontFeatureSettings = TNUM),
    headlineMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 22.sp, fontFeatureSettings = TNUM),
    bodyMedium = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 20.sp, fontFeatureSettings = TNUM),
    bodySmall = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 18.sp, fontFeatureSettings = TNUM),
    labelLarge = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.Medium, fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.Medium, fontSize = 11.sp),
)

fun ltr(text: String): String = "⁦$text⁩"
