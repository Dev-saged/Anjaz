@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.anjaz.app.ui.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anjaz.app.core.DayMath
import com.anjaz.app.data.csv
import com.anjaz.app.data.db.MessageEntity
import com.anjaz.app.data.db.MessageState
import com.anjaz.app.ui.components.AnjazCard
import com.anjaz.app.ui.components.AnjazIcon
import com.anjaz.app.ui.components.PrimaryButton
import com.anjaz.app.ui.components.QuietButton
import com.anjaz.app.ui.components.ScreenTitle
import com.anjaz.app.ui.icons.AnjazIcons
import com.anjaz.app.ui.theme.Anjaz
import com.anjaz.app.ui.theme.ltr
import kotlinx.coroutines.launch

@Composable
fun ChatListScreen(onOpenRoom: (String) -> Unit, vm: ChatListViewModel = hiltViewModel()) {
    val c = Anjaz.colors
    val rooms by vm.rooms.collectAsStateWithLifecycle()
    val contacts by vm.contacts.collectAsStateWithLifecycle()
    var picker by remember { mutableStateOf(false) }
    var groupMode by remember { mutableStateOf(false) }
    val canGroup by vm.canCreateGroup.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().background(c.surface).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            ScreenTitle("المحادثات")
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(c.card).clickable { picker = true }.semantics { contentDescription = "محادثة جديدة" },
                contentAlignment = Alignment.Center,
            ) { AnjazIcon(AnjazIcons.Plus, c.ink) }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp, 4.dp, 18.dp, 120.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (rooms.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AnjazIcon(AnjazIcons.Chat, c.mute, 40.dp)
                    Text("لا توجد محادثات بعد", color = c.ink, style = MaterialTheme.typography.titleMedium)
                    Text("ابدأ محادثة مع أحد زملائك من زر +", color = c.mute, style = MaterialTheme.typography.bodySmall)
                }
            }
            items(rooms, key = { it.id }) { r ->
                AnjazCard(Modifier.fillMaxWidth(), onClick = { onOpenRoom(r.id) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(r.title, color = c.ink, style = MaterialTheme.typography.titleMedium)
                            if (r.at > 0) Text(DayMath.time(r.at), color = c.mute, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(r.last.ifEmpty { "لا رسائل" }, color = c.mute, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    }
                }
            }
        }
    }
    if (picker && groupMode) GroupSheet(contacts, { groupMode = false; picker = false }) { name, ids -> vm.createGroup(name, ids) }
    else if (picker) ModalBottomSheet(onDismissRequest = { picker = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.surface) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ScreenTitle("محادثة جديدة", Modifier.padding(bottom = 8.dp))
            if (canGroup) QuietButton("مجموعة جديدة", Modifier.fillMaxWidth()) { groupMode = true }
            if (contacts.isEmpty()) Text("لا توجد جهات اتصال بعد", color = c.mute, style = MaterialTheme.typography.bodyMedium)
            contacts.forEach { u ->
                Column(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(14.dp)).clickable {
                        scope.launch { vm.open(u)?.let { id -> picker = false; onOpenRoom(id) } }
                    }.padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(u.displayName, color = c.ink, style = MaterialTheme.typography.titleMedium)
                    Text(com.anjaz.app.domain.Role.entries.firstOrNull { it.name == u.role }?.label.orEmpty(), color = c.mute, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun ChatRoomScreen(onBack: () -> Unit, vm: ChatRoomViewModel = hiltViewModel()) {
    val c = Anjaz.colors
    val ui by vm.ui.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf("") }
    var attach by remember { mutableStateOf(false) }
    val rs = remember { RecorderState() }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val stt: com.anjaz.app.stt.SttViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    val sttBusy by stt.busy.collectAsStateWithLifecycle()
    val sttError by stt.error.collectAsStateWithLifecycle()
    val pcm = remember { com.anjaz.app.stt.PcmRecorder() }
    var dictating by remember { mutableStateOf(false) }
    val micPerm = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }
    val listState = rememberLazyListState()
    val msgs = ui?.messages.orEmpty()
    LaunchedEffect(msgs.size) {
        if (msgs.isNotEmpty()) { listState.animateScrollToItem(msgs.lastIndex); vm.markRead() }
    }
    Column(Modifier.fillMaxSize().background(c.surface).statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onBack).semantics { contentDescription = "رجوع" }, contentAlignment = Alignment.Center) {
                AnjazIcon(AnjazIcons.Back, c.ink)
            }
            Text(ui?.title.orEmpty(), color = c.ink, style = MaterialTheme.typography.titleLarge)
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, contentPadding = PaddingValues(14.dp, 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(msgs, key = { it.id }) { m -> Bubble(m, mine = m.senderId == ui?.me, vm, stt, sttBusy, sender = if (ui?.group == true) ui?.names?.get(m.senderId) else null) }
        }
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            if (rs.recording) RecordingBar(rs, Modifier.weight(1f)) else Box(Modifier.size(44.dp).clip(CircleShape).clickable { attach = true }.semantics { contentDescription = "إرفاق" }, contentAlignment = Alignment.Center) { AnjazIcon(AnjazIcons.Clip, c.mute) }
            if (!rs.recording) OutlinedTextField(
                value = text, onValueChange = { if (it.length <= 4000) text = it }, placeholder = { Text("اكتب رسالة") },
                trailingIcon = {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(if (dictating) c.red else androidx.compose.ui.graphics.Color.Transparent).clickable(enabled = sttBusy == null) {
                        if (dictating) { dictating = false; stt.transcribePcm(pcm.stop()) { text = (text + " " + it).trim().take(4000) } }
                        else if (androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) micPerm.launch(android.Manifest.permission.RECORD_AUDIO)
                        else dictating = pcm.start()
                    }.semantics { contentDescription = if (dictating) "إيقاف الإملاء" else "إملاء صوتي" }, contentAlignment = Alignment.Center) {
                        if (sttBusy != null) androidx.compose.material3.CircularProgressIndicator(Modifier.size(18.dp), color = c.emerald, strokeWidth = 2.dp)
                        else AnjazIcon(AnjazIcons.Mic, if (dictating) c.onNavy else c.mute, 20.dp)
                    }
                },
                maxLines = 4, shape = RoundedCornerShape(22.dp), modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.emerald, unfocusedBorderColor = c.line, focusedContainerColor = c.card, unfocusedContainerColor = c.card, cursorColor = c.emerald),
            )
            if (text.isBlank() || rs.recording) HoldToRecord(rs, onVoice = vm::sendVoice) else Box(
                Modifier.size(52.dp).clip(CircleShape).background(c.emerald)
                    .clickable { vm.send(text); text = "" }.semantics { contentDescription = "إرسال" },
                contentAlignment = Alignment.Center,
            ) { AnjazIcon(AnjazIcons.Send, c.onEmerald) }
        }
    }
    val processing by vm.processing.collectAsStateWithLifecycle()
    if (processing) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .35f)), contentAlignment = Alignment.Center) {
        Column(Modifier.clip(RoundedCornerShape(20.dp)).background(c.card).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            androidx.compose.material3.CircularProgressIndicator(color = c.emerald)
            Text("جارٍ ضغط الفيديو…", color = c.ink, style = MaterialTheme.typography.bodyMedium)
        }
    }
    com.anjaz.app.ui.components.Notice(sttError, true, stt::clearError, Modifier.padding(12.dp).statusBarsPadding())
    ModelSheet(stt)
    if (attach) AttachSheet({ attach = false }, vm::sendImage, vm::sendVideo, vm::sendFile, vm::sendLocation)
}

