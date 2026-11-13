package com.anjaz.app.ui.more

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anjaz.app.ui.components.AnjazIcon
import com.anjaz.app.ui.icons.AnjazIcons
import com.anjaz.app.ui.theme.Anjaz

@Composable
fun ChatPlaceholder() {
    val c = Anjaz.colors
    Column(
        Modifier.fillMaxSize().background(c.surface),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
    ) {
        AnjazIcon(AnjazIcons.Chat, c.mute, 44.dp)
        Text("المحادثات", color = c.ink, style = MaterialTheme.typography.titleLarge)
        Text("تُفعَّل مع ربط الحساب بالخادم", color = c.mute, style = MaterialTheme.typography.bodySmall)
    }
}
