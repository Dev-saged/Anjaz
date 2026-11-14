package com.anjaz.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anjaz.app.BrandConfig
import com.anjaz.app.ui.theme.Anjaz
import com.anjaz.app.ui.theme.Display

@Composable
fun LockLayer(locked: Boolean, onUnlock: () -> Unit) {
    if (!locked) return
    val c = Anjaz.colors
    Column(
        Modifier.fillMaxSize().background(c.navy).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
    ) {
        Text(BrandConfig.NAME, color = c.onNavy, fontFamily = Display, style = MaterialTheme.typography.displaySmall)
        Text("التطبيق مقفل", color = c.onNavy.copy(alpha = .8f), style = MaterialTheme.typography.titleMedium)
        PrimaryButton("فتح بالبصمة أو الرمز", onClick = onUnlock)
    }
}
