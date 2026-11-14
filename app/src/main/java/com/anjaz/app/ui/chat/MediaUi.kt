@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.anjaz.app.ui.chat

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.location.LocationManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.foundation.layout.heightIn
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import com.anjaz.app.ui.theme.ltr
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.anjaz.app.data.db.MediaState
import com.anjaz.app.data.db.MessageEntity
import com.anjaz.app.media.VoicePlayer
import com.anjaz.app.media.VoiceRecorder
import com.anjaz.app.ui.components.AnjazIcon
import com.anjaz.app.ui.icons.AnjazIcons
import com.anjaz.app.ui.theme.Anjaz
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private fun sizeLabel(b: Long) = if (b >= 1_000_000) "%.1f MB".format(b / 1_000_000.0) else "${maxOf(1, b / 1000)} KB"
private fun clock(ms: Long) = "%d:%02d".format(ms / 60_000, ms / 1000 % 60)

private fun openFile(ctx: Context, m: MessageEntity) {
    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", java.io.File(m.localPath))
    val i = Intent(Intent.ACTION_VIEW).setDataAndType(uri, m.mediaMime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { ctx.startActivity(Intent.createChooser(i, null)) }.onFailure { Toast.makeText(ctx, "لا يوجد تطبيق لفتح الملف", Toast.LENGTH_SHORT).show() }
}

/** محتوى الرسالة حسب نوعها؛ كل المحتوى يُقرأ من الجهاز دون إنترنت. */
@Composable
fun MediaContent(m: MessageEntity, tint: androidx.compose.ui.graphics.Color, onDownload: () -> Unit, onTranscribe: (() -> Unit)?) {
    val c = Anjaz.colors
    val ctx = LocalContext.current
    val ready = m.mediaState == MediaState.LOCAL && m.localPath.isNotEmpty()
    when (m.type) {
        "image" -> {
            val bmp by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, m.localPath, ready) {
                value = if (ready) withContext(Dispatchers.IO) {
                    runCatching { BitmapFactory.decodeFile(m.localPath, BitmapFactory.Options().apply { inSampleSize = 2 })?.asImageBitmap() }.getOrNull()
                } else null
            }
            val ratio = if (m.width > 0 && m.height > 0) (m.width.toFloat() / m.height).coerceIn(.6f, 1.6f) else 1f
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.line).clickable(enabled = ready) { openFile(ctx, m) }
                .semantics { contentDescription = "صورة" }, contentAlignment = Alignment.Center) {
                val b = bmp
                if (b != null) Image(b, null, Modifier.fillMaxWidth().androidxRatio(ratio), contentScale = ContentScale.Crop)
                else Box(Modifier.fillMaxWidth().androidxRatio(ratio), contentAlignment = Alignment.Center) { StateBadge(m, c.ink, onDownload) }
            }
        }
        "voice" -> {
            var tick by remember { mutableStateOf(0) }
            val playing = VoicePlayer.playingId == m.id
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(c.emerald).clickable {
                        if (ready) VoicePlayer.toggle(m.id, m.localPath) { tick++ } else if (m.mediaState == MediaState.REMOTE || m.mediaState == MediaState.FAILED) onDownload()
                    }.semantics { contentDescription = if (playing) "إيقاف" else "تشغيل" },
                    contentAlignment = Alignment.Center,
                ) { if (ready) AnjazIcon(AnjazIcons.Play, c.onEmerald) else AnjazIcon(AnjazIcons.Cloud, c.onEmerald) }
                Column(Modifier.weight(1f)) {
                    Text(if (playing) "قيد التشغيل" else "رسالة صوتية", color = tint, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                    Text(if (ready || m.durMs > 0) clock(m.durMs) else "", color = tint.copy(.7f), style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                }
                if (!ready) StateText(m, tint)
            }
            if (m.transcript.isNotBlank()) Text(m.transcript, color = tint, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
            else if (ready && onTranscribe != null) Text("تحويل إلى نص", color = c.emerald, style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                modifier = Modifier.clickable(onClick = onTranscribe).padding(vertical = 8.dp))
        }
        "file" -> Row(
            Modifier.fillMaxWidth().clickable { if (ready) openFile(ctx, m) else if (m.mediaState == MediaState.REMOTE || m.mediaState == MediaState.FAILED) onDownload() },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(c.line), contentAlignment = Alignment.Center) {
                AnjazIcon(if (ready) AnjazIcons.Log else AnjazIcons.Cloud, c.ink)
            }
            Column(Modifier.weight(1f)) {
                Text(m.mediaName, color = tint, maxLines = 2, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                Text(sizeLabel(m.mediaSize), color = tint.copy(.7f), style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
            }
            if (!ready) StateText(m, tint)
        }
        "location" -> Row(
            Modifier.fillMaxWidth().clickable {
                val u = Uri.parse("geo:${m.lat},${m.lng}?q=${m.lat},${m.lng}")
                runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, u)) }
            },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(c.red), contentAlignment = Alignment.Center) { AnjazIcon(AnjazIcons.Loc, c.onNavy) }
            Column {
                Text("موقع", color = tint, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                Text("%.5f, %.5f".format(m.lat ?: 0.0, m.lng ?: 0.0), color = tint.copy(.7f), style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private fun Modifier.androidxRatio(r: Float) = this.then(Modifier.aspectRatio(r))

@Composable
private fun StateText(m: MessageEntity, tint: androidx.compose.ui.graphics.Color) {
    val t = when (m.mediaState) {
        MediaState.DOWNLOADING -> "جارٍ التنزيل"
        MediaState.EXPIRED -> "انتهت صلاحية الملف"
        MediaState.FAILED -> "اضغط للمحاولة"
        else -> "اضغط للتنزيل"
    }
    Text(t, color = tint.copy(.8f), style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
}

@Composable
private fun StateBadge(m: MessageEntity, tint: androidx.compose.ui.graphics.Color, onDownload: () -> Unit) {
    val c = Anjaz.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(enabled = m.mediaState == MediaState.REMOTE || m.mediaState == MediaState.FAILED, onClick = onDownload).padding(16.dp)) {
        AnjazIcon(AnjazIcons.Cloud, c.mute, 28.dp)
        StateText(m, tint)
    }
}

/** لوحة الإرفاق: صورة، ملف، موقع. */
@Composable
fun AttachSheet(onDismiss: () -> Unit, onImage: (Uri) -> Unit, onFile: (Uri) -> Unit, onLocation: (Double, Double) -> Unit) {
    val c = Anjaz.colors
    val ctx = LocalContext.current
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let(onImage); onDismiss() }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(onFile); onDismiss() }
    fun locate() {
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val loc = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
        if (loc != null) onLocation(loc.latitude, loc.longitude) else Toast.makeText(ctx, "تعذّر تحديد الموقع", Toast.LENGTH_SHORT).show()
        onDismiss()
    }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) locate() else onDismiss() }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surface) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            Choice("صورة", AnjazIcons.Img) { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            Choice("ملف", AnjazIcons.Clip) { pickFile.launch(arrayOf("application/pdf", "application/zip", "text/plain", "application/msword", "application/vnd.openxmlformats-officedocument.*", "application/vnd.ms-excel")) }
            Choice("موقع", AnjazIcons.Loc) {
                if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) locate()
                else perm.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }
}

