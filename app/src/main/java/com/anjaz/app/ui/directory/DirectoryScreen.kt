@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.anjaz.app.ui.directory

import com.anjaz.app.ui.components.EmptyState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anjaz.app.schema.FieldSchema
import com.anjaz.app.schema.FieldType
import com.anjaz.app.ui.components.AnjazCard
import com.anjaz.app.ui.components.AnjazIcon
import com.anjaz.app.ui.components.PrimaryButton
import com.anjaz.app.ui.components.QuietButton
import com.anjaz.app.ui.components.SchemaForm
import com.anjaz.app.ui.components.ScreenTitle
import com.anjaz.app.ui.components.Tag
import com.anjaz.app.ui.components.Tone
import com.anjaz.app.ui.icons.AnjazIcons
import com.anjaz.app.ui.theme.Anjaz
import com.anjaz.app.ui.theme.ltr
import com.anjaz.app.domain.Plural
import androidx.compose.ui.graphics.graphicsLayer

private fun ltrNum(n: Int) = ltr(n.toString())

@Composable
fun DirectoryScreen(onBack: () -> Unit, vm: DirectoryViewModel = hiltViewModel()) {
    val c = Anjaz.colors
    val ui by vm.ui.collectAsStateWithLifecycle()
    val form by vm.form.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var confirmDelete by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var expanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(setOf<String>()) }
    Column(Modifier.fillMaxSize().background(c.surface).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onBack).semantics { contentDescription = "رجوع" }, contentAlignment = Alignment.Center) {
                AnjazIcon(AnjazIcons.Back, c.ink)
            }
            ScreenTitle("المربعات والمخيمات", Modifier.weight(1f))
        }
        message?.let { Text(it, color = c.ink, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth().clickable(onClick = vm::dismissMessage).background(c.card).padding(14.dp)) }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp, 8.dp, 18.dp, 40.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (ui.canRegion) item { QuietButton("منطقة جديدة") { vm.open(DirKind.REGION) } }
            if (ui.canSquare) item { QuietButton("مربع جديد") { vm.open(DirKind.SQUARE, ui.profile?.regionId) } }
            if (ui.squares.isEmpty()) item { EmptyState(AnjazIcons.Map, "لا توجد مربعات بعد", "أضف منطقة ثم مربعاً ليظهر هنا") }
            if (ui.squares.isNotEmpty()) item {
                val camps = ui.squares.sumOf { it.camps.size }
                val fam = ui.squares.sumOf { r -> r.camps.sumOf { it.families ?: 0 } }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    com.anjaz.app.ui.components.KpiCard(ltr(ui.squares.size.toString()), "مربعات", Tone.Neutral, Modifier.weight(1f))
                    com.anjaz.app.ui.components.KpiCard(ltr(camps.toString()), "مخيمات", Tone.Neutral, Modifier.weight(1f))
                    com.anjaz.app.ui.components.KpiCard(ltr(Plural.grouped(fam)), "أسرة", Tone.Neutral, Modifier.weight(1f))
                }
            }
            items(ui.squares, key = { it.square.id }) { row ->
                val open = row.square.id in expanded
                val fam = row.camps.sumOf { it.families ?: 0 }
                val turn by androidx.compose.animation.core.animateFloatAsState(if (open) 180f else 0f, label = "arrow")
                AnjazCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { expanded = if (open) expanded - row.square.id else expanded + row.square.id }
                                .semantics { contentDescription = row.square.name + if (open) "، مفتوح" else "، اضغط لعرض المخيمات" },
                            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(row.square.name, color = c.ink, style = MaterialTheme.typography.titleMedium)
                                Text("المسؤول: " + (row.square.managerName?.takeIf { it.isNotBlank() } ?: "غير محدد"), color = if (row.square.managerName.isNullOrBlank()) c.mute else c.ink, style = MaterialTheme.typography.bodyMedium)
                                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Tag(Plural.of(row.camps.size, Plural.camp, ::ltrNum), Tone.Neutral)
                                    Tag(Plural.of(fam, Plural.family) { ltr(Plural.grouped(it)) }, Tone.Success)
                                    if (row.regionName.isNotEmpty()) Tag(row.regionName, Tone.Neutral)
                                }
                            }
                            if (ui.canSquare) Box(Modifier.size(44.dp).clip(CircleShape).clickable { vm.openEdit(DirKind.SQUARE, row.square.id) }.semantics { contentDescription = "تعديل " + row.square.name }, contentAlignment = Alignment.Center) {
                                AnjazIcon(AnjazIcons.Edit, c.mute, 20.dp)
                            }
                            Box(Modifier.size(36.dp).clip(CircleShape).background(c.surface), contentAlignment = Alignment.Center) {
                                Box(Modifier.graphicsLayer { rotationZ = turn }) { AnjazIcon(AnjazIcons.Down, c.ink, 20.dp) }
                            }
                        }
                        androidx.compose.animation.AnimatedVisibility(open) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (row.camps.isEmpty()) Text("لا مخيمات في هذا المربع بعد", color = c.mute, style = MaterialTheme.typography.bodySmall)
                                row.camps.forEach { camp ->
                                    Row(
                                        Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).background(c.surface)
                                            .then(if (ui.canCamp) Modifier.clickable { vm.openEdit(DirKind.CAMP, camp.id) } else Modifier).padding(12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        AnjazIcon(AnjazIcons.Camp, c.emeraldDeep, 22.dp)
                                        Column(Modifier.weight(1f)) {
                                            Text(camp.name, color = c.ink, style = MaterialTheme.typography.bodyLarge)
                                            camp.managerName?.takeIf { it.isNotBlank() }?.let { Text(it, color = c.mute, style = MaterialTheme.typography.bodySmall) }
                                        }
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(camp.families?.let { ltr(Plural.grouped(it)) } ?: "—", color = c.ink, style = MaterialTheme.typography.titleMedium)
                                            Text("أسرة", color = c.mute, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                                if (ui.canCamp) QuietButton("مخيم جديد") { vm.open(DirKind.CAMP, row.square.id) }
                            }
                        }
                    }
                }
            }
        }
    }
    val f = form
    if (f != null) ModalBottomSheet(onDismissRequest = vm::close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.surface) {
        val base = vm.schemaFor(f, ui)
        val needsRegion = f.editId == null && f.kind == DirKind.SQUARE && ui.profile?.roleEnum == com.anjaz.app.domain.Role.CENTRAL
        val schema = if (needsRegion) base.copy(fields = listOf(FieldSchema("regionId", "المنطقة", FieldType.CHOICE, required = true, choices = vm.regionChoices(ui))) + base.fields) else base
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ScreenTitle(if (f.editId != null) "تعديل: " + schema.title else schema.title)
            SchemaForm(schema, f.values, f.errors, vm::change)
            PrimaryButton("حفظ", onClick = vm::submit)
            if (f.editId != null) Text(
                "حذف إلى سلة المهملات", color = c.redDeep, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { confirmDelete = true }.padding(vertical = 12.dp),
            )
        }
    }
    if (confirmDelete) androidx.compose.material3.AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("نقل إلى سلة المهملات؟") },
        text = { Text(if (form?.kind == DirKind.SQUARE) "يُنقل المربع مع مخيماته، ويمكنك استرجاعهم خلال 30 يوماً." else "يمكنك استرجاعه خلال 30 يوماً.") },
        confirmButton = { androidx.compose.material3.TextButton({ confirmDelete = false; vm.trashEditing() }) { Text("حذف", color = c.redDeep) } },
        dismissButton = { androidx.compose.material3.TextButton({ confirmDelete = false }) { Text("إلغاء") } },
    )
}
