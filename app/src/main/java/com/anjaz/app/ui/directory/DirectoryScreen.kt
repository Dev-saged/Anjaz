@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.anjaz.app.ui.directory

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

@Composable
fun DirectoryScreen(onBack: () -> Unit, vm: DirectoryViewModel = hiltViewModel()) {
    val c = Anjaz.colors
    val ui by vm.ui.collectAsStateWithLifecycle()
    val form by vm.form.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
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
            if (ui.squares.isEmpty()) item { Text("لا توجد مربعات بعد", color = c.mute, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 24.dp)) }
            items(ui.squares, key = { it.square.id }) { row ->
                AnjazCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(row.square.name, color = c.ink, style = MaterialTheme.typography.titleMedium)
                                if (row.regionName.isNotEmpty()) Text(row.regionName, color = c.mute, style = MaterialTheme.typography.bodySmall)
                            }
                            Tag("${ltr(row.camps.size.toString())} مخيم", Tone.Neutral)
                        }
                        row.camps.forEach { camp ->
                            Column(Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).background(c.surface).padding(10.dp)) {
                                Text(camp.name, color = c.ink, style = MaterialTheme.typography.bodyLarge)
                                camp.managerName?.let { Text(it, color = c.mute, style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                        if (ui.canCamp) QuietButton("مخيم جديد") { vm.open(DirKind.CAMP, row.square.id) }
                    }
                }
            }
        }
    }
    val f = form
    if (f != null) ModalBottomSheet(onDismissRequest = vm::close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.surface) {
        val base = vm.schemaFor(f, ui)
        val needsRegion = f.kind == DirKind.SQUARE && ui.profile?.roleEnum == com.anjaz.app.domain.Role.CENTRAL
        val schema = if (needsRegion) base.copy(fields = listOf(FieldSchema("regionId", "المنطقة", FieldType.CHOICE, required = true, choices = vm.regionChoices(ui))) + base.fields) else base
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ScreenTitle(schema.title)
            SchemaForm(schema, f.values, f.errors, vm::change)
            PrimaryButton("حفظ", onClick = vm::submit)
        }
    }
}