@Composable
private fun Choice(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val c = Anjaz.colors
    Column(Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(c.card), contentAlignment = Alignment.Center) { AnjazIcon(icon, c.emerald, 26.dp) }
        Text(label, color = c.ink, style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
    }
}

/** حالة التسجيل المشتركة بين زر الميكروفون وشريط التسجيل. */
class RecorderState {
    var recording by mutableStateOf(false)
    var locked by mutableStateOf(false)
    var cancelling by mutableStateOf(false)
    var elapsedMs by mutableLongStateOf(0L)
    var onStopRequested: ((cancel: Boolean) -> Unit)? = null
}

/** شريط التسجيل يحل محل حقل الكتابة: نقطة نابضة + مؤقت + تلميح السحب، وعند القفل أزرار إلغاء وإرسال. */
@Composable
fun RecordingBar(rs: RecorderState, modifier: Modifier = Modifier) {
    val c = Anjaz.colors
    val reduce = com.anjaz.app.ui.components.rememberReduceMotion()
    LaunchedEffect(rs.recording) { while (rs.recording) { delay(100); rs.elapsedMs += 100 } }
    val pulse = if (reduce) 1f else rememberInfiniteTransition(label = "rec").animateFloat(.35f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "dot").value
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(c.card).padding(horizontal = 14.dp, vertical = 6.dp).heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(c.red.copy(alpha = pulse)))
        Text(ltr(clock(rs.elapsedMs)), color = c.ink, style = androidx.compose.material3.MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"))
        if (rs.locked) {
            Box(Modifier.weight(1f))
            Text("إلغاء", color = c.redDeep, style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                modifier = Modifier.clip(CircleShape).clickable { rs.onStopRequested?.invoke(true) }.padding(horizontal = 14.dp, vertical = 12.dp))
        } else {
            Text(if (rs.cancelling) "أفلت للإلغاء" else "اسحب للإلغاء  •  للأعلى للقفل", color = if (rs.cancelling) c.redDeep else c.mute,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        }
    }
}

