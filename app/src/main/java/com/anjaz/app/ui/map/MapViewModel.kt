package com.anjaz.app.ui.map

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anjaz.app.cloud.AuthRepository
import com.anjaz.app.data.db.SosEntity
import com.anjaz.app.data.db.SquareCount
import com.anjaz.app.domain.Profile
import com.anjaz.app.domain.Role
import com.anjaz.app.geo.GeoMath
import com.anjaz.app.geo.GeoRepository
import com.anjaz.app.geo.LocationProvider
import com.anjaz.app.geo.MapFilter
import com.anjaz.app.geo.Place
import com.anjaz.app.geo.Presence
import com.anjaz.app.geo.Pulse
import com.anjaz.app.geo.PulseKind
import com.anjaz.app.schema.Choice
import com.anjaz.app.schema.FieldSchema
import com.anjaz.app.schema.FieldType
import com.anjaz.app.schema.ModuleSchema
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MapUi(
    val profile: Profile? = null,
    val places: List<Place> = emptyList(),
    val people: List<Presence> = emptyList(),
    val pulses: List<Pulse> = emptyList(),
    val filter: MapFilter = MapFilter.ALL,
    val selected: String? = null,
    val me: Pair<Double, Double>? = null,
    val now: Long = 0L,
    val busy: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
    val addKind: String? = null,
    val addValues: Map<String, String> = emptyMap(),
    val addErrors: Map<String, String> = emptyMap(),
    val squares: List<Choice> = emptyList(),
) {
    val canPoint get() = profile?.roleEnum.let { it == Role.CENTRAL || it == Role.REGION_MANAGER || it == Role.REGION_ADMIN }
    val canHome get() = profile?.canAssign == true
}

private data class Snap(val places: List<Place>, val people: List<Presence>, val done: List<SquareCount>, val late: List<SquareCount>, val sos: List<SosEntity>)

