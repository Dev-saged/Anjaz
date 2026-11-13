package com.anjaz.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.anjaz.app.core.DayMath
import com.anjaz.app.data.db.TaskEntity
import com.anjaz.app.domain.Priority
import com.anjaz.app.domain.TaskStatus
import com.anjaz.app.ui.theme.Anjaz

fun statusTone(s: TaskStatus): Tone = when (s) {
    TaskStatus.APPROVED -> Tone.Success
    TaskStatus.AWAITING_REVIEW -> Tone.Warning
    TaskStatus.REJECTED -> Tone.Danger
    else -> Tone.Neutral
}

@Composable
fun TaskCard(task: TaskEntity, now: Long, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Anjaz.colors
    val status = TaskStatus.parse(task.status)
    val priority = Priority.parse(task.priority)
    val overdue = TaskStatus.isOverdue(status, task.deadline, now)
    val accent = when {
        overdue || priority == Priority.URGENT -> c.red
        priority == Priority.HIGH -> c.amber
        else -> c.emerald
    }
    AnjazCard(modifier, onClick) {
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.width(4.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(accent))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(task.title, color = c.ink, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Tag(status.label, statusTone(status))
                    if (priority != Priority.NORMAL) Tag(priority.label, if (priority == Priority.URGENT) Tone.Danger else Tone.Warning)
                    DayMath.dueLabel(task.deadline, now)?.let {
                        Text(it, color = if (overdue) c.redDeep else c.mute, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

