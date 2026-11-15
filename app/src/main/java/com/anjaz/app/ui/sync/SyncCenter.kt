package com.anjaz.app.ui.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.anjaz.app.data.db.AnjazDatabase
import com.anjaz.app.data.db.OutboxEntity
import com.anjaz.app.data.db.OutboxState
import com.anjaz.app.sync.SyncScheduler
import com.anjaz.app.ui.components.AnjazCard
import com.anjaz.app.ui.components.PrimaryButton
import com.anjaz.app.ui.components.QuietButton
import com.anjaz.app.ui.theme.Anjaz
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SyncCenterViewModel @Inject constructor(private val db: AnjazDatabase, private val sched: SyncScheduler) : ViewModel() {
    val items = db.outbox().observeOpen().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun retryAll() = viewModelScope.launch { db.outbox().retryAll(); sched.kick() }
    fun retry(key: String) = viewModelScope.launch { db.outbox().retry(key); sched.kick() }
    fun discard(key: String) = viewModelScope.launch { db.outbox().discard(key) }
}

private fun title(o: OutboxEntity): String {
    val what = when (o.entity) {
        "task" -> "مهمة"; "message" -> "رسالة"; "sos" -> "نداء استغاثة"; "region" -> "منطقة"; "square" -> "ساحة"; "camp" -> "مخيم"; "room" -> "محادثة"; "user" -> "حساب"; else -> o.entity
    }
    val act = when (o.op) {
        "create" -> "إنشاء"; "send" -> "إرسال"; "read" -> "قراءة"; "status" -> "تغيير حالة"; "update" -> "تعديل"; else -> o.op
    }
    return "$act $what"
}

private fun reason(o: OutboxEntity): String = when (o.state) {
    OutboxState.LOCAL -> "بانتظار الإرسال، يُرسل تلقائياً عند توفر الإنترنت"
    OutboxState.SENDING -> "جارٍ الإرسال الآن"
    OutboxState.FAILED -> "تعذّر الإرسال وسيعيد التطبيق المحاولة تلقائياً"
    else -> "رفضها الخادم أو القواعد: ${o.lastError.orEmpty().take(80)}"
}

/** مركز المزامنة: يشرح كل عملية معلّقة ولماذا، ويتيح إعادة المحاولة أو التجاهل بدل رقم غامض. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncSheet(onDismiss: () -> Unit, vm: SyncCenterViewModel = hiltViewModel()) {
    val c = Anjaz.colors
    val items by vm.items.collectAsStateWithLifecycle()
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surface) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("المزامنة", color = c.ink, style = MaterialTheme.typography.titleLarge)
            if (items.isEmpty()) Text("كل شيء متزامن.", color = c.mute, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 16.dp))
            else {
                Text("عمليات لم تصل للخادم بعد. المعلّقة تُرسل تلقائياً، والمرفوضة تحتاج قراراً منك.", color = c.mute, style = MaterialTheme.typography.bodySmall)
                PrimaryButton("إعادة محاولة الكل", vm::retryAll, Modifier.fillMaxWidth())
                LazyColumn(Modifier.heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(items, key = { it.idempotencyKey }) { o ->
                        AnjazCard(Modifier.fillMaxWidth()) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(title(o), color = c.ink, style = MaterialTheme.typography.titleMedium)
                                Text(reason(o), color = if (o.state == OutboxState.NEEDS_ATTENTION) c.redDeep else c.mute, style = MaterialTheme.typography.bodySmall)
                                if (o.state == OutboxState.FAILED || o.state == OutboxState.NEEDS_ATTENTION) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    QuietButton("إعادة المحاولة") { vm.retry(o.idempotencyKey) }
                                    QuietButton("تجاهل") { vm.discard(o.idempotencyKey) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
