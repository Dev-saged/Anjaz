package com.anjaz.app.ui.trash

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anjaz.app.ui.components.AnjazCard
import com.anjaz.app.ui.components.AnjazIcon
import com.anjaz.app.ui.components.EmptyState
import com.anjaz.app.ui.components.ScreenTitle
import com.anjaz.app.ui.components.Tag
import com.anjaz.app.ui.components.Tone
import com.anjaz.app.ui.icons.AnjazIcons
import com.anjaz.app.ui.theme.Anjaz
import com.anjaz.app.ui.theme.ltr

@Composable
fun TrashScreen(onBack: () -> Unit, vm: TrashViewModel = hiltViewModel()) {
    val c = Anjaz.colors
    val ui by vm.ui.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var confirmEmpty by remember { mutableStateOf(false) }
    var confirmPurge by remember { mutableStateOf<TrashItem?>(null) }
    Column(Modifier.fillMaxSize().background(c.surface).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onBack).semantics { contentDescription = "رجوع" }, contentAlignment = Alignment.Center) {
                AnjazIcon(AnjazIcons.Back, c.ink)
            }
            ScreenTitle("سلة المهملات", Modifier.weight(1f))
            if (ui.items.isNotEmpty()) Text(
                "إفراغ", color = c.redDeep, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.heightIn(min = 48.dp).clickable { confirmEmpty = true }.padding(horizontal = 14.dp, vertical = 14.dp),
            )
        }
        message?.let { Text(it, color = c.ink, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth().clickable(onClick = vm::dismiss).background(c.card).padding(14.dp)) }
        Text("تُحذف العناصر نهائياً بعد 30 يوماً تلقائياً.", color = c.mute, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp))
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp, 8.dp, 18.dp, 40.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (ui.items.isEmpty()) item { EmptyState(AnjazIcons.Trash, "السلة فارغة", "ما تحذفه من مهام ومربعات ومخيمات يظهر هنا لتسترجعه") }
            items(ui.items, key = { it.kind.name + it.id }) { i ->
                AnjazCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Tag(i.kind.label, Tone.Neutral)
                            Text("بقي ${ltr(vm.daysLeft(i, ui.now).toString())} يوماً", color = c.mute, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(i.title, color = c.ink, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                        if (i.detail.isNotEmpty()) Text(i.detail, color = c.mute, style = MaterialTheme.typography.bodySmall)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Row(
                                Modifier.heightIn(min = 44.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).background(c.navy)
                                    .clickable { vm.restore(i) }.padding(horizontal = 16.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AnjazIcon(AnjazIcons.Retry, c.onNavy)
                                Text("استرجاع", color = c.onNavy, style = MaterialTheme.typography.labelLarge)
                            }
                            Text(
                                "حذف نهائي", color = c.redDeep, style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.heightIn(min = 44.dp).clickable { confirmPurge = i }.padding(horizontal = 12.dp, vertical = 12.dp),
                            )
                        }
                    }
                }
            }
        }
    }
    if (confirmEmpty) AlertDialog(
        onDismissRequest = { confirmEmpty = false },
        title = { Text("إفراغ السلة؟") },
        text = { Text("سيُحذف كل ما فيها نهائياً ولا يمكن استرجاعه.") },
        confirmButton = { TextButton({ confirmEmpty = false; vm.empty() }) { Text("إفراغ", color = c.redDeep) } },
        dismissButton = { TextButton({ confirmEmpty = false }) { Text("إلغاء") } },
    )
    confirmPurge?.let { i ->
        AlertDialog(
            onDismissRequest = { confirmPurge = null },
            title = { Text("حذف نهائي؟") },
            text = { Text("«${i.title}» لن يمكن استرجاعه بعد ذلك.") },
            confirmButton = { TextButton({ confirmPurge = null; vm.purge(i) }) { Text("حذف", color = c.redDeep) } },
            dismissButton = { TextButton({ confirmPurge = null }) { Text("إلغاء") } },
        )
    }
}
