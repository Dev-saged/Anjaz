package com.anjaz.app.ui.distribute

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anjaz.app.domain.Plural
import com.anjaz.app.ui.components.AnjazCard
import com.anjaz.app.ui.components.AnjazIcon
import com.anjaz.app.ui.components.EmptyState
import com.anjaz.app.ui.components.FitText
import com.anjaz.app.ui.components.FormAlert
import com.anjaz.app.ui.components.Notice
import com.anjaz.app.ui.components.PrimaryButton
import com.anjaz.app.ui.components.QuietButton
import com.anjaz.app.ui.components.ScreenTitle
import com.anjaz.app.ui.icons.AnjazIcons
import com.anjaz.app.ui.tasks.DeadlineField
import com.anjaz.app.ui.theme.Anjaz
import com.anjaz.app.ui.theme.Display
import com.anjaz.app.ui.theme.ltr

private fun n(v: Long) = ltr(Plural.grouped(v.toInt()))

@Composable
fun DistributeScreen(onBack: () -> Unit, vm: DistributeViewModel = hiltViewModel()) {
    val c = Anjaz.colors
    val u by vm.ui.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var open by remember { mutableStateOf(setOf<String>()) }
    Column(Modifier.fillMaxSize().background(c.surface).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onBack).semantics { contentDescription = "رجوع" }, contentAlignment = Alignment.Center) {
                AnjazIcon(AnjazIcons.Back, c.ink)
            }
            ScreenTitle("توزيع كمية حسب الأوزان", Modifier.weight(1f))
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp, 4.dp, 18.dp, 48.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("اكتب الكمية الواردة فتُقسَّم على المربعات حسب عدد أسرها، ثم على مخيمات كل مربع بالطريقة نفسها. المجموع يطابق الكمية دائماً.", color = c.mute, style = MaterialTheme.typography.bodyMedium)
            }
            item {
                AnjazCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(u.input.item, { v -> vm.edit { it.copy(item = v.take(60)) } }, label = { Text("المادة (مثل: طرود غذائية)") }, singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                u.input.quantity, { v -> vm.edit { it.copy(quantity = v.take(12)) } }, label = { Text("الكمية *") }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(u.input.unit, { v -> vm.edit { it.copy(unit = v.take(20)) } }, label = { Text("الوحدة") }, singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            if (u.total > 0) item {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.navy).padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("المجموع الموزَّع", color = c.onNavy.copy(alpha = .8f), style = MaterialTheme.typography.labelLarge)
                        FitText(n(u.allocated) + " من " + n(u.total), c.onNavy, MaterialTheme.typography.titleLarge.copy(fontFamily = Display), textAlign = androidx.compose.ui.text.style.TextAlign.Start)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("الأسر المشمولة", color = c.onNavy.copy(alpha = .8f), style = MaterialTheme.typography.labelLarge)
                        Text(n(u.families.toLong()), color = c.onNavy, style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
            if (u.shares.isEmpty()) item { EmptyState(AnjazIcons.Users, "لا مربعات ضمن نطاقك", "أضف المربعات والمخيمات وأعداد الأسر أولاً") }
            items(u.shares, key = { it.square.id }) { s ->
                val expanded = s.square.id in open
                val shape = RoundedCornerShape(20.dp)
                Column(
                    Modifier.fillMaxWidth().clip(shape).background(c.card).border(BorderStroke(1.dp, if (s.included) c.line else c.line.copy(alpha = .4f)), shape).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f).clickable { open = if (expanded) open - s.square.id else open + s.square.id }) {
                            Text(s.square.name, color = if (s.included) c.ink else c.mute, style = MaterialTheme.typography.titleMedium)
                            Text(
                                Plural.of(s.families, Plural.family) { ltr(Plural.grouped(it)) } + (if (s.subs.isNotEmpty()) " · ${ltr(s.subs.size.toString())} مربعات فرعية" else "") +
                                    " · " + Plural.of(s.camps.size + s.subs.sumOf { it.camps.size }, Plural.camp) { ltr(it.toString()) } + if (s.included) " · الوزن ${ltr(s.weight)}" else " · مستثنى",
                                color = c.mute, style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (s.included && u.total > 0) Text(n(s.amount), color = c.emeraldDeep, style = MaterialTheme.typography.titleLarge.copy(fontFamily = Display))
                        Switch(checked = s.included, onCheckedChange = { vm.toggle(s.square.id) }, modifier = Modifier.semantics { contentDescription = "شمول ${s.square.name}" })
                    }
                    if (s.included && s.families == 0) Text("لا عدد أسر مسجّل لمخيمات هذا المربع، فحصته صفر.", color = c.amberDeep, style = MaterialTheme.typography.bodySmall)
                    AnimatedVisibility(expanded && s.included) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            s.camps.forEach { cs ->
                                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.surface).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    AnjazIcon(AnjazIcons.Camp, c.emeraldDeep, 18.dp)
                                    Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                                        Text(cs.camp.name, color = c.ink, style = MaterialTheme.typography.bodyLarge)
                                        Text(Plural.of(cs.families, Plural.family) { ltr(Plural.grouped(it)) }, color = c.mute, style = MaterialTheme.typography.bodySmall)
                                    }
                                    Text(n(cs.amount), color = c.ink, style = MaterialTheme.typography.titleMedium)
                                }
                            }
                            s.subs.forEach { sub ->
                                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("مربع فرعي: " + sub.square.name, color = c.ink, style = MaterialTheme.typography.titleMedium)
                                        Text(Plural.of(sub.families, Plural.family) { ltr(Plural.grouped(it)) } + " · ${ltr(sub.weight)} من ${s.square.name}", color = c.mute, style = MaterialTheme.typography.bodySmall)
                                    }
                                    Text(n(sub.amount), color = c.emeraldDeep, style = MaterialTheme.typography.titleMedium)
                                }
                                sub.camps.forEach { cs ->
                                    Row(Modifier.fillMaxWidth().padding(start = 12.dp).clip(RoundedCornerShape(12.dp)).background(c.surface).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        AnjazIcon(AnjazIcons.Camp, c.emeraldDeep, 18.dp)
                                        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                                            Text(cs.camp.name, color = c.ink, style = MaterialTheme.typography.bodyLarge)
                                            Text(Plural.of(cs.families, Plural.family) { ltr(Plural.grouped(it)) }, color = c.mute, style = MaterialTheme.typography.bodySmall)
                                        }
                                        Text(n(cs.amount), color = c.ink, style = MaterialTheme.typography.titleMedium)
                                    }
                                }
                            }
                            if (s.camps.isEmpty() && s.subs.isEmpty()) Text("لا مخيمات في هذا المربع", color = c.mute, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (s.included && (s.camps.isNotEmpty() || s.subs.isNotEmpty())) Text(if (expanded) "إخفاء التفاصيل" else if (s.subs.isNotEmpty()) "عرض حصص المربعات الفرعية والمخيمات" else "عرض حصص المخيمات", color = c.navy, style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.heightIn(min = 44.dp).clickable { open = if (expanded) open - s.square.id else open + s.square.id }.padding(vertical = 12.dp))
                }
            }
            if (u.total > 0 && u.shares.isNotEmpty()) {
                item { FormAlert(u.error, title = "لم تُرسل الخطة", onDismiss = vm::dismiss) }
                item { Notice(u.message, false, vm::dismiss) }
                if (u.canSend) item {
                    AnjazCard(Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("إرسالها كمهام للمربعات", color = c.ink, style = MaterialTheme.typography.titleMedium)
                            Text("مهمة لكل مربع فيها حصته وتفصيل مخيماته، يتابعها مدير المربع حتى الاعتماد.", color = c.mute, style = MaterialTheme.typography.bodySmall)
                            DeadlineField(u.input.deadline) { d -> vm.edit { it.copy(deadline = d) } }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("عاجلة", color = c.ink, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                Switch(u.input.urgent, { v -> vm.edit { it.copy(urgent = v) } })
                            }
                            PrimaryButton(if (u.busy) "جارٍ الإرسال…" else "إرسال الخطة كمهام", vm::send, enabled = !u.busy)
                        }
                    }
                }
                item {
                    QuietButton("مشاركة الخطة نصاً", Modifier.fillMaxWidth()) {
                        val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, vm.shareText(u))
                        runCatching { ctx.startActivity(android.content.Intent.createChooser(send, "مشاركة خطة التوزيع")) }
                    }
                }
            }
        }
    }
}
