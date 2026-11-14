package com.anjaz.app.ui.about

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anjaz.app.BrandConfig
import com.anjaz.app.R
import com.anjaz.app.ui.theme.Anjaz
import com.anjaz.app.ui.theme.Display

private data class Social(val key: String, val label: String, val icon: Int)

private val socials = listOf(
    Social("whatsapp", "واتساب", R.drawable.ic_social_whatsapp),
    Social("instagram", "إنستغرام", R.drawable.ic_social_instagram),
    Social("facebook", "فيسبوك", R.drawable.ic_social_facebook),
    Social("x", "إكس", R.drawable.ic_social_x),
    Social("github", "غيت هب", R.drawable.ic_social_github),
)

/** من صنع التطبيق: مركز وصغير ومريح للعين، بألوان الواجهة نفسها في الفاتح والداكن. */
@Composable
fun AboutCard(modifier: Modifier = Modifier) {
    val c = Anjaz.colors
    val context = LocalContext.current
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.width(36.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(c.line))
        Text("تصميم وتطوير", color = c.mute, fontSize = 11.sp, style = MaterialTheme.typography.labelSmall)
        Text(BrandConfig.Dev.TITLE, color = if (c.dark) c.ink else c.navy, fontFamily = Display, fontSize = 16.sp, textAlign = TextAlign.Center)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            socials.forEach { s ->
                val url = BrandConfig.Dev.LINKS.firstOrNull { it.first == s.key }?.second ?: return@forEach
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(c.card).border(1.dp, c.line, CircleShape)
                        .clickable { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
                        .semantics { role = Role.Button; contentDescription = s.label },
                    contentAlignment = Alignment.Center,
                ) { Icon(painterResource(s.icon), null, tint = c.emeraldDeep, modifier = Modifier.size(20.dp)) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Image(painterResource(R.drawable.ic_flag_ps), null, Modifier.width(18.dp).height(12.dp).clip(RoundedCornerShape(2.dp)))
            Text(BrandConfig.Dev.PLACE, color = c.ink, fontSize = 12.sp, style = MaterialTheme.typography.labelLarge)
            Icon(painterResource(R.drawable.ic_heart), null, tint = Color(0xFFE5486D), modifier = Modifier.size(12.dp))
        }
        Text("© ${BrandConfig.Dev.YEAR} ساجد العبادلة — جميع الحقوق محفوظة", color = c.mute, fontSize = 10.sp, textAlign = TextAlign.Center)
    }
}
