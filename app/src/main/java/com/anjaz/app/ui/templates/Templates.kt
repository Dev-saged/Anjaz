package com.anjaz.app.ui.templates

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.anjaz.app.data.db.AnjazDatabase
import com.anjaz.app.domain.Profile
import com.anjaz.app.domain.Role
import com.anjaz.app.schema.Choice
import com.anjaz.app.schema.FieldSchema
import com.anjaz.app.schema.FieldType
import com.anjaz.app.schema.ModuleSchema
import com.anjaz.app.schema.SchemaValidator
import com.anjaz.app.ui.components.AnjazCard
import com.anjaz.app.ui.components.AnjazIcon
import com.anjaz.app.ui.components.EmptyState
import com.anjaz.app.ui.components.Notice
import com.anjaz.app.ui.components.PrimaryButton
import com.anjaz.app.ui.components.QuietButton
import com.anjaz.app.ui.components.SchemaForm
import com.anjaz.app.ui.components.ScreenTitle
import com.anjaz.app.ui.components.Tag
import com.anjaz.app.ui.components.Tone
import com.anjaz.app.ui.icons.AnjazIcons
import com.anjaz.app.ui.theme.Anjaz
import com.anjaz.app.ui.theme.ltr
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class Template(
    val id: String, val title: String, val description: String, val priority: String, val every: String,
    val weekday: Int, val hour: Int, val assigneeId: String?, val active: Boolean,
)

val WEEKDAYS = listOf("الأحد", "الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت")

@Singleton
class TemplatesRepository @Inject constructor() {
    private val fs get() = FirebaseFirestore.getInstance()

    fun list(p: Profile): Flow<List<Template>> = callbackFlow {
        val col = fs.collection("templates")
        val q: Query = when (p.roleEnum) {
            Role.CENTRAL -> col
            Role.REGION_MANAGER, Role.REGION_ADMIN -> col.whereEqualTo("regionId", p.regionId)
            else -> col.whereEqualTo("regionId", p.regionId).whereEqualTo("squareId", p.squareId)
        }
        val reg = q.addSnapshotListener { snap, err ->
            if (err != null) close(err) else if (snap != null) trySend(snap.documents.mapNotNull { d ->
                val m = d.data ?: return@mapNotNull null
                Template(
                    d.id, m["title"] as? String ?: return@mapNotNull null, m["description"] as? String ?: "", m["priority"] as? String ?: "NORMAL",
                    m["every"] as? String ?: "DAILY", (m["weekday"] as? Number)?.toInt() ?: 0, (m["hour"] as? Number)?.toInt() ?: 7,
                    m["assigneeId"] as? String, m["active"] as? Boolean ?: false,
                )
            }.sortedBy { it.title })
        }
        awaitClose { reg.remove() }
    }.catch { emit(emptyList()) }

    fun create(p: Profile, v: Map<String, String>) {
        val data = hashMapOf<String, Any?>(
            "title" to SchemaValidator.sanitize(v.getValue("title")), "description" to SchemaValidator.sanitize(v["description"].orEmpty()),
            "priority" to (v["priority"] ?: "NORMAL"), "every" to (v["every"] ?: "DAILY"), "weekday" to (v["weekday"]?.toIntOrNull() ?: 0),
            "hour" to (v["hour"]?.toIntOrNull() ?: 7), "assigneeId" to v["assigneeId"]?.takeIf { it.isNotBlank() }, "active" to true,
            "regionId" to p.regionId, "squareId" to p.squareId, "createdBy" to p.uid, "createdAt" to System.currentTimeMillis(),
        )
        fs.collection("templates").document(UUID.randomUUID().toString()).set(data)
    }

    fun setActive(id: String, active: Boolean) { fs.collection("templates").document(id).update("active", active) }
    fun delete(id: String) { fs.collection("templates").document(id).delete() }
}

data class TemplatesUi(
    val formOpen: Boolean = false, val values: Map<String, String> = emptyMap(), val errors: Map<String, String> = emptyMap(),
    val message: String? = null, val isError: Boolean = false,
)

@HiltViewModel
class TemplatesViewModel @Inject constructor(private val repo: TemplatesRepository, auth: AuthRepository, db: AnjazDatabase) : ViewModel() {
    private val profile: StateFlow<Profile?> = auth.session.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val ui = MutableStateFlow(TemplatesUi())