/** زر الميكروفون على نمط واتساب: اضغط مطولًا، اسحب جانبًا للإلغاء أو للأعلى للقفل، أفلت للإرسال. */
@Composable
fun HoldToRecord(rs: RecorderState, onVoice: (java.io.File, Long) -> Unit, modifier: Modifier = Modifier) {
    val c = Anjaz.colors
    val ctx = LocalContext.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    val recorder = remember { VoiceRecorder(ctx) }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val reduce = com.anjaz.app.ui.components.rememberReduceMotion()
    val scale by androidx.compose.animation.core.animateFloatAsState(if (rs.recording && !rs.locked) 1.45f else 1f, androidx.compose.animation.core.spring(.6f, 400f), label = "mic")
    fun finish(cancel: Boolean) {
        if (!rs.recording) return
        rs.recording = false; rs.locked = false; rs.cancelling = false
        if (!reduce) haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
        recorder.stop(cancel)?.let { (f, d) -> onVoice(f, d) }
    }
    rs.onStopRequested = { cancel -> finish(cancel) }
    val cancelPx = with(androidx.compose.ui.platform.LocalDensity.current) { 110.dp.toPx() }
    val lockPx = with(androidx.compose.ui.platform.LocalDensity.current) { 80.dp.toPx() }
    Box(
        modifier.size(52.dp).graphicsLayer { scaleX = if (reduce) 1f else scale; scaleY = if (reduce) 1f else scale }
            .clip(CircleShape).background(if (rs.locked) c.emerald else if (rs.recording) c.red else c.card)
            .pointerInput(rtl) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    if (rs.locked) { finish(false); return@awaitEachGesture }
                    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        perm.launch(Manifest.permission.RECORD_AUDIO); waitForUpOrCancellation(); return@awaitEachGesture
                    }
                    if (!recorder.start()) return@awaitEachGesture
                    rs.elapsedMs = 0; rs.recording = true; rs.locked = false; rs.cancelling = false
                    if (!reduce) haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    while (true) {
                        val ev = awaitPointerEvent(); val ch = ev.changes.first()
                        if (!ch.pressed) break
                        val dx = (ch.position.x - down.position.x) * (if (rtl) 1 else -1)
                        val dy = ch.position.y - down.position.y
                        rs.cancelling = dx > cancelPx
                        if (-dy > lockPx && !rs.cancelling) { rs.locked = true; ch.consume(); break }
                        ch.consume()
                    }
                    if (!rs.locked) finish(cancel = rs.cancelling)
                }
            }.semantics { contentDescription = if (rs.locked) "إرسال التسجيل" else "اضغط مطولًا لتسجيل رسالة صوتية" },
        contentAlignment = Alignment.Center,
    ) { AnjazIcon(if (rs.locked) AnjazIcons.Send else AnjazIcons.Mic, if (rs.recording || rs.locked) c.onNavy else c.emerald) }
}
