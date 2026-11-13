package com.anjaz.app.sync

import com.anjaz.app.data.db.CampEntity
import com.anjaz.app.data.db.MessageEntity
import com.anjaz.app.data.db.RegionEntity
import com.anjaz.app.data.db.SquareEntity
import com.anjaz.app.data.db.RoomEntity
import com.anjaz.app.data.db.SosEntity
import com.anjaz.app.data.db.TaskEntity
import com.anjaz.app.data.db.TaskEventEntity
import com.anjaz.app.data.db.UserEntity
import com.anjaz.app.domain.Profile
import com.anjaz.app.domain.Role
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirestoreRemoteStore : RemoteStore {
    private val fs get() = FirebaseFirestore.getInstance()
    override val enabled = true

    private suspend fun <T> guard(block: suspend () -> T): T = try {
        block()
    } catch (e: FirebaseFirestoreException) {
        if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) throw RemoteDenied(e.message.orEmpty()) else throw e
    }

    override suspend fun createTask(task: TaskEntity) = guard {
        fs.collection("tasks").document(task.id).set(taskMap(task)).await()
        Unit
    }

    override suspend fun updateTaskStatus(taskId: String, status: String, updatedAt: Long) = guard {
        fs.collection("tasks").document(taskId).update(mapOf("status" to status, "updatedAt" to updatedAt)).await()
        Unit
    }

    override suspend fun appendEvent(event: TaskEventEntity) = guard {
        val data = hashMapOf<String, Any?>(
            "actorId" to event.actorId, "fromStatus" to event.fromStatus, "toStatus" to event.toStatus,
            "note" to event.note, "at" to event.at,
        )
        fs.collection("tasks").document(event.taskId).collection("events").document(event.id).set(data).await()
        Unit
    }

    override fun tasks(profile: Profile): Flow<List<TaskEntity>> {
        val col = fs.collection("tasks")
        val q: Query = when (profile.roleEnum) {
            Role.CENTRAL -> col
            Role.REGION_MANAGER, Role.REGION_ADMIN -> col.whereEqualTo("regionId", profile.regionId)
            Role.SQUARE_MANAGER -> col.whereEqualTo("regionId", profile.regionId).whereEqualTo("squareId", profile.squareId)
            Role.FIELD_STAFF -> col.whereEqualTo("assigneeId", profile.uid)
        }
        return snapshots(q) { snap -> snap.documents.mapNotNull { d -> d.data?.let { taskFrom(d.id, it) } } }
    }

    override fun users(profile: Profile): Flow<List<UserEntity>> {
        val col = fs.collection("users")
        val q: Query = when (profile.roleEnum) {
            Role.CENTRAL -> col
            Role.REGION_MANAGER, Role.REGION_ADMIN -> col.whereEqualTo("regionId", profile.regionId)
            Role.SQUARE_MANAGER -> col.whereEqualTo("regionId", profile.regionId).whereEqualTo("squareId", profile.squareId)
            Role.FIELD_STAFF -> col.whereEqualTo(com.google.firebase.firestore.FieldPath.documentId(), profile.uid)
        }
        return snapshots(q) { snap ->
            snap.documents.mapNotNull { d ->
                d.data?.let {
                    UserEntity(
                        id = d.id, username = it["username"] as? String ?: "", displayName = it["displayName"] as? String ?: "",
                        role = it["role"] as? String ?: "FIELD_STAFF", regionId = it["regionId"] as? String, squareId = it["squareId"] as? String,
                        active = it["active"] as? Boolean ?: false, updatedAt = (it["updatedAt"] as? Number)?.toLong() ?: 0L,
                    )
                }
            }
        }
    }

    override suspend fun createRegion(region: RegionEntity) = guard {
        val data = hashMapOf<String, Any?>("name" to region.name, "active" to region.active, "updatedAt" to region.updatedAt)
        region.phone?.let { data["phone"] = it }
        fs.collection("regions").document(region.id).set(data).await()
        Unit
    }

    override suspend fun createSquare(square: SquareEntity) = guard {
        val data = hashMapOf<String, Any?>("regionId" to square.regionId, "name" to square.name, "updatedAt" to square.updatedAt)
        square.idNumber?.let { data["idNumber"] = it }
        square.phone?.let { data["phone"] = it }
        fs.collection("squares").document(square.id).set(data).await()
        Unit
    }

    override suspend fun createCamp(camp: CampEntity) = guard {
        val data = hashMapOf<String, Any?>("regionId" to camp.regionId, "squareId" to camp.squareId, "name" to camp.name, "updatedAt" to camp.updatedAt)
        camp.managerName?.let { data["managerName"] = it }
        camp.idNumber?.let { data["idNumber"] = it }
        camp.phone?.let { data["phone"] = it }
        fs.collection("camps").document(camp.id).set(data).await()
        Unit
    }

    override fun regions(profile: Profile): Flow<List<RegionEntity>> {
        val col = fs.collection("regions")
        val q: Query = if (profile.roleEnum == Role.CENTRAL) col else col.whereEqualTo(com.google.firebase.firestore.FieldPath.documentId(), profile.regionId ?: "-")
        return snapshots(q) { snap ->
            snap.documents.mapNotNull { d ->
                d.data?.let {
                    RegionEntity(d.id, it["name"] as? String ?: "", it["managerId"] as? String, it["phone"] as? String, it["active"] as? Boolean ?: true, (it["updatedAt"] as? Number)?.toLong() ?: 0L)
                }
            }
        }
    }

    override fun squares(profile: Profile): Flow<List<SquareEntity>> {
        val col = fs.collection("squares")
        val q: Query = when (profile.roleEnum) {
            Role.CENTRAL -> col
            else -> col.whereEqualTo("regionId", profile.regionId ?: "-")
        }
        return snapshots(q) { snap ->
            snap.documents.mapNotNull { d ->
                d.data?.let {
                    SquareEntity(d.id, it["regionId"] as? String ?: "", it["name"] as? String ?: "", it["managerId"] as? String, it["idNumber"] as? String, it["phone"] as? String, (it["updatedAt"] as? Number)?.toLong() ?: 0L)
                }
            }
        }
    }

    override fun camps(profile: Profile): Flow<List<CampEntity>> {
        val col = fs.collection("camps")
        val q: Query = when (profile.roleEnum) {
            Role.CENTRAL -> col
            Role.REGION_MANAGER, Role.REGION_ADMIN -> col.whereEqualTo("regionId", profile.regionId ?: "-")
            else -> col.whereEqualTo("regionId", profile.regionId ?: "-").whereEqualTo("squareId", profile.squareId ?: "-")
        }
        return snapshots(q) { snap ->
            snap.documents.mapNotNull { d ->
                d.data?.let {
                    CampEntity(d.id, it["regionId"] as? String ?: "", it["squareId"] as? String ?: "", it["name"] as? String ?: "", it["managerName"] as? String, it["idNumber"] as? String, it["phone"] as? String, (it["updatedAt"] as? Number)?.toLong() ?: 0L)
                }
            }
        }
    }

    override fun audit(profile: Profile): Flow<List<AuditEntry>> {
        val col = fs.collection("audit")
        val q: Query = when (profile.roleEnum) {
            Role.CENTRAL -> col.orderBy("at", Query.Direction.DESCENDING).limit(200)
            Role.REGION_MANAGER, Role.REGION_ADMIN -> col.whereEqualTo("regionId", profile.regionId ?: "-").limit(200)
            else -> return kotlinx.coroutines.flow.emptyFlow()
        }
        return snapshots(q) { snap ->
            snap.documents.mapNotNull { d ->
                d.data?.let { AuditEntry(d.id, it["actorId"] as? String ?: "", it["action"] as? String ?: "", it["targetId"] as? String ?: "", (it["at"] as? Number)?.toLong() ?: 0L) }
            }.sortedByDescending { it.at }
        }
    }

    override suspend fun createSos(sos: SosEntity) = guard {
        val data = hashMapOf<String, Any?>(
            "senderId" to sos.senderId, "senderName" to sos.senderName, "regionId" to sos.regionId, "squareId" to sos.squareId,
            "status" to sos.status, "note" to sos.note, "lat" to sos.lat, "lng" to sos.lng,
            "createdAt" to sos.createdAt, "updatedAt" to sos.updatedAt,
        )
        fs.collection("sos").document(sos.id).set(data).await()
        Unit
    }

    override suspend fun updateSosStatus(id: String, status: String, by: String?, updatedAt: Long) = guard {
        fs.collection("sos").document(id).update(mapOf("status" to status, "ackBy" to by, "updatedAt" to updatedAt)).await()
        Unit
    }

    override suspend fun createRoom(room: RoomEntity) = guard {
        val data = hashMapOf<String, Any?>(
            "type" to room.type, "members" to room.members.split(','), "lastMessage" to "", "lastAt" to room.lastAt,
        )
        fs.collection("rooms").document(room.id).set(data).await()
        Unit
    }

    override suspend fun sendMessage(message: MessageEntity, roomLast: String) = guard {
        val room = fs.collection("rooms").document(message.roomId)
        val msg = room.collection("messages").document(message.id)
        val data = hashMapOf<String, Any?>(
            "senderId" to message.senderId, "type" to message.type, "text" to message.text,
            "createdAt" to message.createdAt, "readBy" to message.readBy.split(',').filter { it.isNotEmpty() },
        )
        fs.runBatch { b ->
            b.set(msg, data)
            b.update(room, mapOf("lastMessage" to roomLast, "lastAt" to message.createdAt))
        }.await()
        Unit
    }

    override suspend fun markRead(roomId: String, messageId: String, uid: String) = guard {
        fs.collection("rooms").document(roomId).collection("messages").document(messageId)
            .update("readBy", com.google.firebase.firestore.FieldValue.arrayUnion(uid)).await()
        Unit
    }

    override fun sos(profile: Profile): Flow<List<SosEntity>> {
        val col = fs.collection("sos")
        val q: Query = when (profile.roleEnum) {
            Role.CENTRAL -> col
            Role.REGION_MANAGER, Role.REGION_ADMIN -> col.whereEqualTo("regionId", profile.regionId)
            Role.SQUARE_MANAGER -> col.whereEqualTo("regionId", profile.regionId).whereEqualTo("squareId", profile.squareId)
            Role.FIELD_STAFF -> col.whereEqualTo("senderId", profile.uid)
        }
        return snapshots(q) { snap ->
            snap.documents.mapNotNull { d ->
                d.data?.let {
                    SosEntity(
                        id = d.id, senderId = it["senderId"] as? String ?: "", senderName = it["senderName"] as? String ?: "",
                        regionId = it["regionId"] as? String, squareId = it["squareId"] as? String,
                        status = it["status"] as? String ?: "OPEN", note = it["note"] as? String ?: "",
                        lat = (it["lat"] as? Number)?.toDouble(), lng = (it["lng"] as? Number)?.toDouble(),
                        createdAt = (it["createdAt"] as? Number)?.toLong() ?: 0L, updatedAt = (it["updatedAt"] as? Number)?.toLong() ?: 0L,
                    )
                }
            }
        }
    }

    override fun rooms(profile: Profile): Flow<List<RoomEntity>> =
        snapshots(fs.collection("rooms").whereArrayContains("members", profile.uid)) { snap ->
            snap.documents.mapNotNull { d ->
                d.data?.let {
                    @Suppress("UNCHECKED_CAST")
                    val members = (it["members"] as? List<String>).orEmpty().sorted()
                    RoomEntity(
                        d.id, it["type"] as? String ?: "direct", it["name"] as? String ?: "", members.joinToString(","),
                        it["lastMessage"] as? String ?: "", (it["lastAt"] as? Number)?.toLong() ?: 0L,
                    )
                }
            }
        }

    override fun messages(roomId: String): Flow<List<MessageEntity>> =
        snapshots(fs.collection("rooms").document(roomId).collection("messages").orderBy("createdAt").limitToLast(200)) { snap ->
            snap.documents.mapNotNull { d ->
                d.data?.let {
                    @Suppress("UNCHECKED_CAST")
                    val readBy = (it["readBy"] as? List<String>).orEmpty()
                    MessageEntity(
                        d.id, roomId, it["senderId"] as? String ?: "", it["type"] as? String ?: "text", it["text"] as? String ?: "",
                        (it["createdAt"] as? Number)?.toLong() ?: 0L, readBy.joinToString(","), "SENT",
                    )
                }
            }
        }

    private fun <T> snapshots(q: Query, map: (QuerySnapshot) -> T): Flow<T> = callbackFlow {
        val reg = q.addSnapshotListener { snap, err ->
            if (err != null) close(err) else if (snap != null) trySend(map(snap))
        }
        awaitClose { reg.remove() }
    }

    companion object {
        fun taskMap(t: TaskEntity): Map<String, Any?> = mapOf(
            "title" to t.title, "description" to t.description, "priority" to t.priority, "status" to t.status,
            "regionId" to t.regionId, "squareId" to t.squareId, "assigneeId" to t.assigneeId, "createdBy" to t.createdBy,
            "deadline" to t.deadline, "createdAt" to t.createdAt, "updatedAt" to t.updatedAt,
        )

        fun taskFrom(id: String, m: Map<String, Any?>): TaskEntity = TaskEntity(
            id = id, number = (m["number"] as? Number)?.toLong(), title = m["title"] as? String ?: "",
            description = m["description"] as? String ?: "", createdBy = m["createdBy"] as? String ?: "",
            regionId = m["regionId"] as? String, squareId = m["squareId"] as? String, assigneeId = m["assigneeId"] as? String,
            priority = m["priority"] as? String ?: "NORMAL", status = m["status"] as? String ?: "NEW",
            deadline = (m["deadline"] as? Number)?.toLong(), createdAt = (m["createdAt"] as? Number)?.toLong() ?: 0L,
            updatedAt = (m["updatedAt"] as? Number)?.toLong() ?: 0L, archived = false,
        )
    }
}
