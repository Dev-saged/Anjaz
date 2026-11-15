@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.anjaz.app.ui.accounts

import com.anjaz.app.ui.components.EmptyState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.setValue
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
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            QuietButton("تعديل البيانات", Modifier.weight(1f)) { vm.openEdit(u) }
                            QuietButton("حذف", Modifier.weight(0.6f)) { vm.askDelete(u) }
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
            if (ui.isError) com.anjaz.app.ui.components.FormAlert(ui.message, title = "لم يُنشأ الحساب بعد", onDismiss = vm::dismissMessage)
            else Notice(ui.message, false, vm::dismissMessage)
            SchemaForm(s.copy(fields = s.fields.filter { it.key == "displayName" }), ui.values, ui.errors, vm::change)
            LoginField(ui.values["username"].orEmpty(), ui.errors["username"]) { vm.change("username", it) }
            PasswordField(ui.values["password"].orEmpty(), ui.errors["password"], { vm.change("password", it) }, vm::generatePassword)
            SchemaForm(s.copy(fields = s.fields.filter { it.key !in setOf("displayName", "username", "password") }), ui.values, ui.errors, vm::change)
            PrimaryButton(if (ui.busy) "جارٍ الإنشاء" else "إنشاء الحساب", onClick = vm::submit, enabled = !ui.busy)
        }
    }
    val editing = ui.edit
    if (editing != null && s != null) ModalBottomSheet(onDismissRequest = vm::closeEdit, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.surface) {
        val es = s.copy(title = "تعديل الحساب", fields = s.fields.filter { it.key in setOf("displayName", "role", "regionId", "squareId") })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ScreenTitle(es.title)
            Text(ltr(editing.username), color = c.mute, style = MaterialTheme.typography.bodySmall)
            Notice(ui.message, ui.isError, vm::dismissMessage)
            SchemaForm(es, ui.values, ui.errors, vm::change)
            PrimaryButton(if (ui.busy) "جارٍ الحفظ" else "حفظ التعديلات", onClick = vm::submitEdit, enabled = !ui.busy)
        }
    }
    ui.deleting?.let { u ->
        AlertDialog(
            onDismissRequest = vm::cancelDelete,
            confirmButton = { TextButton(onClick = vm::confirmDelete, modifier = Modifier.heightIn(min = 48.dp)) { Text("حذف نهائي", color = c.redDeep) } },
            dismissButton = { TextButton(onClick = vm::cancelDelete, modifier = Modifier.heightIn(min = 48.dp)) { Text("إلغاء") } },
            title = { Text("حذف الحساب") },
            text = { Text("سيُحذف دخول ${u.displayName} نهائياً ويُخفى من القوائم، وتبقى مهامه ورسائله السابقة في السجل. لا يمكن التراجع. للإيقاف المؤقت استخدم «تعطيل».") },
        )
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


/** اسم الدخول مع نطاق الجهة ظاهراً ثابتاً؛ لو لُصق بريد كامل يُؤخذ ما قبل @ فقط. */
@Composable
private fun LoginField(value: String, error: String?, onChange: (String) -> Unit) {
    val c = Anjaz.colors
    val domain = com.anjaz.app.BrandConfig.ACCOUNT_DOMAIN
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("اسم الدخول *", color = c.ink, style = MaterialTheme.typography.labelLarge)
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
            androidx.compose.material3.OutlinedTextField(
                value = value,
                onValueChange = { onChange(com.anjaz.app.domain.Username.localPart(it)) },
                singleLine = true, isError = error != null,
                placeholder = { Text("ahmad") },
                suffix = { Text("@$domain", color = c.mute) },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Email, autoCorrectEnabled = false),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(error ?: "لا حاجة لبريد حقيقي: اكتب اسماً بالإنجليزية ويُضاف @$domain تلقائياً.", color = if (error != null) c.redDeep else c.mute, style = MaterialTheme.typography.bodySmall)
    }
}

/** كلمة مرور بلا قيود: أي طول. مع إظهار/إخفاء وتوليد. */
@Composable
private fun PasswordField(value: String, error: String?, onChange: (String) -> Unit, onGenerate: () -> Unit) {
    val c = Anjaz.colors
    var shown by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("كلمة المرور *", color = c.ink, style = MaterialTheme.typography.labelLarge)
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
            androidx.compose.material3.OutlinedTextField(
                value = value, onValueChange = onChange, singleLine = true, isError = error != null,
                visualTransformation = if (shown) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                trailingIcon = {
                    Text(if (shown) "إخفاء" else "إظهار", color = c.navy, style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.heightIn(min = 44.dp).clickable { shown = !shown }.padding(horizontal = 10.dp, vertical = 12.dp))
                },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(autoCorrectEnabled = false),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(error ?: "أي كلمة مرور تناسبك، قصيرة أو طويلة.", color = if (error != null) c.redDeep else c.mute, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            Text("توليد", color = c.navy, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.heightIn(min = 44.dp).clickable(onClick = onGenerate).padding(horizontal = 12.dp, vertical = 12.dp))
        }
    }
}
