package com.anjaz.app.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.anjaz.app.data.db.AnjazDatabase
import com.anjaz.app.ui.components.AnjazCard
import com.anjaz.app.ui.components.AnjazIcon
import com.anjaz.app.ui.components.EmptyState
import com.anjaz.app.ui.components.ScreenTitle
import com.anjaz.app.ui.icons.AnjazIcons
import com.anjaz.app.ui.theme.Anjaz
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flatMapLatest

enum class HitKind { TASK, MESSAGE, PERSON, SQUARE, CAMP }

data class Hit(val key: String, val kind: HitKind, val title: String, val sub: String, val target: String)

/** يمنع الرموز الخاصة بـ LIKE من التوسع في الاستعلام. */
fun likeArg(raw: String): String {
    val t = raw.trim().take(60).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
    return "%$t%"
}

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(db: AnjazDatabase) : ViewModel() {
    val query = MutableStateFlow("")
    private val dao = db.search()

    val hits = query.debounce(250).distinctUntilChanged().flatMapLatest { raw ->
        flow {
            if (raw.trim().length < 2) { emit(emptyList()); return@flow }
            val q = likeArg(raw)
            emit(
                dao.tasks(q).map { Hit("t${it.id}", HitKind.TASK, it.title, it.description.take(80), it.id) } +
                    dao.users(q).map { Hit("u${it.id}", HitKind.PERSON, it.displayName, it.username, it.id) } +
                    dao.squares(q).map { Hit("s${it.id}", HitKind.SQUARE, it.name, it.phone.orEmpty(), it.id) } +
                    dao.camps(q).map { Hit("c${it.id}", HitKind.CAMP, it.name, it.managerName.orEmpty(), it.id) } +
                    dao.messages(q).map { Hit("m${it.id}", HitKind.MESSAGE, (it.text.ifBlank { it.transcript }).take(90), "رسالة", it.roomId) },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

}

@Composable
fun SearchScreen(onBack: () -> Unit, onOpen: (Hit) -> Unit, vm: SearchViewModel = hiltViewModel()) {
    val c = Anjaz.colors
    val q by vm.query.collectAsStateWithLifecycle()
    val hits by vm.hits.collectAsStateWithLifecycle()
    val focus = FocusRequester()
    Column(Modifier.fillMaxSize().background(c.surface).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onBack).semantics { contentDescription = "رجوع" }, contentAlignment = Alignment.Center) { AnjazIcon(AnjazIcons.Back, c.ink) }
            ScreenTitle("بحث شامل", Modifier.weight(1f))
        }
        OutlinedTextField(
            value = q, onValueChange = { vm.query.value = it.take(60) }, singleLine = true,
            placeholder = { Text("مهمة، شخص، ساحة، مخيم، رسالة…") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp).focusRequester(focus),
        )
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp, 12.dp, 18.dp, 40.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (hits.isEmpty()) item {
                if (q.trim().length < 2) EmptyState(AnjazIcons.Search, "ابحث في كل شيء", "اكتب حرفين على الأقل")
                else EmptyState(AnjazIcons.Search, "لا نتائج", "جرّب كلمة أخرى")
            }
            items(hits, key = { it.key }) { h ->
                AnjazCard(Modifier.fillMaxWidth().clickable { onOpen(h) }) {
                    Column(Modifier.heightIn(min = 44.dp), verticalArrangement = Arrangement.Center) {
                        Text(label(h.kind), color = c.mute, style = MaterialTheme.typography.labelSmall)
                        Text(h.title, color = c.ink, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                        if (h.sub.isNotBlank()) Text(h.sub, color = c.mute, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    }
                }
            }
        }
    }
}

private fun label(k: HitKind) = when (k) {
    HitKind.TASK -> "مهمة"
    HitKind.MESSAGE -> "محادثة"
    HitKind.PERSON -> "شخص"
    HitKind.SQUARE -> "ساحة"
    HitKind.CAMP -> "مخيم"
}
