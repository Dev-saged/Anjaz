@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.anjaz.app.ui.accounts

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.anjaz.app.domain.Role
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
import androidx.compose.foundation.layout.height
import com.anjaz.app.ui.components.Notice
import com.anjaz.app.ui.theme.ltr

@Composable
fun AccountsScreen(onBack: () -> Unit, vm: AccountsViewModel = hiltViewModel()) {
    val c = Anjaz.colors
    val ui by vm.ui.collectAsStateWithLifecycle()
    val users by vm.users.collectAsStateWithLifecycle()
    val schema by vm.schema.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().background(c.surface).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onBack).semantics { contentDescription = "رجوع" }, contentAlignment = Alignment.Center) {
                AnjazIcon(AnjazIcons.Back, c.ink)
            }
            ScreenTitle("الحسابات", Modifier.weight(1f))
            Box(Modifier.size(48.dp).clip(CircleShape).background(c.emerald).clickable(onClick = vm::openForm).semantics { contentDescription = "حساب جديد" }, contentAlignment = Alignment.Center) {
                AnjazIcon(AnjazIcons.Plus, c.onEmerald)
            }
        }
        if (!ui.formOpen) Notice(ui.message, ui.isError, vm::dismissMessage, Modifier.padding(horizontal = 18.dp, vertical = 4.dp))
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp, 8.dp, 18.dp, 40.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (users.isEmpty()) item { EmptyState(AnjazIcons.User, "لا توجد حسابات بعد", "الحسابات التي تنشئها ضمن نطاقك تظهر هنا") }
            items(users, key = { it.id }) { u ->
                AnjazCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(u.displayName, color = c.ink, style = MaterialTheme.typography.titleMedium)
                                Text(Role.entries.firstOrNull { it.name == u.role }?.label.orEmpty() + " · " + ltr(u.username), color = c.mute, style = MaterialTheme.typography.bodySmall)
                            }
                            if (!u.active) Tag("معطّل", Tone.Danger)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            QuietButton("إعادة ضبط كلمة المرور", Modifier.weight(1f)) { vm.reset(u) }
                            QuietButton(if (u.active) "تعطيل" else "تفعيل", Modifier.weight(0.6f)) { vm.toggle(u) }
                        }
                    }
                }
            }
        }
    }
    val s = schema
    if (ui.formOpen && s != null) ModalBottomSheet(onDismissRequest = vm::closeForm, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.surface) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(c.navy), contentAlignment = Alignment.Center) { AnjazIcon(AnjazIcons.User, c.onNavy) }
                Column {
                    ScreenTitle(s.title)
                    Text("بيانات الدخول تُعرض مرة واحدة بعد الإنشاء", color = c.mute, style = MaterialTheme.typography.bodySmall)
                }
            }
            Notice(ui.message, ui.isError, vm::dismissMessage)
            SchemaForm(s, ui.values, ui.errors, vm::change)
            val user = ui.values["username"].orEmpty().trim().lowercase()
            if (user.isNotEmpty()) Text("يدخل بـ: " + ltr(user) + if ('@' in user) "" else "  (يُضاف نطاق النظام تلقائياً)", color = c.mute, style = MaterialTheme.typography.bodySmall)
            PasswordMeter(ui.values["password"].orEmpty())
            QuietButton("توليد كلمة مرور قوية", onClick = vm::generatePassword)
            PrimaryButton(if (ui.busy) "جارٍ الإنشاء" else "إنشاء الحساب", onClick = vm::submit, enabled = !ui.busy)
        }
    }
    ui.revealedPassword?.let { (user, pass) ->
        AlertDialog(
            onDismissRequest = vm::dismissPassword,
            confirmButton = { TextButton(onClick = vm::dismissPassword, modifier = Modifier.heightIn(min = 48.dp)) { Text("تم") } },
            title = { Text("بيانات الدخول") },
            text = { Text("المستخدم: ${ltr(user)}\nكلمة المرور: ${ltr(pass)}\nسلّمها لصاحب الحساب؛ لن تظهر مرة أخرى.") },
        )
    }
}

@Composable
private fun PasswordMeter(p: String) {
    val c = Anjaz.colors
    val score = listOf(p.length >= 8, p.length >= 12, p.any { it.isDigit() } && p.any { it.isLetter() }, p.any { !it.isLetterOrDigit() } || p.any { it.isUpperCase() }).count { it }
    if (p.isEmpty()) return
    val color = when (score) { 0, 1 -> c.red; 2 -> c.amber; else -> c.emerald }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(4) { Box(Modifier.weight(1f).height(5.dp).clip(CircleShape).background(if (it < score) color else c.line)) }
        Text(when (score) { 0, 1 -> "ضعيفة"; 2 -> "متوسطة"; 3 -> "جيدة"; else -> "قوية" }, color = color, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 8.dp))
    }
}