@HiltViewModel
class MapViewModel @Inject constructor(
    private val repo: GeoRepository,
    auth: AuthRepository,
    @ApplicationContext private val ctx: Context,
) : ViewModel() {
    private val profile: StateFlow<Profile?> = auth.session.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val ui = MutableStateFlow(MapUi())

    init {
        val ticker = flow { while (true) { emit(System.currentTimeMillis()); delay(30_000) } }
        viewModelScope.launch {
            profile.filterNotNull().flatMapLatest { p ->
                val now = System.currentTimeMillis()
                val data = combine(
                    repo.places(p), repo.presence(p, GeoMath.day(now)), repo.doneBySquare(now - 86_400_000L), repo.overdueBySquare(now), repo.sos(),
                ) { a, b, c, d, e -> Snap(a, b, c, d, e) }
                combine(data, ticker, repo.squares()) { s, t, sq -> Triple(p, s to t, sq) }
            }.collect { (p, st, squares) ->
                val (s, t) = st
                ui.update {
                    it.copy(
                        profile = p, places = s.places, people = s.people, now = t, pulses = pulses(s, t),
                        squares = squares.filter { sq -> p.roleEnum == Role.CENTRAL || sq.regionId == p.regionId }.map { sq -> Choice(sq.id, sq.name) },
                    )
                }
            }
        }
    }

    private fun pulses(s: Snap, now: Long): List<Pulse> = buildList {
        s.sos.filter { it.status == "OPEN" && it.lat != null && it.lng != null && now - it.createdAt < 6 * 3_600_000L }
            .forEach { add(Pulse("sos:${it.id}", PulseKind.SOS, it.lat!!, it.lng!!, 1, "نداء استغاثة من ${it.senderName}")) }
        s.done.forEach { c ->
            s.places.firstOrNull { !it.isHome && it.squareId == c.squareId }?.let { add(Pulse("done:${c.squareId}", PulseKind.DONE, it.lat, it.lng, c.n, "${c.n} مهمة معتمدة اليوم في ${it.name}")) }
        }
        s.late.forEach { c ->
            s.places.firstOrNull { !it.isHome && it.squareId == c.squareId }?.let { add(Pulse("late:${c.squareId}", PulseKind.LATE, it.lat, it.lng, c.n, "${c.n} مهمة متأخرة في ${it.name}")) }
        }
    }

    fun setFilter(f: MapFilter) = ui.update { it.copy(filter = f, selected = null) }
    fun select(key: String?) = ui.update { it.copy(selected = key) }
    fun dismissMessage() = ui.update { it.copy(message = null) }

    fun locateMe() = viewModelScope.launch {
        val loc = LocationProvider.current(ctx)
        ui.update { if (loc == null) it.copy(message = "تعذّر تحديد موقعك. فعّل GPS وامنح الإذن", isError = true) else it.copy(me = loc) }
    }

    fun checkIn() {
        val p = profile.value ?: return
        viewModelScope.launch {
            ui.update { it.copy(busy = true) }
            val loc = LocationProvider.current(ctx)
            if (loc == null) {
                ui.update { it.copy(busy = false, message = "تعذّر تحديد موقعك. فعّل GPS وامنح الإذن", isError = true) }
                return@launch
            }
            val (place, inside) = repo.checkIn(p, loc.first, loc.second, ui.value.places, System.currentTimeMillis())
            val text = when {
                place == null -> "سُجّل حضورك"
                inside -> "سُجّل وصولك إلى ${place.name}"
                else -> "سُجّل حضورك، وأنت خارج نطاق ${place.name} بمسافة ${GeoMath.distanceLabel(GeoMath.meters(loc.first, loc.second, place.lat, place.lng))}"
            }
            ui.update { it.copy(busy = false, me = loc, message = text, isError = false) }
        }
    }

    fun openAdd(kind: String) {
        val p = profile.value ?: return
        ui.update {
            it.copy(
                addKind = kind, addErrors = emptyMap(),
                addValues = mapOf("name" to if (kind == "HOME") "مسكن ${p.displayName}" else "", "radius" to if (kind == "HOME") "150" else "200", "squareId" to p.squareId.orEmpty()),
            )
        }
    }

    fun closeAdd() = ui.update { it.copy(addKind = null, addErrors = emptyMap(), busy = false) }
    fun changeAdd(k: String, v: String) = ui.update { it.copy(addValues = it.addValues + (k to v), addErrors = it.addErrors - k) }

    fun addSchema(kind: String, squares: List<Choice>) = ModuleSchema(
        id = "place", title = if (kind == "HOME") "تحديد المسكن" else "نقطة جديدة",
        fields = buildList {
            add(FieldSchema("name", "الاسم", FieldType.TEXT, required = true, maxLength = 80))
            add(FieldSchema("radius", "نصف القطر بالأمتار (20 إلى 5000)", FieldType.NUMBER, required = true, maxLength = 4))
            if (kind == "POINT" && squares.isNotEmpty()) add(FieldSchema("squareId", "المربع المرتبط (لنبض الإنجاز)", FieldType.CHOICE, choices = squares))
        },
        listColumns = listOf("name"),
    )

    fun submitAdd() {
        val p = profile.value ?: return
        val cur = ui.value
        val kind = cur.addKind ?: return
        val errors = mutableMapOf<String, String>()
        if (cur.addValues["name"].orEmpty().isBlank()) errors["name"] = "الاسم مطلوب"
        val radius = cur.addValues["radius"]?.toDoubleOrNull()
        if (radius == null || radius < 20 || radius > 5000) errors["radius"] = "بين 20 و5000 متر"
        if (errors.isNotEmpty()) { ui.update { it.copy(addErrors = errors) }; return }
        viewModelScope.launch {
            ui.update { it.copy(busy = true) }
            val loc = LocationProvider.current(ctx)
            if (loc == null) {
                ui.update { it.copy(busy = false, message = "تعذّر تحديد موقعك. فعّل GPS وامنح الإذن", isError = true) }
                return@launch
            }
            repo.addPlace(p, cur.addValues.getValue("name"), kind, loc.first, loc.second, radius!!, cur.addValues["squareId"]?.takeIf { it.isNotBlank() })
            ui.update { it.copy(busy = false, addKind = null, me = loc, message = if (kind == "HOME") "حُدّد المسكن عند موقعك الحالي" else "أُضيفت النقطة عند موقعك الحالي", isError = false) }
        }
    }

    fun deleteSelected() {
        val key = ui.value.selected ?: return
        if (!key.startsWith("p:")) return
        repo.deletePlace(key.removePrefix("p:"))
        ui.update { it.copy(selected = null, message = "حُذف الموقع", isError = false) }
    }
}
