package com.anjaz.app.ui.sos

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.Canvas
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anjaz.app.core.DayMath
import com.anjaz.app.data.SosStatus
import com.anjaz.app.data.db.SosEntity
import com.anjaz.app.ui.components.AnjazCard
import com.anjaz.app.ui.components.ScreenTitle
import com.anjaz.app.ui.components.Tag
import com.anjaz.app.ui.components.Tone
import com.anjaz.app.ui.components.rememberReduceMotion
import com.anjaz.app.ui.theme.Anjaz
import kotlinx.coroutines.launch

@SuppressLint("MissingPermission")
private fun lastKnown(ctx: Context): Pair<Double, Double>? {
    val ok = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    if (!ok) return null
    val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val loc = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
    return loc?.let { it.latitude to it.longitude }
}

@Composable
fun SosScreen(vm: SosViewModel = hiltViewModel()) {
    val c = Anjaz.colors
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val profile by vm.profile.collectAsStateWithLifecycle()
    val alerts by vm.alerts.collectAsStateWithLifecycle()
    var note by remember { mutableStateOf("") }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    val lead = profile?.canAssign == true
    val trigger = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        val loc = lastKnown(ctx)
        vm.send(note, loc?.first, loc?.second)
        note = ""
    }
    Column(Modifier.fillMaxSize().background(c.surface).statusBarsPadding()) {
        ScreenTitle("نداء الاستغاثة", Modifier.padding(horizontal = 18.dp, vertical = 12.dp))
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp, 6.dp, 18.dp, 120.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    HoldButton(onTrigger = trigger)
                    Text("اضغط مطولاً لإرسال النداء إلى مدرائك مع موقعك", color = c.mute, style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = note, onValueChange = { if (it.length <= 500) note = it }, label = { Text("ملاحظة (اختياري)") },
                        shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.red, unfocusedBorderColor = c.line, focusedContainerColor = c.card, unfocusedContainerColor = c.card, cursorColor = c.red),
                    )
                    if (lastKnown(ctx) == null) {
                        Text(
                            "تفعيل الموقع يساعد المدراء على الوصول إليك", color = c.redDeep, style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.heightIn(min = 44.dp).clickable {
                                perm.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                            }.padding(8.dp),
                        )
                    }
                }
            }
            if (alerts.isEmpty()) item { Text("لا توجد نداءات", color = c.mute, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 16.dp)) }
            items(alerts, key = { it.id }) { a -> AlertCard(a, lead) { vm.advance(a.id) } }
        }
    }
}

@Composable
private fun HoldButton(onTrigger: () -> Unit) {
    val c = Anjaz.colors
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val reduce = rememberReduceMotion()
    Box(
        Modifier.size(176.dp)
            .semantics { contentDescription = "إرسال نداء استغاثة"; onClick("إرسال") { onTrigger(); true } }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    val job = scope.launch {
                        progress.animateTo(1f, tween(if (reduce) 600 else 1200, easing = LinearEasing))
                        onTrigger()
                        progress.snapTo(0f)
                    }
                    tryAwaitRelease()
                    job.cancel()
                    scope.launch { progress.snapTo(0f) }
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = 10.dp.toPx()
            drawCircle(c.red.copy(alpha = .14f))
            drawCircle(c.red, radius = size.minDimension / 2 - w * 1.6f)
            drawArc(c.onNavy, -90f, 360f * progress.value, false, Offset(w / 2, w / 2), Size(size.width - w, size.height - w), style = Stroke(w))
        }
        Text("SOS", color = c.onNavy, fontFamily = com.anjaz.app.ui.theme.Display, style = MaterialTheme.typography.displayLarge)
    }
}

@Composable
private fun AlertCard(a: SosEntity, lead: Boolean, onAdvance: () -> Unit) {
    val c = Anjaz.colors
    val (label, tone) = when (a.status) {
        SosStatus.OPEN -> "بانتظار الاستلام" to Tone.Danger
        SosStatus.ACK -> "تم الاستلام" to Tone.Warning
        else -> "تمت المعالجة" to Tone.Success
    }
    AnjazCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(a.senderName, color = c.ink, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Tag(label, tone)
            }
            if (a.note.isNotBlank()) Text(a.note, color = c.ink, style = MaterialTheme.typography.bodyMedium)
            Text(
                DayMath.time(a.createdAt) + if (a.lat != null) "  ·  الموقع مرفق" else "", color = c.mute,
                style = MaterialTheme.typography.bodySmall,
            )
            val next = SosStatus.next(a.status)
            if (lead && next != null) {
                Text(
                    if (next == SosStatus.ACK) "تأكيد الاستلام" else "تمت المعالجة", color = c.navy.let { if (c.dark) c.ink else it },
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.heightIn(min = 44.dp).clip(CircleShape).clickable(onClick = onAdvance).padding(vertical = 10.dp),
                )
            }
        }
    }
}
