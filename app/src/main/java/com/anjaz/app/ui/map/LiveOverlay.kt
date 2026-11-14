package com.anjaz.app.ui.map

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.view.MotionEvent
import com.anjaz.app.geo.MapFilter
import com.anjaz.app.geo.Place
import com.anjaz.app.geo.Presence
import com.anjaz.app.geo.Pulse
import com.anjaz.app.geo.PulseKind
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

class MapPalette(val navy: Int, val emerald: Int, val amber: Int, val red: Int, val ink: Int, val card: Int)

/** طبقة الخريطة الحية: نقاط ومساكن وحضور مع نبض متحرك، كلها مرسومة مباشرة على Canvas. */
class LiveOverlay(private val density: Float, private val palette: MapPalette) : Overlay() {
    var places: List<Place> = emptyList()
    var people: List<Presence> = emptyList()
    var pulses: List<Pulse> = emptyList()
    var filter: MapFilter = MapFilter.ALL
    var selected: String? = null
    var now: Long = 0L
    var me: Pair<Double, Double>? = null
    var reduceMotion = false
    var onTap: (String?) -> Unit = {}

    private class Hit(val key: String, val x: Float, val y: Float, val r: Float)

    private val hits = ArrayList<Hit>()
    private val pt = Point()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; isFakeBoldText = true }
    private fun dp(v: Float) = v * density

    private fun ring(c: Canvas, x: Float, y: Float, r: Float, color: Int, alpha: Float, width: Float) {
        line.color = color; line.alpha = (alpha * 255).toInt().coerceIn(0, 255); line.strokeWidth = width
        c.drawCircle(x, y, r, line)
    }

    private fun disc(c: Canvas, x: Float, y: Float, r: Float, color: Int, alpha: Float = 1f) {
        fill.color = color; fill.alpha = (alpha * 255).toInt().coerceIn(0, 255)
        c.drawCircle(x, y, r, fill)
    }

    private fun ripple(c: Canvas, x: Float, y: Float, base: Float, color: Int, phase: Float, strong: Boolean) {
        if (reduceMotion) { ring(c, x, y, base * 1.7f, color, 0.4f, dp(2f)); return }
        for (i in 0..1) {
            val p = (phase + i * 0.5f) % 1f
            ring(c, x, y, base + p * base * (if (strong) 3.2f else 2.2f), color, (1f - p) * 0.6f, dp(2f))
        }
    }

    private fun label(c: Canvas, s: String, x: Float, y: Float, size: Float) {
        text.textSize = dp(size)
        text.style = Paint.Style.STROKE; text.strokeWidth = dp(3f); text.color = palette.card
        c.drawText(s, x, y, text)
        text.style = Paint.Style.FILL; text.color = palette.ink
        c.drawText(s, x, y, text)
    }

    override fun draw(c: Canvas, map: MapView, shadow: Boolean) {
        if (shadow) return
        val proj = map.projection
        hits.clear()
        val phase = (System.currentTimeMillis() % 2600L) / 2600f
        val mpp = 156543.03392 * cos(Math.toRadians(map.mapCenter.latitude)) / 2.0.pow(map.zoomLevelDouble)
        val showPoints = filter == MapFilter.ALL || filter == MapFilter.POINTS
        val showHomes = filter == MapFilter.ALL || filter == MapFilter.HOMES
        val showPeople = filter == MapFilter.ALL || filter == MapFilter.PEOPLE
        val showAlerts = filter == MapFilter.ALL || filter == MapFilter.ALERTS
        var animate = false

        places.filter { if (it.isHome) showHomes else showPoints }.forEach { p ->
            proj.toPixels(GeoPoint(p.lat, p.lng), pt)
            val x = pt.x.toFloat(); val y = pt.y.toFloat()
            val color = if (p.isHome) palette.amber else palette.emerald
            val r = (p.radius / mpp).toFloat().coerceAtLeast(dp(16f))
            disc(c, x, y, r, color, 0.14f)
            ring(c, x, y, r, color, 0.85f, dp(1.5f))
            disc(c, x, y, dp(9f), palette.card)
            disc(c, x, y, dp(6f), color)
            if (selected == "p:${p.id}") ring(c, x, y, dp(14f), palette.navy, 1f, dp(2.5f))
            label(c, if (p.isHome) (p.ownerName ?: p.name) else p.name, x, y + r.coerceAtMost(dp(60f)) + dp(14f), 11f)
            hits += Hit("p:${p.id}", x, y, dp(20f))
        }

        if (showAlerts) pulses.forEach { u ->
            proj.toPixels(GeoPoint(u.lat, u.lng), pt)
            val x = pt.x.toFloat(); val y = pt.y.toFloat()
            val color = when (u.kind) { PulseKind.SOS -> palette.red; PulseKind.DONE -> palette.emerald; PulseKind.LATE -> palette.amber; PulseKind.HERE -> palette.emerald }
            ripple(c, x, y, dp(14f), color, phase, u.kind == PulseKind.SOS)
            animate = true
            disc(c, x, y, dp(12f), color)
            ring(c, x, y, dp(12f), palette.card, 1f, dp(2f))
            text.textSize = dp(11f); text.style = Paint.Style.FILL; text.color = Color.WHITE
            c.drawText(if (u.kind == PulseKind.SOS) "!" else u.count.toString(), x, y + dp(4f), text)
            hits += Hit("s:${u.key}", x, y, dp(22f))
        }

        if (showPeople) people.forEach { p ->
            proj.toPixels(GeoPoint(p.lat, p.lng), pt)
            val x = pt.x.toFloat(); val y = pt.y.toFloat()
            val recent = now - p.at < 20 * 60_000L
            val edge = if (p.inside) palette.emerald else palette.amber
            if (recent) { ripple(c, x, y, dp(16f), palette.emerald, phase, false); animate = true }
            disc(c, x, y, dp(16f), palette.navy)
            ring(c, x, y, dp(16f), edge, 1f, dp(2.5f))
            text.textSize = dp(13f); text.style = Paint.Style.FILL; text.color = Color.WHITE
            c.drawText(p.name.trim().firstOrNull()?.toString() ?: "؟", x, y + dp(4.5f), text)
            if (selected == "u:${p.userId}") ring(c, x, y, dp(21f), palette.navy, 1f, dp(2.5f))
            hits += Hit("u:${p.userId}", x, y, dp(20f))
        }

        me?.let { (la, ln) ->
            proj.toPixels(GeoPoint(la, ln), pt)
            val x = pt.x.toFloat(); val y = pt.y.toFloat()
            disc(c, x, y, dp(14f), 0xFF2F7DF6.toInt(), 0.18f)
            disc(c, x, y, dp(7f), Color.WHITE)
            disc(c, x, y, dp(5f), 0xFF2F7DF6.toInt())
        }
        if (animate && !reduceMotion) map.postInvalidateOnAnimation()
    }

    override fun onSingleTapConfirmed(e: MotionEvent, map: MapView): Boolean {
        val best = hits.filter { hypot((it.x - e.x).toDouble(), (it.y - e.y).toDouble()) <= it.r + dp(10f) }
            .minByOrNull { hypot((it.x - e.x).toDouble(), (it.y - e.y).toDouble()) }
        onTap(best?.key)
        return best != null
    }
}
