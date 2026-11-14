package com.anjaz.app.call

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anjaz.app.ui.components.rememberReduceMotion
import com.anjaz.app.ui.theme.Anjaz
import com.anjaz.app.ui.theme.Display
import com.anjaz.app.ui.theme.ltr
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay

@HiltViewModel
class CallViewModel @Inject constructor(val manager: CallManager) : ViewModel()

@Composable
fun CallScreen(id: String, members: List<String>, onClose: () -> Unit, vm: CallViewModel = hiltViewModel()) {
    val c = Anjaz.colors
    val ctx = LocalContext.current
    val state by vm.manager.state.collectAsStateWithLifecycle()
    var error by remember { mutableStateOf<String?>(null) }
    var secs by remember { mutableLongStateOf(0L) }
    val micOk = { ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) vm.manager.join() else error = "يلزم إذن الميكروفون لإجراء المكالمة" }
    val accept = { if (micOk()) vm.manager.join() else perm.launch(Manifest.permission.RECORD_AUDIO) }

    LaunchedEffect(id) {
        if (id == "new") {
            val (nid, e) = vm.manager.start(members)
            if (nid == null) { error = e; return@LaunchedEffect }
            vm.manager.prepare(nid)
            accept()
        } else vm.manager.prepare(id)
    }
    LaunchedEffect(state.phase) {
        if (state.phase == CallPhase.ACTIVE) { secs = 0; while (true) { delay(1000); secs++ } }
        if (state.phase == CallPhase.ENDED) { delay(1600); vm.manager.clear(); onClose() }
    }
    val live = state.phase == CallPhase.CONNECTING || state.phase == CallPhase.ACTIVE
    BackHandler(enabled = live) { }

    val reduce = rememberReduceMotion()
    val pulse = rememberInfiniteTransition(label = "ring")
    val k by pulse.animateFloat(1f, 1.35f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "k")
    val names = state.peers.values.joinToString("، ") { it.name }.ifBlank { state.callerName }
    val title = if (state.incoming && state.phase == CallPhase.RINGING) state.callerName.ifBlank { "مكالمة واردة" } else names.ifBlank { "مكالمة" }

    Column(Modifier.fillMaxSize().background(c.navy).statusBarsPadding().navigationBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    if (!reduce && state.phase != CallPhase.IDLE && state.phase != CallPhase.ENDED) {
                        Box(Modifier.size(132.dp).scale(k).alpha((1.35f - k).coerceIn(0f, 1f) * 1.4f).clip(CircleShape).border(2.dp, c.emerald, CircleShape))
                    }
                    Box(Modifier.size(112.dp).clip(CircleShape).background(c.navyHigh).border(3.dp, if (state.phase == CallPhase.ACTIVE) c.emerald else c.onNavy.copy(alpha = .3f), CircleShape), contentAlignment = Alignment.Center) {
                        Text(title.trim().firstOrNull()?.toString() ?: "؟", color = c.onNavy, fontFamily = Display, style = MaterialTheme.typography.displaySmall)
                    }
                }
                Text(title, color = c.onNavy, fontFamily = Display, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                Text(
                    error ?: state.note ?: when (state.phase) {
                        CallPhase.IDLE -> "جارٍ التحضير…"
                        CallPhase.RINGING -> if (state.incoming) "مكالمة صوتية واردة" else "جارٍ الاتصال…"
                        CallPhase.CONNECTING -> "جارٍ الاتصال…"
                        CallPhase.ACTIVE -> ltr("%02d:%02d".format(secs / 60, secs % 60))
                        CallPhase.ENDED -> "انتهت المكالمة"
                    },
                    color = c.onNavy.copy(alpha = .8f), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center,
                )
                if (state.peers.size > 1) state.peers.values.forEach {
                    Text((if (it.connected) "● " else "○ ") + it.name, color = if (it.connected) c.emerald else c.onNavy.copy(alpha = .6f), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        when {
            error != null || state.phase == CallPhase.ENDED -> RoundAction("إغلاق", c.card, c.ink) { vm.manager.clear(); onClose() }
            state.phase == CallPhase.RINGING && state.incoming -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                RoundAction("رفض", c.red, Color.White) { vm.manager.decline(); onClose() }
                RoundAction("رد", c.emerald, c.onEmerald) { accept() }
            }
            live -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                RoundAction(if (state.muted) "إلغاء الكتم" else "كتم", if (state.muted) c.amber else c.navyHigh, if (state.muted) c.ink else c.onNavy) { vm.manager.toggleMute() }
                RoundAction("إنهاء", c.red, Color.White) { vm.manager.hangUp(); onClose() }
                RoundAction(if (state.speaker) "السماعة" else "الأذن", if (state.speaker) c.emerald else c.navyHigh, if (state.speaker) c.onEmerald else c.onNavy) { vm.manager.toggleSpeaker() }
            }
            else -> RoundAction("إلغاء", c.red, Color.White) { vm.manager.decline(); onClose() }
        }
    }
}

@Composable
private fun RoundAction(label: String, bg: Color, fg: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(bg).clickable(onClick = onClick).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
            Text(label, color = fg, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
        }
    }
}
