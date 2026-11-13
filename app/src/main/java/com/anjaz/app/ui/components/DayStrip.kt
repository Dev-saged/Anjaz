package com.anjaz.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.anjaz.app.ui.theme.Anjaz

data class DayChip(val offset: Int, val weekday: String, val dayOfMonth: String, val hasTasks: Boolean)

@Composable
fun DayStrip(days: List<DayChip>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = Anjaz.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        days.forEach { d ->
            val on = d.offset == selected
            val shape = RoundedCornerShape(16.dp)
            Column(
                Modifier.weight(1f).height(72.dp).clip(shape)
                    .background(if (on) c.navy else c.card)
                    .clickable { onSelect(d.offset) }
                    .semantics { this.selected = on; contentDescription = "${d.weekday} ${d.dayOfMonth}" },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Text(d.weekday, color = if (on) c.onNavy.copy(.8f) else c.mute, style = MaterialTheme.typography.labelSmall)
                Text(d.dayOfMonth, color = if (on) c.onNavy else c.ink, style = MaterialTheme.typography.titleMedium)
                Box(
                    Modifier.size(6.dp).clip(CircleShape)
                        .background(if (d.hasTasks) c.emerald else androidx.compose.ui.graphics.Color.Transparent),
                )
            }
        }
    }
}
