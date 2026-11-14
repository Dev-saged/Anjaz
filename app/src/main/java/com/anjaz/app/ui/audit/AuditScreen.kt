package com.anjaz.app.ui.audit

import com.anjaz.app.ui.components.EmptyState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.anjaz.app.cloud.AuthRepository
import com.anjaz.app.core.DayMath
import com.anjaz.app.data.db.AnjazDatabase
import com.anjaz.app.sync.AuditEntry
import com.anjaz.app.sync.RemoteStore
import com.anjaz.app.ui.components.AnjazCard
import com.anjaz.app.ui.components.AnjazIcon
import com.anjaz.app.ui.components.ScreenTitle
import com.anjaz.app.ui.icons.AnjazIcons
import com.anjaz.app.ui.theme.Anjaz
import com.anjaz.app.ui.theme.ltr
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

data class AuditRow(val title: String, val actor: String, val target: String, val at: Long)

fun actionLabel(action: String): String = when (action) {
    "user.create" -> "إنشاء حساب"
    "user.reset_password" -> "إعادة ضبط كلمة المرور"
    "user.disable" -> "تعطيل حساب"
    "user.enable" -> "تفعيل حساب"
    else -> action
}

@HiltViewModel
class AuditViewModel @Inject constructor(remote: RemoteStore, auth: AuthRepository, db: AnjazDatabase) : ViewModel() {
    val rows: StateFlow<List<AuditRow>> = auth.session.filterNotNull().flatMapLatest { p ->
        combine(remote.audit(p).catch { emit(emptyList()) }, db.contacts().observeAll("")) { entries: List<AuditEntry>, users ->
            val names = users.associate { it.id to it.displayName }
            entries.map { AuditRow(actionLabel(it.action), names[it.actorId] ?: "—", names[it.targetId] ?: it.targetId.take(6), it.at) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@Composable
fun AuditScreen(onBack: () -> Unit, vm: AuditViewModel = hiltViewModel()) {
    val c = Anjaz.colors
    val rows by vm.rows.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().background(c.surface).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onBack).semantics { contentDescription = "رجوع" }, contentAlignment = Alignment.Center) {
                AnjazIcon(AnjazIcons.Back, c.ink)
            }
            ScreenTitle("سجل العمليات")
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp, 8.dp, 18.dp, 40.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (rows.isEmpty()) item { EmptyState(AnjazIcons.Log, "لا توجد عمليات مسجّلة", "أو لا تتوفر لك صلاحية العرض") }
            items(rows) { r ->
                AnjazCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(r.title, color = c.ink, style = MaterialTheme.typography.titleMedium)
                        Text("${r.actor} ← ${r.target}", color = c.mute, style = MaterialTheme.typography.bodySmall)
                        Text(ltr("${DayMath.date(r.at)} ${DayMath.time(r.at)}"), color = c.mute, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
