package com.anjaz.app.geo

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class Place(
    val id: String, val name: String, val kind: String, val ownerId: String?, val ownerName: String?,
    val lat: Double, val lng: Double, val radius: Double, val regionId: String?, val squareId: String?,
) {
    val isHome get() = kind == "HOME"
}

data class Presence(
    val userId: String, val name: String, val role: String, val lat: Double, val lng: Double, val at: Long,
    val placeName: String?, val inside: Boolean, val squareId: String?,
)

enum class PulseKind { SOS, DONE, LATE, HERE }

data class Pulse(val key: String, val kind: PulseKind, val lat: Double, val lng: Double, val count: Int, val label: String)

enum class MapFilter(val label: String) { ALL("الكل"), PEOPLE("الحضور"), POINTS("النقاط"), HOMES("المساكن"), ALERTS("التنبيهات") }

object GeoMath {
    fun meters(aLat: Double, aLng: Double, bLat: Double, bLng: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(bLat - aLat)
        val dLng = Math.toRadians(bLng - aLng)
        val h = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(aLat)) * cos(Math.toRadians(bLat)) * sin(dLng / 2) * sin(dLng / 2)
        return 2 * r * asin(sqrt(h))
    }

    fun day(now: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now))

    fun distanceLabel(m: Double): String = if (m < 1000) "${m.toInt()} م" else String.format(Locale.US, "%.1f كم", m / 1000)
}
