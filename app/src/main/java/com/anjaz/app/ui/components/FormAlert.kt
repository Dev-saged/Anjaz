package com.anjaz.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.anjaz.app.ui.icons.AnjazIcons
import com.anjaz.app.ui.theme.Anjaz

/** تنبيه بارز ولطيف داخل النماذج: لماذا رُفض الطلب وماذا تفعل. يُقرأ تلقائياً لقارئ الشاشة. */
@Composable
fun FormAlert(message: String?, title: String = "لم يكتمل الطلب", onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val c = Anjaz.colors
    AnimatedVisibility(message != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically(), modifier = modifier) {
        val shape = RoundedCornerShape(18.dp)
        Row(
            Modifier.fillMaxWidth().clip(shape).background(c.red.copy(alpha = .10f)).border(BorderStroke(1.dp, c.red.copy(alpha = .35f)), shape)
                .semantics { liveRegion = LiveRegionMode.Assertive }.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top,
        ) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(c.red.copy(alpha = .18f)), contentAlignment = Alignment.Center) {
                Text("!", color = c.redDeep, style = MaterialTheme.typography.titleLarge)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = c.redDeep, style = MaterialTheme.typography.titleMedium)
                Text(message.orEmpty(), color = c.ink, style = MaterialTheme.typography.bodyMedium)
            }
            Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onDismiss).semantics { contentDescription = "إغلاق التنبيه" }, contentAlignment = Alignment.Center) {
                AnjazIcon(AnjazIcons.Close, c.mute, 18.dp)
            }
        }
    }
}
