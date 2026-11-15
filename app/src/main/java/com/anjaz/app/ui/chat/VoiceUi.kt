package com.anjaz.app.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.anjaz.app.data.db.MediaState
import com.anjaz.app.data.db.MessageEntity
import com.anjaz.app.media.MediaProgress
import com.anjaz.app.media.VoicePlayer
import com.anjaz.app.media.VoiceRecorder
import com.anjaz.app.ui.components.AnjazIcon
import com.anjaz.app.ui.components.rememberReduceMotion
import com.anjaz.app.ui.icons.AnjazIcons
import com.anjaz.app.ui.theme.Anjaz
import com.anjaz.app.ui.theme.ltr
import kotlinx.coroutines.delay

private const val BARS = 34

/** ارتفاعات موجة ثابتة مشتقة من معرّف الرسالة كي تبقى الفقاعة نفسها شكلاً ثابتاً. */
private fun waveOf(id: String): FloatArray {
    var h = id.hashCode().toLong() and 0xffffffffL
    return FloatArray(BARS) { i ->
        h = (h * 1103515245L + 12345L) and 0x7fffffffL
        val base = .35f + (h % 1000) / 1000f * .65f
        val envelope = .55f + .45f * kotlin.math.sin(Math.PI * (i + 1) / (BARS + 1)).toFloat()
        (base * envelope).coerceIn(.18f, 1f)
    }
}

