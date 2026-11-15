package com.anjaz.app.ui.tasks

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.anjaz.app.cloud.AuthRepository
import com.anjaz.app.media.MediaCompressor
import com.anjaz.app.ui.components.Notice
import com.anjaz.app.ui.theme.Anjaz
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.tasks.await

class TaskPhoto(val id: String, val kind: String, val at: Long, val bytes: ByteArray, val by: String)

@HiltViewModel
class TaskPhotosViewModel @Inject constructor(@ApplicationContext private val ctx: Context, private val auth: AuthRepository) : ViewModel() {
    private val fs get() = FirebaseFirestore.getInstance()
    val message = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)

    fun photos(taskId: String): Flow<List<TaskPhoto>> = callbackFlow {
        val reg = fs.collection("tasks").document(taskId).collection("photos").orderBy("at").addSnapshotListener { snap, err ->
            if (err != null) close(err) else if (snap != null) trySend(snap.documents.mapNotNull { d ->
                val raw = d.getString("data") ?: return@mapNotNull null
                runCatching { TaskPhoto(d.id, d.getString("kind") ?: "AFTER", d.getLong("at") ?: 0L, Base64.decode(raw, Base64.DEFAULT), d.getString("by").orEmpty()) }.getOrNull()
            })
        }
        awaitClose { reg.remove() }
    }.catch { emit(emptyList()) }

    fun add(taskId: String, kind: String, uri: Uri) {
        viewModelScope.launch {
            busy.value = true
            val me = auth.session.first()
            val encoded = runCatching { withContext(Dispatchers.IO) { Base64.encodeToString(MediaCompressor.image(ctx, uri, 1024, 68).bytes, Base64.NO_WRAP) } }.getOrNull()
            if (me == null || encoded == null) { message.value = "تعذّرت معالجة الصورة"; busy.value = false; return@launch }
            if (encoded.length > 380_000) { message.value = "الصورة كبيرة جداً، جرّب صورة أخرى"; busy.value = false; return@launch }
            fs.collection("tasks").document(taskId).collection("photos").document(UUID.randomUUID().toString())
                .set(mapOf("by" to me.uid, "kind" to kind, "at" to System.currentTimeMillis(), "data" to encoded))
            message.update { "أُضيفت الصورة" }
            busy.value = false
        }
    }

    val me = auth.session

    fun canRemove(p: TaskPhoto, uid: String?, role: String?) = p.by == uid || (role != null && role != "FIELD_STAFF")

    fun remove(taskId: String, p: TaskPhoto) {
        viewModelScope.launch {
            runCatching { fs.collection("tasks").document(taskId).collection("photos").document(p.id).delete().await() }
                .onSuccess { message.value = "حُذفت الصورة" }
                .onFailure { message.value = "تعذّرت إزالة الصورة" }
        }
    }
}

@Composable
fun TaskPhotosSection(taskId: String, vm: TaskPhotosViewModel = hiltViewModel()) {
    val c = Anjaz.colors
    val ctx = LocalContext.current
    val photos by remember(taskId) { vm.photos(taskId) }.collectAsStateWithLifecycle(emptyList())
    val msg by vm.message.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val session by vm.me.collectAsStateWithLifecycle(null)
    var kind by remember { mutableStateOf("BEFORE") }
    var viewing by remember { mutableStateOf<TaskPhoto?>(null) }
    var removing by remember { mutableStateOf<TaskPhoto?>(null) }
    var shot by remember { mutableStateOf<Uri?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { it?.let { u -> vm.add(taskId, kind, u) } }
    val take = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) shot?.let { vm.add(taskId, kind, it) } }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("صور المهمة (قبل وبعد)", color = c.mute, style = MaterialTheme.typography.labelLarge)
        if (photos.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            photos.forEach { p ->
                val bmp = remember(p.id) { BitmapFactory.decodeByteArray(p.bytes, 0, p.bytes.size)?.asImageBitmap() }
                Box(Modifier.size(96.dp).clip(RoundedCornerShape(14.dp)).border(1.dp, c.line, RoundedCornerShape(14.dp)).background(c.card).clickable { viewing = p }) {
                    if (bmp != null) Image(bmp, "صورة المهمة", Modifier.size(96.dp), contentScale = ContentScale.Crop)
                    if (vm.canRemove(p, session?.uid, session?.role)) Box(
                        Modifier.align(Alignment.TopEnd).size(44.dp).clickable { removing = p }.semantics { contentDescription = "حذف الصورة" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.size(28.dp).clip(CircleShape).background(c.redDeep), contentAlignment = Alignment.Center) {
                            com.anjaz.app.ui.components.AnjazIcon(com.anjaz.app.ui.icons.AnjazIcons.Trash, c.onNavy, size = 16.dp)
                        }
                    }
                    Text(
                        if (p.kind == "BEFORE") "قبل" else "بعد", color = c.onNavy, style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.align(Alignment.BottomStart).padding(4.dp).clip(RoundedCornerShape(8.dp)).background(c.navy).padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf("BEFORE" to "قبل", "AFTER" to "بعد").forEach { (k, label) ->
                val on = kind == k
                Text(
                    label, color = if (on) c.onNavy else c.ink, style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp)).background(if (on) c.navy else c.card).border(1.dp, c.line, RoundedCornerShape(22.dp))
                        .clickable { kind = k }.padding(horizontal = 18.dp, vertical = 12.dp),
                )
            }
            Text(
                "التقاط", color = c.ink, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.heightIn(min = 44.dp).clickable(enabled = !busy) {
                    val f = File(ctx.cacheDir, "task-shot.jpg").also { it.parentFile?.mkdirs(); it.createNewFile() }
                    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
                    shot = uri
                    take.launch(uri)
                }.padding(horizontal = 10.dp, vertical = 12.dp),
            )
            Text("المعرض", color = c.ink, style = MaterialTheme.typography.labelLarge, modifier = Modifier.heightIn(min = 44.dp).clickable(enabled = !busy) { pick.launch("image/*") }.padding(horizontal = 10.dp, vertical = 12.dp))
        }
        viewing?.let { p ->
            val big = remember(p.id) { BitmapFactory.decodeByteArray(p.bytes, 0, p.bytes.size)?.asImageBitmap() }
            androidx.compose.ui.window.Dialog(onDismissRequest = { viewing = null }) {
                Box(Modifier.clip(RoundedCornerShape(18.dp)).background(c.card).clickable { viewing = null }) {
                    if (big != null) Image(big, "صورة المهمة", Modifier.fillMaxWidth(), contentScale = ContentScale.Fit)
                }
            }
        }
        removing?.let { p ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { removing = null },
                title = { Text("حذف الصورة؟") },
                text = { Text("ستُزال الصورة من المهمة لدى الجميع.") },
                confirmButton = { androidx.compose.material3.TextButton({ removing = null; vm.remove(taskId, p) }) { Text("حذف", color = c.redDeep) } },
                dismissButton = { androidx.compose.material3.TextButton({ removing = null }) { Text("إلغاء") } },
            )
        }
        Notice(msg, msg?.startsWith("تعذّرت") == true || msg?.startsWith("الصورة كبيرة") == true, { vm.message.value = null })
    }
}
