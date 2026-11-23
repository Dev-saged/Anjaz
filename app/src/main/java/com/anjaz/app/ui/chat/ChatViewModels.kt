package com.anjaz.app.ui.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anjaz.app.cloud.AuthRepository
import com.anjaz.app.data.ChatRepository
import com.anjaz.app.data.csv
import com.anjaz.app.data.db.MessageEntity
import com.anjaz.app.data.db.RoomEntity
import com.anjaz.app.data.db.UserEntity
import com.anjaz.app.domain.Outcome
import com.anjaz.app.domain.Profile
import com.anjaz.app.sync.SyncEngine
import com.anjaz.app.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RoomRow(val id: String, val title: String, val last: String, val at: Long, val subtitle: String = "")

/** جهة في دليل المحادثة: حساب يمكن مراسلته، أو مخيم بلا حساب يُعرض باسمه وهاتفه. */
data class ContactRow(val key: String, val title: String, val subtitle: String, val user: UserEntity? = null, val phone: String? = null)
data class ContactGroup(val title: String, val rows: List<ContactRow>)

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val repo: ChatRepository,
    auth: AuthRepository,
    private val scheduler: SyncScheduler,
    private val api: com.anjaz.app.sync.ApiClient,
    directory: com.anjaz.app.data.DirectoryRepository,
) : ViewModel() {
    private val profile: StateFlow<Profile?> = auth.session.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val canCreateGroup: StateFlow<Boolean> = profile.map { it != null && it.role != com.anjaz.app.domain.Role.FIELD_STAFF.name }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    private val contactsFlow = profile.filterNotNull().flatMapLatest { repo.contacts(it.uid) }

    val contacts: StateFlow<List<UserEntity>> = contactsFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val places = combine(directory.squares(), directory.camps()) { s, c -> s.filter { it.deletedAt == null } to c.filter { it.deletedAt == null } }

    /** وصف الشخص: دوره ومكانه (مخيم/مربع) ليكون واضحاً من يراسل. */
    private fun describe(u: UserEntity, sq: Map<String, String>, cp: Map<String, String>): String =
        listOfNotNull(com.anjaz.app.domain.Role.label(u.role).ifEmpty { null }, u.campId?.let { cp[it]?.let { n -> "مخيم $n" } }, u.squareId?.let(sq::get)).joinToString(" · ")

    val rooms: StateFlow<List<RoomRow>> = combine(repo.rooms(), contactsFlow, profile, places) { rooms, contacts, me, (sq, cp) ->
        val names = contacts.associate { it.id to it.displayName }
        val byId = contacts.associateBy { it.id }
        val sqN = sq.associate { it.id to it.name }
        val cpN = cp.associate { it.id to it.name }
        rooms.map { r ->
            val other = if (r.type == "direct") r.members.csv().firstOrNull { it != me?.uid }?.let(byId::get) else null
            RoomRow(r.id, roomTitle(r, me?.uid, names), r.lastMessage, r.lastAt, other?.let { describe(it, sqN, cpN) } ?: if (r.type == "group") "مجموعة" else "")
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** الدليل: الإدارة، ثم كل مربع بمديره وأفراده، ثم مخيماته (المرتبطة بحساب للمراسلة، والبقية بالهاتف). */
    val directoryGroups: StateFlow<List<ContactGroup>> = combine(contactsFlow, profile, places) { contacts, me, (sq, cp) ->
        if (me == null) return@combine emptyList()
        val sqN = sq.associate { it.id to it.name }
        val cpN = cp.associate { it.id to it.name }
        val row = { u: UserEntity -> ContactRow("u_" + u.id, u.displayName, describe(u, sqN, cpN), u) }
        val leads = contacts.filter { it.role in setOf("CENTRAL", "REGION_MANAGER", "REGION_ADMIN") }
        val squares = com.anjaz.app.data.DirectoryRepository.squaresInScope(me, sq).ifEmpty { sq.filter { it.id == me.squareId } }
        val groups = mutableListOf<ContactGroup>()
        if (leads.isNotEmpty()) groups += ContactGroup("الإدارة", leads.map(row))
        squares.forEach { s ->
            val people = contacts.filter { it.squareId == s.id && it.role !in setOf("CENTRAL", "REGION_MANAGER", "REGION_ADMIN") }
            val rows = people.filter { it.campId == null }.sortedByDescending { it.role == "SQUARE_MANAGER" }.map(row).toMutableList()
            cp.filter { it.squareId == s.id }.forEach { camp ->
                val linked = people.filter { it.campId == camp.id }
                if (linked.isEmpty()) rows += ContactRow("c_" + camp.id, "مخيم " + camp.name, listOfNotNull(camp.managerName?.ifBlank { null }, "لا حساب في التطبيق بعد").joinToString(" · "), phone = camp.phone?.ifBlank { null })
                else linked.forEach { u -> rows += ContactRow("u_" + u.id, "مخيم " + camp.name + " — " + u.displayName, describe(u, sqN, cpN), u) }
            }
            if (rows.isNotEmpty()) groups += ContactGroup(s.name + (s.managerName?.ifBlank { null }?.let { " · المسؤول: $it" } ?: ""), rows)
        }
        val placed = groups.flatMap { g -> g.rows.mapNotNull { it.user?.id } }.toSet()
        val rest = contacts.filter { it.id !in placed }
        if (rest.isNotEmpty()) groups += ContactGroup("آخرون", rest.map(row))
        groups
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** المجموعات تُنشأ عبر الـ Worker (يتحقق من النطاق والصلاحية)، وتصل للجهاز عبر مزامنة الغرف. يعيد رسالة خطأ أو null. */
    suspend fun createGroup(name: String, members: Set<String>): String? {
        val me = profile.value ?: return "غير مسجل"
        val clean = com.anjaz.app.schema.SchemaValidator.sanitize(name).take(80)
        if (clean.isEmpty()) return "اسم المجموعة مطلوب"
        if (members.isEmpty()) return "اختر عضواً واحداً على الأقل"
        val body = kotlinx.serialization.json.buildJsonObject {
            put("name", kotlinx.serialization.json.JsonPrimitive(clean))
            put("members", kotlinx.serialization.json.JsonArray((members + me.uid).map { kotlinx.serialization.json.JsonPrimitive(it) }))
        }.toString()
        val r = runCatching { api.post("/v1/rooms/group", body) }.getOrNull() ?: return "يلزم اتصال بالإنترنت لإنشاء مجموعة"
        scheduler.kick()
        return if (r.first in 200..299) null else runCatching { org.json.JSONObject(r.second).optString("error") }.getOrNull()?.ifEmpty { null } ?: "تعذّر إنشاء المجموعة"
    }

    suspend fun open(other: UserEntity): String? {
        val me = profile.value ?: return null
        return repo.openDirect(me, other).also { scheduler.kick() }
    }
}

fun roomTitle(r: RoomEntity, me: String?, names: Map<String, String>): String {
    if (r.type != "direct") return r.name.ifEmpty { "مجموعة" }
    val other = r.members.csv().firstOrNull { it != me } ?: return "محادثة"
    return names[other] ?: com.anjaz.app.data.decodeNames(r.memberNames)[other] ?: "محادثة"
}

data class RoomUi(val title: String, val messages: List<MessageEntity>, val me: String, val group: Boolean = false, val names: Map<String, String> = emptyMap(), val members: List<String> = emptyList())

@HiltViewModel
class ChatRoomViewModel @Inject constructor(
    saved: SavedStateHandle,
    private val repo: ChatRepository,
    private val engine: SyncEngine,
    private val scheduler: SyncScheduler,
    private val media: com.anjaz.app.media.MediaRepository,
    auth: AuthRepository,
) : ViewModel() {
    val roomId: String = checkNotNull(saved["roomId"])
    private val profile: StateFlow<Profile?> = auth.session.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val error = MutableStateFlow<String?>(null)

    val ui: StateFlow<RoomUi?> = combine(repo.messages(roomId), repo.rooms(), profile.filterNotNull().flatMapLatest { p -> repo.contacts(p.uid).map { p to it } }) { msgs, rooms, (me, contacts) ->
        val room = rooms.firstOrNull { it.id == roomId }
        val names = contacts.associate { c -> c.id to c.displayName }
        val title = room?.let { roomTitle(it, me.uid, names) }?.takeIf { it != "محادثة" }
            ?: msgs.lastOrNull { it.senderId != me.uid && it.senderName.isNotBlank() }?.senderName ?: "محادثة"
        RoomUi(title, msgs, me.uid, room?.type == "group", names, room?.members?.csv().orEmpty().filter { it != me.uid })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        engine.listenRoom(viewModelScope, roomId)
        viewModelScope.launch {
            profile.filterNotNull().collect { p ->
                repo.messages(roomId).collect { media.autoDownload(roomId, p.uid) }
            }
        }
    }

    private fun after(r: Outcome<String>) { if (r is Outcome.Failure) error.value = r.reason else scheduler.kick() }

    fun sendImage(uri: android.net.Uri) { val me = profile.value ?: return; viewModelScope.launch { after(media.sendImage(roomId, me, uri)) } }
    fun sendVideo(uri: android.net.Uri) { val me = profile.value ?: return; processing.value = true; viewModelScope.launch { after(media.sendVideo(roomId, me, uri)); processing.value = false } }
    val processing = MutableStateFlow(false)
    fun sendFile(uri: android.net.Uri) { val me = profile.value ?: return; viewModelScope.launch { after(media.sendFile(roomId, me, uri)) } }
    fun sendVoice(file: java.io.File, durMs: Long) { val me = profile.value ?: return; viewModelScope.launch { after(media.sendVoice(roomId, me, file, durMs)) } }
    fun sendLocation(lat: Double, lng: Double) { val me = profile.value ?: return; viewModelScope.launch { after(media.sendLocation(roomId, me, lat, lng)) } }
    fun download(id: String) = media.fetch(id)
    fun resend(id: String) { viewModelScope.launch { repo.resend(id); scheduler.kick() } }
    fun deleteUnsent(id: String) { viewModelScope.launch { repo.deleteUnsent(id) } }
    fun unsend(id: String) { val me = profile.value ?: return; viewModelScope.launch { repo.unsend(id, me); scheduler.kick() } }
    fun setTranscript(id: String, text: String) { viewModelScope.launch { media.saveTranscript(id, text) } }

    fun send(text: String) {
        val me = profile.value ?: return
        viewModelScope.launch {
            val r = repo.send(roomId, me, text)
            if (r is Outcome.Failure) error.value = r.reason else scheduler.kick()
        }
    }

    fun markRead() {
        val me = profile.value ?: return
        viewModelScope.launch { repo.markRead(roomId, me); scheduler.kick() }
    }
}
