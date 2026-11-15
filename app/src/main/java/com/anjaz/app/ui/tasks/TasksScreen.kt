@file:OptIn(ExperimentalMaterial3Api::class)

package com.anjaz.app.ui.tasks

import com.anjaz.app.ui.components.EmptyState
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
import com.anjaz.app.ui.theme.ltr
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape

@Composable
fun TasksScreen(vm: TasksViewModel, onNewTask: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    val names by vm.names.collectAsStateWithLifecycle()
    val c = Anjaz.colors
    var selected by remember { mutableStateOf<TaskEntity?>(null) }
    Column(Modifier.fillMaxSize().background(c.surface).statusBarsPadding()) {
        val cat = s.category
        androidx.activity.compose.BackHandler(enabled = cat != null) { vm.openCategory(null) }
        if (cat == null) {
            ScreenTitle("المهام", Modifier.padding(horizontal = 18.dp, vertical = 12.dp))
            TaskHub(s, vm::openCategory)
        } else {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(CircleShape).clickable { vm.openCategory(null) }.semantics { contentDescription = "رجوع إلى الأقسام" }, contentAlignment = Alignment.Center) {
                    AnjazIcon(AnjazIcons.Back, c.ink)
                }
                ScreenTitle(cat.label, Modifier.weight(1f))
                Text(ltr(s.tasks.size.toString()), color = c.mute, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 14.dp))
            }
            androidx.compose.material3.OutlinedTextField(
                value = s.query, onValueChange = vm::search, singleLine = true,
                placeholder = { Text("ابحث في ${cat.label}") },
                leadingIcon = { AnjazIcon(AnjazIcons.Search, c.mute, 20.dp) },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            )
            LazyColumn(
                Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp, 14.dp, 18.dp, 120.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (!s.loading && s.tasks.isEmpty()) item {
                    EmptyState(AnjazIcons.Tasks, if (s.query.isBlank()) "لا توجد مهام هنا" else "لا نتائج", if (s.query.isBlank()) "ستظهر هنا مهام هذا القسم" else "جرّب كلمة أخرى")
                }
                items(s.tasks, key = { it.id }) { t -> TaskCard(t, s.now, { selected = t }, subtitle = targetLabel(t, names)) }
            }
        }
    }
    selected?.let { t ->
        val live = s.tasks.firstOrNull { it.id == t.id } ?: t
        val history by vm.history.collectAsStateWithLifecycle()
        androidx.compose.runtime.DisposableEffect(live.id) { vm.watch(live.id); onDispose { vm.watch(null) } }
        TaskDetailSheet(
            live, s.now, history, targetLabel(live, names), vm.canDelete,
            onMove = { vm.move(live, it) },
            onDuplicate = { selected = null; vm.duplicate(live) },
            onDelete = { selected = null; vm.delete(live) },
            onDismiss = { selected = null },
        )
    }
}

@Composable
private fun TaskDetailSheet(
    task: TaskEntity, now: Long, history: List<com.anjaz.app.data.db.TaskEventEntity>, target: String?, canDelete: Boolean,
    onMove: (TaskStatus) -> Unit, onDuplicate: () -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit,
) {
    var confirm by remember { mutableStateOf(false) }
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
                Priority.parse(task.priority).takeIf { it.urgent }?.let { Tag(it.label, Tone.Danger) }
                DayMath.dueLabel(task.deadline, now)?.let { Text(it, color = c.mute, style = MaterialTheme.typography.bodySmall) }
            }
            target?.let { Text(it, color = c.mute, style = MaterialTheme.typography.bodyMedium) }
            if (task.description.isNotBlank()) Text(task.description, color = c.ink, style = MaterialTheme.typography.bodyLarge)
            TaskPhotosSection(task.id)
            if (primary != null) PrimaryButton(primaryLabel(primary), { onMove(primary) })
            if (history.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("سجل المهمة", color = c.mute, style = MaterialTheme.typography.labelLarge)
                history.takeLast(8).asReversed().forEach { e ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(DayMath.time(e.at), color = c.mute, style = MaterialTheme.typography.bodySmall)
                        Text(TaskStatus.parse(e.toStatus).label + (e.note?.takeIf { it.isNotBlank() }?.let { " — $it" } ?: ""), color = c.ink, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            quiet.forEach { to ->
                Text(
                    quietLabel(to), color = if (to == TaskStatus.CANCELLED || to == TaskStatus.REJECTED) c.redDeep else c.ink,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onMove(to) }.padding(vertical = 12.dp),
                )
            }
            Text(
                "تكرار المهمة", color = c.ink, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onDuplicate).padding(vertical = 12.dp),
            )
            if (canDelete) Text(
                "حذف المهمة", color = c.redDeep, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { confirm = true }.padding(vertical = 12.dp),
            )
        }
    }
    if (confirm) androidx.compose.material3.AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("حذف المهمة؟") },
        text = { Text("تنتقل إلى سلة المهملات وتبقى 30 يوماً يمكنك استرجاعها خلالها.") },
        confirmButton = { androidx.compose.material3.TextButton({ confirm = false; onDelete() }) { Text("حذف", color = Anjaz.colors.redDeep) } },
        dismissButton = { androidx.compose.material3.TextButton({ confirm = false }) { Text("إلغاء") } },
    )
}

