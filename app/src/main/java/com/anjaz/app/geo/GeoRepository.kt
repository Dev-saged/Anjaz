package com.anjaz.app.geo

import com.anjaz.app.data.db.AnjazDatabase
import com.anjaz.app.domain.Profile
import com.anjaz.app.domain.Role
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

@Singleton
class GeoRepository @Inject constructor(private val db: AnjazDatabase) {
    private val fs get() = FirebaseFirestore.getInstance()

    private fun <T> docs(q: Query, map: (String, Map<String, Any?>) -> T?): Flow<List<T>> = callbackFlow {
        val reg = q.addSnapshotListener { snap, err ->
            if (err != null) close(err) else if (snap != null) trySend(snap.documents.mapNotNull { d -> d.data?.let { map(d.id, it) } })
        }
        awaitClose { reg.remove() }
    }.catch { emit(emptyList()) }

    fun places(p: Profile): Flow<List<Place>> {
        val col = fs.collection("places")
        val base: Query = if (p.roleEnum == Role.CENTRAL) col else col.whereEqualTo("regionId", p.regionId)
        val homes: Query = when (p.roleEnum) {
            Role.CENTRAL, Role.REGION_MANAGER, Role.REGION_ADMIN -> base.whereEqualTo("kind", "HOME")
            else -> col.whereEqualTo("regionId", p.regionId).whereEqualTo("ownerId", p.uid)
        }
        return combine(docs(base.whereEqualTo("kind", "POINT"), this::place), docs(homes, this::place)) { a, b -> (a + b).distinctBy { it.id } }
    }

    fun presence(p: Profile, day: String): Flow<List<Presence>> {
        val col = fs.collection("checkins").whereEqualTo("day", day)
        val q: Query = when (p.roleEnum) {
            Role.CENTRAL -> col
            Role.REGION_MANAGER, Role.REGION_ADMIN -> col.whereEqualTo("regionId", p.regionId)
            Role.SQUARE_MANAGER -> col.whereEqualTo("regionId", p.regionId).whereEqualTo("squareId", p.squareId)
            Role.FIELD_STAFF -> col.whereEqualTo("userId", p.uid)
        }
        return docs(q) { _, m ->
            Presence(
                userId = m["userId"] as? String ?: return@docs null, name = m["name"] as? String ?: "", role = m["role"] as? String ?: "",
                lat = (m["lat"] as? Number)?.toDouble() ?: return@docs null, lng = (m["lng"] as? Number)?.toDouble() ?: return@docs null,
                at = (m["at"] as? Number)?.toLong() ?: 0L, placeName = m["placeName"] as? String, inside = m["inside"] as? Boolean ?: false,
                squareId = m["squareId"] as? String,
            )
        }.map { list -> list.groupBy { it.userId }.map { e -> e.value.maxBy { it.at } } }
    }

    /** يسجّل الوصول: أقرب نقطة ضمن نصف قطرها يعني «داخل النطاق». الكتابة تُخزَّن محلياً وتصل عند عودة الاتصال. */
    fun checkIn(p: Profile, lat: Double, lng: Double, places: List<Place>, now: Long): Pair<Place?, Boolean> {
        val nearest = places.filter { !it.isHome }.minByOrNull { GeoMath.meters(lat, lng, it.lat, it.lng) }
        val inside = nearest != null && GeoMath.meters(lat, lng, nearest.lat, nearest.lng) <= nearest.radius
        val data = hashMapOf<String, Any?>(
            "userId" to p.uid, "name" to p.displayName, "role" to p.role, "regionId" to p.regionId, "squareId" to p.squareId,
            "lat" to lat, "lng" to lng, "at" to now, "day" to GeoMath.day(now), "placeId" to nearest?.id, "placeName" to nearest?.name, "inside" to inside,
        )
        fs.collection("checkins").document(UUID.randomUUID().toString()).set(data)
        return nearest to inside
    }

    fun addPlace(p: Profile, name: String, kind: String, lat: Double, lng: Double, radius: Double, squareId: String?) {
        val data = hashMapOf<String, Any?>(
            "name" to name.trim().take(80), "kind" to kind, "lat" to lat, "lng" to lng, "radius" to radius,
            "regionId" to p.regionId, "squareId" to squareId, "ownerId" to if (kind == "HOME") p.uid else null,
            "ownerName" to if (kind == "HOME") p.displayName else null, "createdBy" to p.uid, "createdAt" to System.currentTimeMillis(),
        )
        fs.collection("places").document(UUID.randomUUID().toString()).set(data)
    }

    fun deletePlace(id: String) { fs.collection("places").document(id).delete() }

    fun doneBySquare(since: Long) = db.tasks().doneBySquare(since)
    fun overdueBySquare(now: Long) = db.tasks().overdueBySquare(now)
    fun sos() = db.sos().observeRecent()
    fun squares() = db.directory().observeSquares()

    private fun place(id: String, m: Map<String, Any?>): Place? = Place(
        id = id, name = m["name"] as? String ?: return null, kind = m["kind"] as? String ?: "POINT",
        ownerId = m["ownerId"] as? String, ownerName = m["ownerName"] as? String,
        lat = (m["lat"] as? Number)?.toDouble() ?: return null, lng = (m["lng"] as? Number)?.toDouble() ?: return null,
        radius = (m["radius"] as? Number)?.toDouble() ?: 100.0, regionId = m["regionId"] as? String, squareId = m["squareId"] as? String,
    )
}
