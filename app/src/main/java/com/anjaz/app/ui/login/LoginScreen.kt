package com.anjaz.app.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import com.anjaz.app.ui.icons.AnjazIcons
import com.anjaz.app.ui.about.AboutCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anjaz.app.BrandConfig
import com.anjaz.app.ui.components.AmbientBackground
import com.anjaz.app.ui.components.PrimaryButton
import com.anjaz.app.ui.components.ProgressRing
import com.anjaz.app.ui.theme.Anjaz
import com.anjaz.app.ui.theme.Display

@Composable
fun LoginScreen(vm: LoginViewModel = hiltViewModel()) {
    val s by vm.ui.collectAsStateWithLifecycle()
    val c = Anjaz.colors
    Box(Modifier.fillMaxSize().background(c.surface)) {
        AmbientBackground(c.emerald.copy(alpha = .18f), c.navyHigh.copy(alpha = .16f))
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                ProgressRing(.78f, Modifier.size(88.dp), stroke = 9.dp, track = c.line, ring = if (c.dark) c.ink else c.navy, square = c.emerald)
                Text(BrandConfig.NAME, color = if (c.dark) c.ink else c.navy, fontFamily = Display, style = MaterialTheme.typography.displayLarge, modifier = Modifier.padding(top = 10.dp))
                Text(BrandConfig.TAGLINE, color = c.mute, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 22.dp))
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.card).border(1.dp, c.line, RoundedCornerShape(22.dp)).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("تسجيل الدخول", color = c.ink, style = MaterialTheme.typography.titleMedium)
                    Field(s.username, vm::username, "اسم المستخدم", AnjazIcons.User, KeyboardType.Ascii, ImeAction.Next, false)
                    Field(s.password, vm::password, "كلمة المرور", AnjazIcons.Lock, KeyboardType.Password, ImeAction.Done, true, vm::submit)
                    s.error?.let { Text(it, color = c.redDeep, style = MaterialTheme.typography.bodySmall) }
                    PrimaryButton(if (s.busy) "جارٍ الدخول…" else "دخول", vm::submit, Modifier.padding(top = 4.dp), enabled = !s.busy)
                }
                Text(
                    "نسيت كلمة المرور؟ تواصل مع مدير منطقتك لإعادة ضبطها.",
                    color = c.mute, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 14.dp),
                )
            }
            AboutCard(Modifier.padding(horizontal = 24.dp).padding(top = 8.dp, bottom = 14.dp))
        }
    }
}

@Composable
private fun Field(
    value: String, onChange: (String) -> Unit, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, type: KeyboardType, ime: ImeAction, secret: Boolean, onDone: () -> Unit = {},
) {
    val c = Anjaz.colors
    OutlinedTextField(
        value = value, onValueChange = onChange, singleLine = true, label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        leadingIcon = { Icon(icon, null, Modifier.size(20.dp), tint = c.mute) },
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = c.ink, textDirection = TextDirection.Ltr),
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = type, imeAction = ime, autoCorrectEnabled = false),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onDone() }),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = c.emerald, unfocusedBorderColor = c.line, focusedContainerColor = c.card, unfocusedContainerColor = c.card,
            focusedLabelColor = c.emeraldDeep, unfocusedLabelColor = c.mute, cursorColor = c.emerald,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}