@Composable
private fun TaskHub(s: TasksUiState, onOpen: (TaskCategory) -> Unit) {
    val c = Anjaz.colors
    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
        columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(minSize = 150.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp, 4.dp, 18.dp, 120.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(TaskCategory.tiles.size) { i ->
            val cat = TaskCategory.tiles[i]
            val (tint, bg, icon) = when (cat) {
                TaskCategory.NORMAL -> Triple(if (c.dark) c.ink else c.navy, c.navy.copy(alpha = .10f), AnjazIcons.Tasks)
                TaskCategory.URGENT -> Triple(c.redDeep, c.red.copy(alpha = .12f), AnjazIcons.Bell)
                TaskCategory.REVIEW -> Triple(c.amberDeep, c.amber.copy(alpha = .16f), AnjazIcons.Report)
                else -> Triple(c.emeraldDeep, c.emerald.copy(alpha = .14f), AnjazIcons.Check)
            }
            val count = s.counts[cat] ?: 0
            val late = s.overdue[cat] ?: 0
            Column(
                Modifier.fillMaxWidth().heightIn(min = 150.dp).clip(RoundedCornerShape(22.dp)).background(c.card)
                    .border(BorderStroke(1.dp, c.line), RoundedCornerShape(22.dp))
                    .clickable { onOpen(cat) }.semantics { contentDescription = "${cat.label}: $count" }.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(bg), contentAlignment = Alignment.Center) { AnjazIcon(icon, tint, 22.dp) }
                    Spacer(Modifier.weight(1f))
                    if (late > 0) Tag("${ltr(late.toString())} متأخرة", Tone.Danger)
                }
                com.anjaz.app.ui.components.FitText(ltr(count.toString()), tint, MaterialTheme.typography.displayLarge.copy(fontFamily = com.anjaz.app.ui.theme.Display), Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Start)
                Text(cat.label, color = c.ink, style = MaterialTheme.typography.titleMedium)
                Text(cat.hint, color = c.mute, style = MaterialTheme.typography.bodySmall)
            }
        }
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            com.anjaz.app.ui.components.QuietButton("عرض كل المهام (${ltr((s.counts[TaskCategory.ALL] ?: 0).toString())})") { onOpen(TaskCategory.ALL) }
        }
    }
}

private fun targetLabel(t: TaskEntity, names: Map<String, String>): String? = when {
    t.campId != null -> names[t.campId]?.let { "المخيم: $it" }
    t.assigneeId != null -> names[t.assigneeId]?.let { "المكلّف: $it" }
    t.squareId != null -> names[t.squareId]?.let { "المربع: $it" }
    else -> null
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
    val squares by vm.squares.collectAsStateWithLifecycle()
    val camps by vm.camps.collectAsStateWithLifecycle()
    if (!f.visible) return
    val schema = Schemas.task
    val c = Anjaz.colors
    ModalBottomSheet(onDismissRequest = vm::closeForm, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.surface) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ScreenTitle(schema.title)
            SchemaForm(schema, f.values, f.errors, vm::edit)
            DeadlineField(f.deadline, vm::setDeadline)
            TargetField(f.kind, f.targets, f.errors["target"], squares, camps, team, vm::pickKind, vm::toggleTarget)
            PrimaryButton(if (f.saving) "جارٍ الحفظ…" else "حفظ المهمة", vm::submit, enabled = !f.saving)
        }
    }
}
