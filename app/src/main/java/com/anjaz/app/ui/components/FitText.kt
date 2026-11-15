package com.anjaz.app.ui.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

/** نص في سطر واحد يصغر تلقائياً ليتّسع لمكانه بدل أن يلتفّ أو يتداخل (أيام الأسبوع، الأرقام، الوسوم). */
@Composable
fun FitText(
    text: String,
    color: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Center,
    minSize: TextUnit = 8.sp,
) {
    val max = if (style.fontSize.isSpecified) style.fontSize else 14.sp
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(color = color, textAlign = textAlign),
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(minFontSize = minSize, maxFontSize = max, stepSize = 0.5.sp),
    )
}