/** فقاعة صوت تفاعلية: حلقة تقدّم دائرية حول زر التشغيل، موجة قابلة للتقديم، سرعة، ونسبة التنزيل. */
@Composable
fun VoiceBubble(m: MessageEntity, ready: Boolean, tint: Color, onDownload: () -> Unit, onTranscribe: (() -> Unit)?, transcribing: Boolean) {
    val c = Anjaz.colors
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val loaded = VoicePlayer.playingId == m.id
    val playing = loaded && VoicePlayer.playing
    var pos by remember { mutableIntStateOf(0) }
    LaunchedEffect(loaded, playing) {
        if (!loaded) { pos = 0; return@LaunchedEffect }
        while (true) { pos = VoicePlayer.position(); if (!playing) break; delay(60) }
    }
    val total = m.durMs.coerceAtLeast(1L).toInt()
    val frac = if (loaded) (pos.toFloat() / total).coerceIn(0f, 1f) else 0f
    val dl = MediaProgress.percent[m.mediaId]
    val downloading = m.mediaState == MediaState.DOWNLOADING
    val wave = remember(m.id) { waveOf(m.id) }
    val haptic = LocalHapticFeedback.current
    val reduce = rememberReduceMotion()
    val press by animateFloatAsState(if (playing && !reduce) 1.06f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "p")

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(52.dp).graphicsLayer { scaleX = press; scaleY = press }.clip(CircleShape).clickable {
                    if (!reduce) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    if (ready) VoicePlayer.play(m.id, m.localPath)
                    else if (m.mediaState == MediaState.REMOTE || m.mediaState == MediaState.FAILED) onDownload()
                }.semantics { contentDescription = if (!ready) "تنزيل الرسالة الصوتية" else if (playing) "إيقاف مؤقت" else "تشغيل" },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(52.dp)) {
                    val w = 3.dp.toPx(); val inset = w / 2
                    val sz = Size(size.width - w, size.height - w)
                    drawCircle(c.emerald.copy(alpha = .18f))
                    val sweep = when {
                        !ready && downloading -> (dl ?: 0) / 100f
                        ready -> frac
                        else -> 0f
                    }
                    drawArc(c.emerald.copy(alpha = .3f), 0f, 360f, false, Offset(inset, inset), sz, style = Stroke(w))
                    if (sweep > 0f) drawArc(c.emerald, -90f, 360f * sweep, false, Offset(inset, inset), sz, style = Stroke(w, cap = StrokeCap.Round))
                    if (!ready && downloading && dl == null) drawArc(c.emerald, -90f, 100f, false, Offset(inset, inset), sz, style = Stroke(w, cap = StrokeCap.Round))
                }
                when {
                    ready -> AnjazIcon(if (playing) AnjazIcons.Pause else AnjazIcons.Play, c.emeraldDeep, 24.dp)
                    downloading -> Text(if (dl != null) ltr("$dl") else "…", color = c.emeraldDeep, style = MaterialTheme.typography.labelMedium)
                    else -> AnjazIcon(if (m.mediaState == MediaState.FAILED) AnjazIcons.Retry else AnjazIcons.Down, c.emeraldDeep, 24.dp)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Canvas(
                    Modifier.fillMaxWidth().height(34.dp).pointerInput(ready, m.id, rtl) {
                        if (!ready) return@pointerInput
                        detectTapGestures { o ->
                            val f = (o.x / size.width).let { if (rtl) 1f - it else it }.coerceIn(0f, 1f)
                            if (loaded) VoicePlayer.seek(m.id, (f * total).toInt()) else VoicePlayer.play(m.id, m.localPath, (f * total).toInt().coerceAtLeast(1))
                            pos = (f * total).toInt()
                        }
                    },
                ) {
                    val gap = 3.dp.toPx(); val bw = (size.width - gap * (BARS - 1)) / BARS
                    for (i in 0 until BARS) {
                        val idx = if (rtl) BARS - 1 - i else i
                        val h = size.height * wave[idx]
                        val done = (idx + .5f) / BARS <= frac
                        drawRoundRect(
                            if (done) c.emerald else tint.copy(alpha = .28f),
                            Offset(i * (bw + gap), (size.height - h) / 2), Size(bw, h), CornerRadius(bw / 2),
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        ltr(if (loaded) "${clock(pos.toLong())} / ${clock(m.durMs)}" else clock(m.durMs)) +
                            if (!ready) "  •  " + (if (downloading) (if (dl != null) "جارٍ التنزيل $dl٪" else "جارٍ التنزيل") else if (m.mediaState == MediaState.EXPIRED) "انتهت الصلاحية" else if (m.mediaState == MediaState.FAILED) "فشل، اضغط للإعادة" else sizeLabel(m.mediaSize)) else "",
                        color = tint.copy(.75f), style = MaterialTheme.typography.labelSmall,
                    )
                    if (loaded) Text(
                        ltr(if (VoicePlayer.speed == 1f) "1×" else "${VoicePlayer.speed}×"), color = c.emeraldDeep, style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.clip(CircleShape).background(c.emerald.copy(alpha = .16f)).clickable { VoicePlayer.cycleSpeed() }.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }
        if (m.transcript.isNotBlank()) Text(m.transcript, color = tint, style = MaterialTheme.typography.bodyMedium)
        else if (transcribing) Text("جارٍ التحويل إلى نص…", color = c.mute, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(vertical = 4.dp))
        else if (ready && onTranscribe != null) Text("تحويل إلى نص", color = c.emeraldDeep, style = MaterialTheme.typography.labelLarge, modifier = Modifier.heightIn(min = 44.dp).clickable(onClick = onTranscribe).padding(vertical = 10.dp))
    }
}

/** حالة التسجيل المشتركة بين زر الميكروفون وشريط التسجيل. */
class RecorderState {
    var recording by mutableStateOf(false)
    var locked by mutableStateOf(false)
    var cancelling by mutableStateOf(false)
    var elapsedMs by mutableLongStateOf(0L)
    var dragX by mutableFloatStateOf(0f)
    var lockProgress by mutableFloatStateOf(0f)
    val levels = mutableStateListOf<Float>()
    var level: () -> Float = { 0f }
    var onStopRequested: ((cancel: Boolean) -> Unit)? = null
}

/** شريط التسجيل: نقطة نابضة ومؤقت وموجة حية؛ والتلميح يتحرك مع الإصبع، وعند القفل زر إلغاء. */
@Composable
fun RecordingBar(rs: RecorderState, modifier: Modifier = Modifier) {
    val c = Anjaz.colors
    val reduce = rememberReduceMotion()
    LaunchedEffect(rs.recording) {
        rs.levels.clear()
        while (rs.recording) { delay(80); rs.elapsedMs += 80; rs.levels.add(rs.level()); if (rs.levels.size > 26) rs.levels.removeAt(0) }
    }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val pulse = if (reduce) 1f else rememberInfiniteTransition(label = "rec").animateFloat(.3f, 1f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "dot").value
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(c.card).padding(horizontal = 14.dp, vertical = 6.dp).heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(11.dp).clip(CircleShape).background(c.red.copy(alpha = pulse)))
        Text(ltr(clock(rs.elapsedMs)), color = c.ink, style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"))
        Canvas(Modifier.weight(1f).height(28.dp)) {
            val n = 26; val gap = 3.dp.toPx(); val bw = (size.width - gap * (n - 1)) / n
            for (i in 0 until n) {
                val v = rs.levels.getOrNull(rs.levels.size - n + i) ?: 0f
                val h = size.height * (.14f + v * .86f)
                drawRoundRect(if (rs.cancelling) c.redDeep else c.emerald, Offset(i * (bw + gap), (size.height - h) / 2), Size(bw, h), CornerRadius(bw / 2))
            }
        }
        if (rs.locked) {
            Row(
                Modifier.clip(CircleShape).clickable { rs.onStopRequested?.invoke(true) }.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                AnjazIcon(AnjazIcons.Trash, c.redDeep, 20.dp)
                Text("إلغاء", color = c.redDeep, style = MaterialTheme.typography.labelLarge)
            }
        } else {
            Text(
                if (rs.cancelling) "أفلت للإلغاء" else "اسحب للإلغاء", color = if (rs.cancelling) c.redDeep else c.mute,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.graphicsLayer { translationX = rs.dragX * .45f * (if (rtl) 1f else -1f); alpha = (1f - rs.dragX / 400f).coerceIn(.25f, 1f) },
            )
        }
    }
}

