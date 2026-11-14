package com.anjaz.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.anjaz.app.ui.icons.AnjazIcons
import com.anjaz.app.ui.theme.Anjaz
import kotlinx.coroutines.delay

/** شريط تنبيه ملوّن يختفي تلقائياً: أحمر للأخطاء وأخضر للنجاح، ويُغلق بالضغط عليه. */
@Composable
fun Notice(message: String?, isError: Boolean, onDismiss: () -> Unit, modifier: Modifier = Modifier, millis: Long = 5000) {
    val c = Anjaz.colors
    LaunchedEffect(message) { if (message != null) { delay(millis); onDismiss() } }
    AnimatedVisibility(message != null, modifier, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        val tone = if (isError) c.red else c.emerald
        val text = if (isError) c.redDeep else c.emeraldDeep
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(tone.copy(alpha = .13f)).border(1.dp, tone.copy(alpha = .45f), RoundedCornerShape(16.dp))
                .clickable(onClick = onDismiss).padding(horizontal = 14.dp, vertical = 12.dp).semantics { liveRegion = LiveRegionMode.Assertive },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(tone), contentAlignment = Alignment.Center) {
                AnjazIcon(if (isError) AnjazIcons.Shield else AnjazIcons.Check, c.onNavy, 16.dp)
            }
            Text(message.orEmpty(), color = text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(vertical = 2.dp))
        }
    }
}