    val items: StateFlow<List<Template>> = profile.filterNotNull().flatMapLatest { repo.list(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val people: StateFlow<Map<String, String>> = profile.filterNotNull().flatMapLatest { p -> db.contacts().observeContacts(p.uid) }
        .let { f -> combine(f, profile) { list, me -> list.associate { u -> u.id to u.displayName } + (me?.let { mapOf(it.uid to it.displayName) } ?: emptyMap()) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun schema(): ModuleSchema = ModuleSchema(
        id = "templates", title = "مهمة متكررة",
        fields = buildList {
            add(FieldSchema("title", "عنوان المهمة", FieldType.TEXT, required = true, maxLength = 120))
            add(FieldSchema("description", "الوصف", FieldType.MULTILINE, maxLength = 1000))
            add(FieldSchema("priority", "الأولوية", FieldType.CHOICE, required = true, choices = listOf(Choice("NORMAL", "عادية"), Choice("HIGH", "مرتفعة"), Choice("URGENT", "عاجلة"))))
            add(FieldSchema("every", "التكرار", FieldType.CHOICE, required = true, choices = listOf(Choice("DAILY", "يومي"), Choice("WEEKLY", "أسبوعي"))))
            if (ui.value.values["every"] == "WEEKLY") add(FieldSchema("weekday", "يوم الأسبوع", FieldType.CHOICE, choices = WEEKDAYS.mapIndexed { i, d -> Choice(i.toString(), d) }))
            add(FieldSchema("hour", "الساعة (0 إلى 23، بتوقيت غزة)", FieldType.NUMBER, required = true, maxLength = 2))
            if (people.value.isNotEmpty()) add(FieldSchema("assigneeId", "المُكلَّف", FieldType.CHOICE, choices = people.value.map { Choice(it.key, it.value) }))
        },
        listColumns = listOf("title"),
    )

    fun openForm() = ui.update { TemplatesUi(formOpen = true, values = mapOf("priority" to "NORMAL", "every" to "DAILY", "weekday" to "0", "hour" to "7")) }
    fun closeForm() = ui.update { it.copy(formOpen = false, errors = emptyMap()) }
    fun change(k: String, v: String) = ui.update { it.copy(values = it.values + (k to v), errors = it.errors - k) }
    fun dismiss() = ui.update { it.copy(message = null) }

    fun submit() {
        val p = profile.value ?: return
        val v = ui.value.values
        val errors = SchemaValidator.validate(schema(), v).toMutableMap()
        val hour = v["hour"]?.toIntOrNull()
        if ("hour" !in errors && (hour == null || hour !in 0..23)) errors["hour"] = "من 0 إلى 23"
        if (errors.isNotEmpty()) { ui.update { it.copy(errors = errors) }; return }
        repo.create(p, v)
        ui.update { TemplatesUi(message = "حُفظت المهمة المتكررة، وتُنشأ تلقائياً في موعدها", isError = false) }
    }

    fun toggle(t: Template) = repo.setActive(t.id, !t.active)
    fun remove(t: Template) { repo.delete(t.id); ui.update { it.copy(message = "حُذفت المهمة المتكررة", isError = false) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatesScreen(onBack: () -> Unit, vm: TemplatesViewModel = hiltViewModel()) {
    val c = Anjaz.colors
    val ui by vm.ui.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val people by vm.people.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().background(c.surface).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onBack).semantics { contentDescription = "رجوع" }, contentAlignment = Alignment.Center) { AnjazIcon(AnjazIcons.Back, c.ink) }
            ScreenTitle("المهام المتكررة", Modifier.weight(1f))
            Box(Modifier.size(48.dp).clip(CircleShape).background(c.emerald).clickable(onClick = vm::openForm).semantics { contentDescription = "مهمة متكررة جديدة" }, contentAlignment = Alignment.Center) { AnjazIcon(AnjazIcons.Plus, c.onEmerald) }
        }
        if (!ui.formOpen) Notice(ui.message, ui.isError, vm::dismiss, Modifier.padding(horizontal = 18.dp, vertical = 4.dp))
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp, 8.dp, 18.dp, 40.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (items.isEmpty()) item { EmptyState(AnjazIcons.Tasks, "لا توجد مهام متكررة", "أنشئ مهمة يومية أو أسبوعية وتُسلَّم تلقائياً في موعدها") }
            items(items, key = { it.id }) { t ->
                AnjazCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(t.title, color = c.ink, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    (if (t.every == "DAILY") "يومياً" else "كل " + WEEKDAYS.getOrElse(t.weekday) { "" }) + " · " + ltr("%02d:00".format(t.hour)) +
                                        (t.assigneeId?.let { people[it] }?.let { " · $it" } ?: ""),
                                    color = c.mute, style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Tag(if (t.active) "فعّالة" else "موقوفة", if (t.active) Tone.Success else Tone.Neutral)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            QuietButton(if (t.active) "إيقاف" else "تشغيل", Modifier.weight(1f)) { vm.toggle(t) }
                            QuietButton("حذف", Modifier.weight(1f)) { vm.remove(t) }
                        }
                    }
                }
            }
        }
    }
    if (ui.formOpen) ModalBottomSheet(onDismissRequest = vm::closeForm, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.surface) {
        val schema = vm.schema()
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ScreenTitle(schema.title)
            SchemaForm(schema, ui.values, ui.errors, vm::change)
            PrimaryButton("حفظ", onClick = vm::submit)
        }
    }
}
