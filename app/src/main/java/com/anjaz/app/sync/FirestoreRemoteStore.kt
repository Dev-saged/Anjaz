package com.anjaz.app.sync

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
