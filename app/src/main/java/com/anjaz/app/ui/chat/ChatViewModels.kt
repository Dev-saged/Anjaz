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

data class RoomRow(val id: String, val title: String, val last: String, val at: Long)

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val repo: ChatRepository,
    auth: AuthRepository,
    private val scheduler: SyncScheduler,
    private val api: com.anjaz.app.sync.ApiClient,
) : ViewModel() {
    private val profile: StateFlow<Profile?> = auth.session.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val canCreateGroup: StateFlow<Boolean> = profile.map { it != null && it.role != com.anjaz.app.domain.Role.FIELD_STAFF.name }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    private val contactsFlow = profile.filterNotNull().flatMapLatest { repo.contacts(it.uid) }

    val contacts: StateFlow<List<UserEntity>> = contactsFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val rooms: StateFlow<List<RoomRow>> = combine(repo.rooms(), contactsFlow, profile) { rooms, contacts, me ->
        val names = contacts.associate { it.id to it.displayName }
        rooms.map { r -> RoomRow(r.id, roomTitle(r, me?.uid, names), r.lastMessage, r.lastAt) }
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
    val other = r.members.csv().firstOrNull { it != me }
    return other?.let { names[it] } ?: r.name.ifEmpty { "محادثة" }
}

data class RoomUi(val title: String, val messages: List<MessageEntity>, val me: String, val group: Boolean = false, val names: Map<String, String> = emptyMap())

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
        val title = room?.let { roomTitle(it, me.uid, names) } ?: "محادثة"
        RoomUi(title, msgs, me.uid, room?.type == "group", names)
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
    fun sendFile(uri: android.net.Uri) { val me = profile.value ?: return; viewModelScope.launch { after(media.sendFile(roomId, me, uri)) } }
    fun sendVoice(file: java.io.File, durMs: Long) { val me = profile.value ?: return; viewModelScope.launch { after(media.sendVoice(roomId, me, file, durMs)) } }
    fun sendLocation(lat: Double, lng: Double) { val me = profile.value ?: return; viewModelScope.launch { after(media.sendLocation(roomId, me, lat, lng)) } }
    fun download(id: String) = media.fetch(id)
    fun resend(id: String) { viewModelScope.launch { repo.resend(id); scheduler.kick() } }
    fun deleteUnsent(id: String) { viewModelScope.launch { repo.deleteUnsent(id) } }
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
