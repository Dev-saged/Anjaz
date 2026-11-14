package com.anjaz.app.call

import android.content.Context
import android.media.AudioManager
import com.anjaz.app.cloud.AuthRepository
import com.anjaz.app.data.db.AnjazDatabase
import com.anjaz.app.sync.ApiClient
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.audio.JavaAudioDeviceModule

enum class CallPhase { IDLE, RINGING, CONNECTING, ACTIVE, ENDED }

data class CallPeer(val name: String, val connected: Boolean)

data class CallState(
    val meetingId: String? = null,
    val phase: CallPhase = CallPhase.IDLE,
    val incoming: Boolean = false,
    val callerName: String = "",
    val peers: Map<String, CallPeer> = emptyMap(),
    val muted: Boolean = false,
    val speaker: Boolean = false,
    val note: String? = null,
)

private class Peer(val pc: PeerConnection) {
    val pendingIce = mutableListOf<IceCandidate>()
    var remoteSet = false
    var offered = false
}

/** مكالمات صوتية WebRTC: الإشارات عبر meetings/{id}/signals في Firestore، وصوت نظير لنظير (حتى 4 مشاركين بشبكة متداخلة). */
@Singleton
class CallManager @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val auth: AuthRepository,
    private val db: AnjazDatabase,
    private val api: ApiClient,
) {
    val state = MutableStateFlow(CallState())

    private val fs get() = FirebaseFirestore.getInstance()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val peers = mutableMapOf<String, Peer>()
    private var factory: PeerConnectionFactory? = null
    private var audioTrack: AudioTrack? = null
    private var listeners: List<ListenerRegistration> = emptyList()
    private var timeout: Job? = null
    private var me: String = ""
    private var joinedAt = 0L
    private val seen = HashSet<String>()
    private var names: Map<String, String> = emptyMap()

    suspend fun prepare(meetingId: String) {
        if (state.value.meetingId == meetingId && state.value.phase != CallPhase.IDLE && state.value.phase != CallPhase.ENDED) return
        val profile = auth.session.first() ?: return
        me = profile.uid
        names = db.directory().allUsers().associate { it.id to it.displayName } + (me to profile.displayName)
        val doc = runCatching { fs.collection("meetings").document(meetingId).get().await() }.getOrNull()
        val members = (doc?.get("members") as? List<*>)?.filterIsInstance<String>().orEmpty()
        val creator = doc?.getString("createdBy").orEmpty()
        if (doc == null || !doc.exists() || me !in members) {
            state.value = CallState(meetingId = meetingId, phase = CallPhase.ENDED, note = "المكالمة غير متاحة")
            return
        }
        state.value = CallState(
            meetingId = meetingId, phase = CallPhase.RINGING, incoming = creator != me, callerName = names[creator].orEmpty(),
            peers = members.filter { it != me }.associateWith { CallPeer(names[it] ?: "مشارك", false) },
        )
    }

    /** يبدأ مكالمة جديدة مع أعضاء محددين؛ يعيد المعرّف أو رسالة الخطأ. */
    suspend fun start(members: List<String>): Pair<String?, String?> {
        if (!api.configured) return null to "الخادم غير مُعدّ أو لا يوجد اتصال"
        val body = buildJsonObject { put("members", JsonArray(members.map { JsonPrimitive(it) })) }.toString()
        val res = runCatching { api.post("/v1/calls", body) }.getOrNull() ?: return null to "تعذّر الاتصال بالخادم"
        val obj = runCatching { Json.parseToJsonElement(res.second).jsonObject }.getOrNull()
        return if (res.first in 200..299) obj?.get("id")?.jsonPrimitive?.contentOrNull to null
        else null to (obj?.get("error")?.jsonPrimitive?.contentOrNull ?: "تعذّر بدء المكالمة")
    }

    fun join() {
        val id = state.value.meetingId ?: return
        if (state.value.phase != CallPhase.RINGING) return
        state.update { it.copy(phase = CallPhase.CONNECTING) }
        joinedAt = System.currentTimeMillis()
        ensureFactory()
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.mode = AudioManager.MODE_IN_COMMUNICATION
        am.isSpeakerphoneOn = false
        val col = fs.collection("meetings").document(id).collection("signals")
        listeners = listOf("all", me).map { to ->
            col.whereEqualTo("to", to).addSnapshotListener { snap, _ ->
                snap?.documentChanges?.filter { it.type == DocumentChange.Type.ADDED }?.forEach { ch ->
                    val d = ch.document
                    if (seen.add(d.id)) handle(d.getString("from").orEmpty(), d.getString("payload").orEmpty(), d.getLong("at") ?: 0L)
                }
            }
        }
        send("all", buildJsonObject { put("t", "join") }.toString())
        timeout?.cancel()
        timeout = scope.launch {
            delay(45_000)
            if (state.value.phase == CallPhase.CONNECTING && state.value.peers.none { it.value.connected }) end("لا رد على المكالمة")
        }
    }

    fun decline() {
        val id = state.value.meetingId ?: return
        if (state.value.phase == CallPhase.RINGING) {
            fs.collection("meetings").document(id).collection("signals")
                .add(mapOf("from" to me, "to" to "all", "payload" to buildJsonObject { put("t", "leave") }.toString(), "at" to System.currentTimeMillis()))
        }
        reset(null)
    }

    fun hangUp() {
        if (state.value.meetingId == null) return
        send("all", buildJsonObject { put("t", "leave") }.toString())
        end(null)
    }

    fun toggleMute() = state.update { s ->
        audioTrack?.setEnabled(s.muted)
        s.copy(muted = !s.muted)
    }

    fun toggleSpeaker() = state.update { s ->
        (ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager).isSpeakerphoneOn = !s.speaker
        s.copy(speaker = !s.speaker)
    }

    private fun ensureFactory() {
        if (factory != null) return
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(ctx).createInitializationOptions())
        val f = PeerConnectionFactory.builder().setAudioDeviceModule(JavaAudioDeviceModule.builder(ctx).createAudioDeviceModule()).createPeerConnectionFactory()
        factory = f
        audioTrack = f.createAudioTrack("anjaz-a0", f.createAudioSource(MediaConstraints()))
    }

    private fun send(to: String, payload: String) {
        val id = state.value.meetingId ?: return
        fs.collection("meetings").document(id).collection("signals")
            .add(mapOf("from" to me, "to" to to, "payload" to payload, "at" to System.currentTimeMillis()))
    }

    private fun peerFor(uid: String): Peer? {
        peers[uid]?.let { return it }
        val f = factory ?: return null
        val cfg = PeerConnection.RTCConfiguration(
            listOf("stun:stun.l.google.com:19302", "stun:stun1.l.google.com:19302").map { PeerConnection.IceServer.builder(it).createIceServer() },
        ).apply { sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN }
        val pc = f.createPeerConnection(cfg, object : PeerConnection.Observer {
            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {}
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidate(c: IceCandidate?) {
                c ?: return
                send(uid, buildJsonObject { put("t", "ice"); put("c", c.sdp); put("m", c.sdpMid ?: ""); put("i", c.sdpMLineIndex) }.toString())
            }
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            override fun onAddStream(p0: MediaStream?) {}
            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onDataChannel(p0: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onConnectionChange(s: PeerConnection.PeerConnectionState?) {
                scope.launch {
                    when (s) {
                        PeerConnection.PeerConnectionState.CONNECTED -> markConnected(uid, true)
                        PeerConnection.PeerConnectionState.FAILED, PeerConnection.PeerConnectionState.CLOSED -> markConnected(uid, false)
                        else -> {}
                    }
                }
            }
        }) ?: return null
        audioTrack?.let { pc.addTrack(it, listOf("anjaz-s0")) }
        return Peer(pc).also { peers[uid] = it }
    }

    private fun markConnected(uid: String, ok: Boolean) {
        state.update { s ->
            val peersNow = s.peers + (uid to (s.peers[uid]?.copy(connected = ok) ?: CallPeer(names[uid] ?: "مشارك", ok)))
            s.copy(peers = peersNow, phase = if (peersNow.any { it.value.connected }) CallPhase.ACTIVE else s.phase)
        }
        if (ok) timeout?.cancel()
    }

    private fun handle(from: String, payload: String, at: Long) {
        if (from == me || from.isEmpty() || at < joinedAt - 120_000) return
        val j = runCatching { Json.parseToJsonElement(payload).jsonObject }.getOrNull() ?: return
        when (j["t"]?.jsonPrimitive?.contentOrNull) {
            "join" -> {
                val p = peerFor(from) ?: return
                if (me < from && !p.offered) { p.offered = true; offer(from, p) }
            }
            "offer" -> {
                val p = peerFor(from) ?: return
                val sdp = j["sdp"]?.jsonPrimitive?.contentOrNull ?: return
                p.pc.setRemoteDescription(object : Obs() {
                    override fun onSetSuccess() {
                        p.remoteSet = true
                        flush(p)
                        p.pc.createAnswer(object : Obs() {
                            override fun onCreateSuccess(d: SessionDescription?) {
                                d ?: return
                                p.pc.setLocalDescription(Obs(), d)
                                send(from, buildJsonObject { put("t", "answer"); put("sdp", d.description) }.toString())
                            }
                        }, MediaConstraints())
                    }
                }, SessionDescription(SessionDescription.Type.OFFER, sdp))
            }
            "answer" -> {
                val p = peers[from] ?: return
                val sdp = j["sdp"]?.jsonPrimitive?.contentOrNull ?: return
                p.pc.setRemoteDescription(object : Obs() { override fun onSetSuccess() { p.remoteSet = true; flush(p) } }, SessionDescription(SessionDescription.Type.ANSWER, sdp))
            }
            "ice" -> {
                val p = peerFor(from) ?: return
                val cand = IceCandidate(j["m"]?.jsonPrimitive?.contentOrNull, j["i"]?.jsonPrimitive?.int ?: 0, j["c"]?.jsonPrimitive?.contentOrNull ?: return)
                if (p.remoteSet) p.pc.addIceCandidate(cand) else p.pendingIce += cand
            }
            "leave" -> scope.launch {
                peers.remove(from)?.pc?.close()
                state.update { s -> s.copy(peers = s.peers + (from to (s.peers[from]?.copy(connected = false) ?: CallPeer(names[from] ?: "مشارك", false)))) }
                if (peers.isEmpty() || state.value.peers.none { it.value.connected }) end("انتهت المكالمة")
            }
        }
    }

    private fun offer(to: String, p: Peer) {
        p.pc.createOffer(object : Obs() {
            override fun onCreateSuccess(d: SessionDescription?) {
                d ?: return
                p.pc.setLocalDescription(Obs(), d)
                send(to, buildJsonObject { put("t", "offer"); put("sdp", d.description) }.toString())
            }
        }, MediaConstraints())
    }

    private fun flush(p: Peer) { p.pendingIce.forEach { p.pc.addIceCandidate(it) }; p.pendingIce.clear() }

    private fun end(note: String?) {
        reset(note)
    }

    private fun reset(note: String?) {
        timeout?.cancel()
        listeners.forEach { it.remove() }
        listeners = emptyList()
        peers.values.forEach { runCatching { it.pc.close() } }
        peers.clear()
        seen.clear()
        runCatching {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.isSpeakerphoneOn = false
            am.mode = AudioManager.MODE_NORMAL
        }
        audioTrack?.setEnabled(true)
        state.value = if (note != null) CallState(phase = CallPhase.ENDED, note = note, meetingId = state.value.meetingId) else CallState()
    }

    fun clear() { if (state.value.phase == CallPhase.ENDED) state.value = CallState() }

    private open class Obs : SdpObserver {
        override fun onCreateSuccess(p0: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(p0: String?) {}
        override fun onSetFailure(p0: String?) {}
    }
}
