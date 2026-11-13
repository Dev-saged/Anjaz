@file:OptIn(ExperimentalMaterial3Api::class)

package com.anjaz.app.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.BorderStroke
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anjaz.app.core.DayMath
import com.anjaz.app.data.db.TaskEntity
import com.anjaz.app.domain.Priority
import com.anjaz.app.domain.TaskStatus
import com.anjaz.app.schema.Schemas
import com.anjaz.app.ui.components.AnjazIcon
import com.anjaz.app.ui.components.PrimaryButton
import com.anjaz.app.ui.components.ScreenTitle
import com.anjaz.app.ui.components.SchemaForm
import com.anjaz.app.ui.components.Tag
import com.anjaz.app.ui.components.TaskCard
import com.anjaz.app.ui.components.Tone
import com.anjaz.app.ui.components.statusTone
import com.anjaz.app.ui.icons.AnjazIcons
import com.anjaz.app.ui.theme.Anjaz
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
fun TasksScreen(vm: TasksViewModel, onNewTask: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    val c = Anjaz.colors
    var selected by remember { mutableStateOf<TaskEntity?>(null) }
    Column(Modifier.fillMaxSize().background(c.surface).statusBarsPadding()) {
        ScreenTitle("المهام", Modifier.padding(horizontal = 18.dp, vertical = 12.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TaskFilter.entries.forEach { f ->
                val on = f == s.filter
                val shape = RoundedCornerShape(14.dp)
                Text(
                    f.label, color = if (on) c.onNavy else c.ink, style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.heightIn(min = 44.dp).clip(shape)
                        .background(if (on) c.navy else c.card)
                        .border(BorderStroke(1.dp, if (on) c.navy else c.line), shape)
                        .clickable { vm.setFilter(f) }
                        .padding(horizontal = 16.dp, vertical = 11.dp),
                )
            }
        }
        LazyColumn(
            Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp, 14.dp, 18.dp, 120.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!s.loading && s.tasks.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AnjazIcon(AnjazIcons.Tasks, c.mute, 40.dp)
                    Text("لا توجد مهام هنا", color = c.ink, style = MaterialTheme.typography.titleMedium)
                    Text("المهام التي تنشئها تظهر في هذه القائمة", color = c.mute, style = MaterialTheme.typography.bodySmall)
                }
            }
            items(s.tasks, key = { it.id }) { t -> TaskCard(t, s.now, { selected = t }) }
        }
    }
    selected?.let { t ->
        val live = s.tasks.firstOrNull { it.id == t.id } ?: t
        TaskDetailSheet(live, s.now, onMove = { vm.move(live, it) }, onDismiss = { selected = null })
    }
}

@Composable
private fun TaskDetailSheet(task: TaskEntity, now: Long, onMove: (TaskStatus) -> Unit, onDismiss: () -> Unit) {
    val c = Anjaz.colors
    val status = TaskStatus.parse(task.status)
    val primary = when (status) {
        TaskStatus.NEW, TaskStatus.SEEN -> TaskStatus.IN_PROGRESS
        TaskStatus.IN_PROGRESS -> TaskStatus.AWAITING_REVIEW
        TaskStatus.AWAITING_REVIEW -> TaskStatus.APPROVED
        else -> null
    }
    val quiet = TaskStatus.entries.filter { it != primary && status.canMoveTo(it) && it != TaskStatus.SEEN }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.surface) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(task.title, color = c.ink, style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Tag(status.label, statusTone(status))
                Priority.parse(task.priority).takeIf { it != Priority.NORMAL }?.let { Tag(it.label, if (it == Priority.URGENT) Tone.Danger else Tone.Warning) }
                DayMath.dueLabel(task.deadline, now)?.let { Text(it, color = c.mute, style = MaterialTheme.typography.bodySmall) }
            }
            if (task.description.isNotBlank()) Text(task.description, color = c.ink, style = MaterialTheme.typography.bodyLarge)
            if (primary != null) PrimaryButton(primaryLabel(primary), { onMove(primary) })
            quiet.forEach { to ->
                Text(
                    quietLabel(to), color = if (to == TaskStatus.CANCELLED || to == TaskStatus.REJECTED) c.redDeep else c.ink,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onMove(to) }.padding(vertical = 12.dp),
                )
            }
        }
    }
}

private fun primaryLabel(to: TaskStatus) = when (to) {
    TaskStatus.IN_PROGRESS -> "بدء المتابعة"
    TaskStatus.AWAITING_REVIEW -> "إرسال للاعتماد"
    TaskStatus.APPROVED -> "اعتماد الإنجاز"
    else -> to.label
}

private fun quietLabel(to: TaskStatus) = when (to) {
    TaskStatus.IN_PROGRESS -> "إعادة للمتابعة"
    TaskStatus.REJECTED -> "رفض"
    TaskStatus.CANCELLED -> "إلغاء المهمة"
    else -> to.label
}

@Composable
fun NewTaskSheet(vm: TasksViewModel) {
    val f by vm.form.collectAsStateWithLifecycle()
    val team by vm.team.collectAsStateWithLifecycle()
    if (!f.visible) return
    val schema = Schemas.taskWithAssignee(team)
    val c = Anjaz.colors
    ModalBottomSheet(onDismissRequest = vm::closeForm, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.surface) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ScreenTitle(schema.title)
            SchemaForm(schema, f.values, f.errors, vm::edit)
            PrimaryButton(if (f.saving) "جارٍ الحفظ…" else "حفظ المهمة", vm::submit, enabled = !f.saving)
        }
    }
}