@Composable
private fun Bubble(m: MessageEntity, mine: Boolean, vm: ChatRoomViewModel, stt: com.anjaz.app.stt.SttViewModel, sttBusy: String?, sender: String?) {
    val c = Anjaz.colors
    val shape = RoundedCornerShape(18.dp, 18.dp, if (mine) 4.dp else 18.dp, if (mine) 18.dp else 4.dp)
    var menu by remember { mutableStateOf(false) }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val unsent = mine && m.state != MessageState.SENT
    Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        androidx.compose.material3.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (unsent) {
                androidx.compose.material3.DropdownMenuItem(text = { Text("إعادة الإرسال") }, onClick = { menu = false; vm.resend(m.id) })
                androidx.compose.material3.DropdownMenuItem(text = { Text("حذف", color = c.redDeep) }, onClick = { menu = false; vm.deleteUnsent(m.id) })
            }
            if (m.type == "text") androidx.compose.material3.DropdownMenuItem(text = { Text("نسخ") }, onClick = { menu = false; clipboard.setText(androidx.compose.ui.text.AnnotatedString(m.text)) })
        }
        Column(
            Modifier.widthIn(max = 300.dp).clip(shape).background(if (mine) c.navy else c.card)
                .combinedClickable(onClick = {}, onLongClick = { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); menu = true })
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            val tint = if (mine) c.onNavy else c.ink
            if (!mine && sender != null) Text(sender, color = c.emerald, style = MaterialTheme.typography.labelMedium)
            if (m.type == "text") Text(m.text, color = tint, style = MaterialTheme.typography.bodyLarge)
            else MediaContent(m, tint, onDownload = { vm.download(m.id) }, onTranscribe = { stt.transcribeFile(m.id, m.localPath) { vm.setTranscript(m.id, it) } }, transcribing = sttBusy == m.id)
            Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(DayMath.time(m.createdAt), color = if (mine) c.onNavy.copy(.7f) else c.mute, style = MaterialTheme.typography.labelSmall)
                if (mine) Receipt(m)
            }
        }
    }
}

