@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.anjaz.app.ui.tasks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.anjaz.app.core.DayMath
import com.anjaz.app.data.TargetKind
import com.anjaz.app.data.db.CampEntity
import com.anjaz.app.data.db.SquareEntity
import com.anjaz.app.schema.Choice
import com.anjaz.app.ui.theme.Anjaz
import com.anjaz.app.ui.theme.ltr
import java.util.Calendar
import java.util.TimeZone

@Composable
private fun OptionChip(label: String, on: Boolean, onClick: () -> Unit) {
    val c = Anjaz.colors
    val shape = RoundedCornerShape(14.dp)
    Text(
        label, color = if (on) c.onNavy else c.ink, style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.heightIn(min = 44.dp).clip(shape)
            .background(if (on) c.navy else c.card)
            .border(BorderStroke(1.dp, if (on) c.navy else c.line), shape)
            .clickable(onClick = onClick).semantics { role = Role.RadioButton }
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

private fun utcMidnight(localMs: Long): Long {
    val l = Calendar.getInstance().apply { timeInMillis = localMs }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear(); set(l[Calendar.YEAR], l[Calendar.MONTH], l[Calendar.DAY_OF_MONTH])
    }.timeInMillis
}

/** المنتقي يعيد منتصف الليل UTC؛ الموعد يُحفظ نهاية اليوم المحلي حتى لا تتأخر المهمة في يومها. */
private fun endOfLocalDay(utcMs: Long): Long {
    val u = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMs }
    return Calendar.getInstance().apply {
        clear(); set(u[Calendar.YEAR], u[Calendar.MONTH], u[Calendar.DAY_OF_MONTH], 23, 59, 59)
    }.timeInMillis
}

@Composable
fun DeadlineField(deadline: Long?, onPick: (Long?) -> Unit) {
    val c = Anjaz.colors
    var open by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("آخر موعد للتنفيذ", color = c.ink, style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OptionChip(deadline?.let { ltr(DayMath.date(it)) } ?: "اختر التاريخ", deadline != null) { open = true }
            if (deadline != null) Text(
                "إزالة", color = c.redDeep, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.heightIn(min = 44.dp).clickable { onPick(null) }.padding(horizontal = 10.dp, vertical = 12.dp),
            )
        }
    }
    if (open) {
        val today = utcMidnight(System.currentTimeMillis())
        val state = rememberDatePickerState(
            initialSelectedDateMillis = deadline?.let(::utcMidnight),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= today
            },
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton({ state.selectedDateMillis?.let { onPick(endOfLocalDay(it)) }; open = false }, enabled = state.selectedDateMillis != null) { Text("تأكيد") }
            },
            dismissButton = { TextButton({ open = false }) { Text("إلغاء") } },
        ) { DatePicker(state) }
    }
}

@Composable
fun TargetField(
    kind: TargetKind?, selected: Set<String>, error: String?,
    squares: List<SquareEntity>, camps: List<CampEntity>, people: List<Choice>,
    onKind: (TargetKind) -> Unit, onToggle: (String) -> Unit,
) {
    val c = Anjaz.colors
    val squareNames = squares.associate { it.id to com.anjaz.app.domain.SquareTree.fullName(it, squares) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("المكلّف بالمهمة *", color = c.ink, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TargetKind.entries.forEach { k ->
                val hidden = (k == TargetKind.ALL_SQUARES || k == TargetKind.SQUARE) && squares.isEmpty() ||
                    (k == TargetKind.ALL_CAMPS || k == TargetKind.CAMP) && camps.isEmpty() ||
                    k == TargetKind.PERSONS && people.isEmpty()
                if (!hidden) OptionChip(k.label, k == kind) { onKind(k) }
            }
        }
        val items: List<Choice> = when (kind) {
            TargetKind.SQUARE -> squares.map { Choice(it.id, com.anjaz.app.domain.SquareTree.fullName(it, squares)) }
            TargetKind.CAMP -> camps.map { Choice(it.id, it.name + (squareNames[it.squareId]?.let { s -> " — $s" } ?: "")) }
            TargetKind.PERSONS -> people
            else -> emptyList()
        }
        if (items.isNotEmpty()) Column(
            Modifier.fillMaxWidth().heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items.forEach { ch -> OptionChip(ch.label, ch.key in selected) { onToggle(ch.key) } }
            }
        }
        val count = when (kind) {
            TargetKind.ALL_SQUARES -> squares.size
            TargetKind.ALL_CAMPS -> camps.size
            null -> 0
            else -> selected.size
        }
        if (count > 1) Text("سيُنشأ ${ltr(count.toString())} مهمة، واحدة لكل جهة لتُتابَع على حدة", color = c.mute, style = MaterialTheme.typography.bodySmall)
        error?.let { Text(it, color = c.redDeep, style = MaterialTheme.typography.bodySmall) }
    }
}