/** زر التسجيل على نمط واتساب: اضغط مطولاً، اسحب جانباً للإلغاء أو للأعلى للقفل، أفلت للإرسال. الضغطة القصيرة تعرض تلميحاً. */
@Composable
fun HoldToRecord(rs: RecorderState, onVoice: (java.io.File, Long) -> Unit, modifier: Modifier = Modifier) {
    val c = Anjaz.colors
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val recorder = remember { VoiceRecorder(ctx) }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) Toast.makeText(ctx, "اضغط مطولاً على الميكروفون للتسجيل", Toast.LENGTH_SHORT).show()
    }
    val reduce = rememberReduceMotion()
    val active = rs.recording && !rs.locked
    val scale by animateFloatAsState(if (active) 1.5f else 1f, spring(.55f, 380f), label = "mic")
    val ringPulse = if (reduce || !active) 0f else rememberInfiniteTransition(label = "ring").animateFloat(0f, 1f, infiniteRepeatable(tween(1100)), label = "r").value
    val d = LocalDensity.current
    val cancelPx = with(d) { 110.dp.toPx() }
    val lockPx = with(d) { 90.dp.toPx() }
    rs.level = { recorder.level() }

    fun finish(cancel: Boolean) {
        if (!rs.recording) return
        rs.recording = false; rs.locked = false; rs.cancelling = false; rs.dragX = 0f; rs.lockProgress = 0f
        if (!reduce) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        recorder.stop(cancel)?.let { (f, dur) -> onVoice(f, dur) }
    }
    rs.onStopRequested = { cancel -> finish(cancel) }

    Box(modifier.size(56.dp), contentAlignment = Alignment.Center) {
        if (active) Box(
            Modifier.offset(y = (-96).dp).size(width = 44.dp, height = 78.dp).clip(RoundedCornerShape(24.dp))
                .background(if (rs.lockProgress > .9f) c.emerald else c.card).padding(top = 10.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AnjazIcon(AnjazIcons.Lock, if (rs.lockProgress > .9f) c.onEmerald else c.ink, 20.dp)
                Box(Modifier.size(width = 3.dp, height = (22 * (1f - rs.lockProgress)).dp).clip(CircleShape).background(c.mute.copy(alpha = .6f)))
            }
        }
        if (active) Canvas(Modifier.size(56.dp)) {
            drawCircle(c.red.copy(alpha = (1f - ringPulse) * .4f), size.minDimension / 2 * (1f + ringPulse * .9f))
        }
        Box(
            Modifier.size(56.dp)
                .graphicsLayer {
                    val s = if (reduce) 1f else scale; scaleX = s; scaleY = s
                    translationX = if (active) rs.dragX * (if (rtl) 1f else -1f) * .5f else 0f
                    translationY = if (active) -rs.lockProgress * lockPx * .6f else 0f
                }
                .clip(CircleShape).background(if (rs.locked) c.emerald else if (rs.recording) c.red else c.emerald)
                .pointerInput(rtl) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        if (rs.locked) { finish(false); return@awaitEachGesture }
                        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                            perm.launch(Manifest.permission.RECORD_AUDIO); waitForUpOrCancellation(); return@awaitEachGesture
                        }
                        if (!recorder.start()) { Toast.makeText(ctx, "تعذّر بدء التسجيل", Toast.LENGTH_SHORT).show(); return@awaitEachGesture }
                        rs.elapsedMs = 0; rs.recording = true; rs.locked = false; rs.cancelling = false; rs.dragX = 0f; rs.lockProgress = 0f
                        if (!reduce) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        while (true) {
                            val ev = awaitPointerEvent(); val ch = ev.changes.first()
                            if (!ch.pressed) break
                            val dx = (ch.position.x - down.position.x) * (if (rtl) 1 else -1)
                            val dy = ch.position.y - down.position.y
                            rs.dragX = dx.coerceIn(0f, cancelPx * 1.2f)
                            rs.lockProgress = (-dy / lockPx).coerceIn(0f, 1f)
                            val wasCancelling = rs.cancelling
                            rs.cancelling = dx > cancelPx
                            if (rs.cancelling && !wasCancelling && !reduce) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (rs.lockProgress >= 1f && !rs.cancelling) {
                                rs.locked = true; rs.dragX = 0f; ch.consume()
                                if (!reduce) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                break
                            }
                            ch.consume()
                        }
                        if (!rs.locked) {
                            val held = SystemClock.uptimeMillis() - down.uptimeMillis
                            if (held < 600 && !rs.cancelling) {
                                finish(cancel = true)
                                Toast.makeText(ctx, "اضغط مطولاً للتسجيل، واسحب للأعلى للقفل", Toast.LENGTH_SHORT).show()
                            } else finish(cancel = rs.cancelling)
                        }
                    }
                }.semantics { contentDescription = if (rs.locked) "إرسال التسجيل" else "اضغط مطولاً لتسجيل رسالة صوتية" },
            contentAlignment = Alignment.Center,
        ) { AnjazIcon(if (rs.locked) AnjazIcons.Send else AnjazIcons.Mic, if (rs.recording && !rs.locked) c.onNavy else c.onEmerald, 26.dp) }
    }
}