@Composable
private fun Receipt(m: MessageEntity) {
    val c = Anjaz.colors
    val readByOthers = m.readBy.csv().any { it != m.senderId }
    val tint = if (readByOthers) c.emerald else c.onNavy.copy(.7f)
    when {
        m.state == MessageState.FAILED -> Canvas(Modifier.size(14.dp).semantics { contentDescription = "فشل الإرسال، اضغط مطولاً" }) {
            drawCircle(c.red); drawLine(Color.White, Offset(size.width / 2, size.height * .25f), Offset(size.width / 2, size.height * .6f), 2.dp.toPx())
            drawCircle(Color.White, 1.2.dp.toPx(), Offset(size.width / 2, size.height * .78f))
        }
        m.state == MessageState.LOCAL -> Canvas(Modifier.size(10.dp).semantics { contentDescription = "قيد الإرسال" }) { drawCircle(tint, style = Stroke(1.5.dp.toPx())) }
        readByOthers -> Box(Modifier.semantics { contentDescription = "تمت القراءة" }) {
            AnjazIcon(AnjazIcons.Check, tint, 14.dp)
            Box(Modifier.padding(start = 5.dp)) { AnjazIcon(AnjazIcons.Check, tint, 14.dp) }
        }
        else -> AnjazIcon(AnjazIcons.Check, tint, 14.dp, "أُرسلت")
    }
}

@Composable
private fun GroupSheet(contacts: List<com.anjaz.app.data.db.UserEntity>, onDismiss: () -> Unit, onCreate: suspend (String, Set<String>) -> String?) {
    val c = Anjaz.colors
    var name by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf(setOf<String>()) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.surface) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ScreenTitle("مجموعة جديدة", Modifier.padding(bottom = 4.dp))
            OutlinedTextField(
                value = name, onValueChange = { if (it.length <= 80) name = it }, placeholder = { Text("اسم المجموعة") }, singleLine = true,
                shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.emerald, unfocusedBorderColor = c.line, focusedContainerColor = c.card, unfocusedContainerColor = c.card, cursorColor = c.emerald),
            )
            LazyColumn(Modifier.heightIn(max = 320.dp)) {
                items(contacts, key = { it.id }) { u ->
                    val on = u.id in picked
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(14.dp)).clickable { picked = if (on) picked - u.id else picked + u.id }.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(Modifier.size(24.dp).clip(CircleShape).background(if (on) c.emerald else c.line), contentAlignment = Alignment.Center) { if (on) AnjazIcon(AnjazIcons.Check, c.onEmerald, 16.dp) }
                        Text(u.displayName, color = c.ink, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            error?.let { Text(it, color = c.red, style = MaterialTheme.typography.bodySmall) }
            PrimaryButton(if (busy) "جارٍ الإنشاء" else "إنشاء", { scope.launch { busy = true; error = onCreate(name, picked); busy = false; if (error == null) onDismiss() } }, Modifier.fillMaxWidth(), enabled = !busy)
        }
    }
}

@Composable
private fun ModelSheet(stt: com.anjaz.app.stt.SttViewModel) {
    val c = Anjaz.colors
    val need by stt.needModel.collectAsStateWithLifecycle()
    val st by stt.state.collectAsStateWithLifecycle()
    if (!need) return
    ModalBottomSheet(onDismissRequest = stt::dismissModel, containerColor = c.surface) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("نموذج التعرف على الكلام", color = c.ink, style = MaterialTheme.typography.titleLarge)
            Text("يُنزَّل مرة واحدة فقط (حوالي 160 ميغابايت، يُفضَّل واي فاي). بعدها يعمل التحويل على جهازك بلا إنترنت، ولا يُرسل صوتك لأي جهة.", color = c.mute, style = MaterialTheme.typography.bodyMedium)
            when (val s = st) {
                is com.anjaz.app.stt.ModelState.Downloading -> {
                    androidx.compose.material3.LinearProgressIndicator(progress = { s.percent / 100f }, Modifier.fillMaxWidth().height(8.dp).clip(CircleShape), color = c.emerald, trackColor = c.line)
                    Text(ltr("${s.percent}") + "٪", color = c.mute, style = MaterialTheme.typography.labelLarge)
                }
                is com.anjaz.app.stt.ModelState.Failed -> {
                    Text(s.reason, color = c.redDeep, style = MaterialTheme.typography.bodySmall)
                    PrimaryButton("إعادة المحاولة", stt::downloadModel, Modifier.fillMaxWidth())
                }
                else -> PrimaryButton("تنزيل النموذج", stt::downloadModel, Modifier.fillMaxWidth())
            }
        }
    }
}
