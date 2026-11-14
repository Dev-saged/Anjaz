package com.anjaz.app.ui.map

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anjaz.app.domain.Role
import com.anjaz.app.geo.LocationProvider
import com.anjaz.app.geo.MapFilter
import com.anjaz.app.geo.PulseKind
import com.anjaz.app.ui.components.AnjazIcon
import com.anjaz.app.ui.components.Notice
import com.anjaz.app.ui.components.PrimaryButton
import com.anjaz.app.ui.components.QuietButton
import com.anjaz.app.ui.components.SchemaForm
import com.anjaz.app.ui.components.ScreenTitle
import com.anjaz.app.ui.components.rememberReduceMotion
import com.anjaz.app.ui.icons.AnjazIcons
import com.anjaz.app.ui.theme.Anjaz
import java.io.File
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.TilesOverlay

private fun newMap(ctx: Context): MapView {
    Configuration.getInstance().apply {
        load(ctx, ctx.getSharedPreferences("osm", Context.MODE_PRIVATE))
        userAgentValue = ctx.packageName
        osmdroidBasePath = File(ctx.cacheDir, "osm")
        osmdroidTileCache = File(ctx.cacheDir, "osm/tiles")
        tileFileSystemCacheMaxBytes = 100L * 1024 * 1024
        tileFileSystemCacheTrimBytes = 80L * 1024 * 1024
    }
    return MapView(ctx).apply {
        setTileSource(TileSourceFactory.MAPNIK)
        setMultiTouchControls(true)
        zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        minZoomLevel = 3.0
        controller.setZoom(13.0)
        controller.setCenter(GeoPoint(31.5, 34.45))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveMapScreen(onBack: () -> Unit, vm: MapViewModel = hiltViewModel()) {
    val c = Anjaz.colors
    val ctx = LocalContext.current
    val ui by vm.ui.collectAsStateWithLifecycle()
    val reduce = rememberReduceMotion()
    var pending by remember { mutableStateOf<String?>(null) }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.any { it }) when (pending) { "in" -> vm.checkIn(); "me" -> vm.locateMe(); "add" -> vm.submitAdd() }
        pending = null
    }
    fun need(action: String, run: () -> Unit) {
        if (LocationProvider.granted(ctx)) run() else { pending = action; perm.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }
    }

    val map = remember { newMap(ctx) }
    val overlay = remember {
        LiveOverlay(ctx.resources.displayMetrics.density, MapPalette(c.navy.toArgb(), c.emerald.toArgb(), c.amber.toArgb(), c.red.toArgb(), c.ink.toArgb(), c.card.toArgb()))
            .also { map.overlays.add(it) }
    }
    overlay.onTap = vm::select
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) map.onResume() else if (e == Lifecycle.Event.ON_PAUSE) map.onPause() }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs); map.onDetach() }
    }
    LaunchedEffect(c.dark) { map.overlayManager.tilesOverlay.setColorFilter(if (c.dark) TilesOverlay.INVERT_COLORS else null) }

    var fitted by remember { mutableStateOf(false) }
    LaunchedEffect(ui.places.size, ui.people.size, ui.pulses.size) {
        if (fitted) return@LaunchedEffect
        val pts = ui.places.map { GeoPoint(it.lat, it.lng) } + ui.people.map { GeoPoint(it.lat, it.lng) } + ui.pulses.map { GeoPoint(it.lat, it.lng) }
        if (pts.isEmpty()) return@LaunchedEffect
        fitted = true
        map.post {
            val box = BoundingBox.fromGeoPoints(pts)
            if (pts.size > 1 && (box.latitudeSpan > 0.0004 || box.longitudeSpan > 0.0004)) map.zoomToBoundingBox(box, true, 140)
            else { map.controller.setZoom(16.0); map.controller.animateTo(pts.first()) }
        }
    }
    LaunchedEffect(ui.me) { ui.me?.let { map.controller.animateTo(GeoPoint(it.first, it.second)) } }

    overlay.places = ui.places; overlay.people = ui.people; overlay.pulses = ui.pulses; overlay.filter = ui.filter
    overlay.selected = ui.selected; overlay.now = ui.now; overlay.me = ui.me; overlay.reduceMotion = reduce

    Box(Modifier.fillMaxSize().background(c.surface)) {
        AndroidView(factory = { map }, modifier = Modifier.fillMaxSize(), update = { it.invalidate() })

        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(c.card).border(1.dp, c.line, CircleShape).clickable(onClick = onBack).semantics { contentDescription = "رجوع" }, contentAlignment = Alignment.Center) {
                    AnjazIcon(AnjazIcons.Back, c.ink)
                }
                Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(c.card.copy(alpha = .94f)).padding(horizontal = 14.dp, vertical = 8.dp)) {
                    ScreenTitle("الخريطة الحية")
                    Text("${ui.people.size} حاضر · ${ui.places.count { !it.isHome }} نقطة · ${ui.pulses.size} تنبيه", color = c.mute, style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MapFilter.entries.forEach { f ->
                    val on = ui.filter == f
                    Text(
                        f.label, color = if (on) c.onNavy else c.ink, style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp)).background(if (on) c.navy else c.card.copy(alpha = .94f))
                            .border(1.dp, c.line, RoundedCornerShape(22.dp)).clickable { vm.setFilter(f) }.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }
            Notice(ui.message, ui.isError, vm::dismissMessage)
        }

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp).navigationBarsPadding()) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.card.copy(alpha = .96f)).border(1.dp, c.line, RoundedCornerShape(24.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SelectionCard(ui, onDelete = vm::deleteSelected, onClose = { vm.select(null) })
                PrimaryButton(if (ui.busy) "جارٍ التحديد…" else "سجّل وصولي", onClick = { need("in", vm::checkIn) }, enabled = !ui.busy)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuietButton("موقعي", Modifier.weight(1f)) { need("me", vm::locateMe) }
                    if (ui.canHome) QuietButton("تحديد مسكني", Modifier.weight(1f)) { vm.openAdd("HOME") }
                    if (ui.canPoint) QuietButton("نقطة جديدة", Modifier.weight(1f)) { vm.openAdd("POINT") }
                }
            }
        }
    }

    val kind = ui.addKind
    if (kind != null) ModalBottomSheet(onDismissRequest = vm::closeAdd, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.surface) {
        val schema = vm.addSchema(kind, ui.squares)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ScreenTitle(schema.title)
            Text("سيُحفظ الموقع عند مكانك الحالي. قف في المكان المطلوب قبل الحفظ.", color = c.mute, style = MaterialTheme.typography.bodySmall)
            SchemaForm(schema, ui.addValues, ui.addErrors, vm::changeAdd)
            PrimaryButton(if (ui.busy) "جارٍ الحفظ…" else "حفظ عند موقعي الحالي", onClick = { need("add", vm::submitAdd) }, enabled = !ui.busy)
        }
    }
}

@Composable
private fun SelectionCard(ui: MapUi, onDelete: () -> Unit, onClose: () -> Unit) {
    val c = Anjaz.colors
    val key = ui.selected ?: return
    val title: String
    val sub: String
    var canDelete = false
    when {
        key.startsWith("p:") -> {
            val p = ui.places.firstOrNull { "p:${it.id}" == key } ?: return
            title = if (p.isHome) "مسكن ${p.ownerName ?: p.name}" else p.name
            sub = (if (p.isHome) "مسكن" else "نقطة") + " · نطاق ${p.radius.toInt()} م"
            canDelete = ui.profile?.let { it.uid == p.ownerId || it.roleEnum == Role.CENTRAL || it.roleEnum == Role.REGION_MANAGER || it.roleEnum == Role.REGION_ADMIN } == true
        }
        key.startsWith("u:") -> {
            val u = ui.people.firstOrNull { "u:${it.userId}" == key } ?: return
            val mins = ((ui.now - u.at) / 60_000L).coerceAtLeast(0)
            title = u.name
            sub = (Role.entries.firstOrNull { it.name == u.role }?.label.orEmpty()) + " · قبل " + (if (mins < 60) "$mins دقيقة" else "${mins / 60} ساعة") +
                (if (u.placeName != null) (if (u.inside) " · داخل ${u.placeName}" else " · خارج نطاق ${u.placeName}") else "")
        }
        else -> {
            val s = ui.pulses.firstOrNull { "s:${it.key}" == key } ?: return
            title = s.label
            sub = when (s.kind) { PulseKind.SOS -> "استغاثة مفتوحة تحتاج استجابة"; PulseKind.DONE -> "إنجاز اليوم"; PulseKind.LATE -> "تحتاج متابعة" }
        }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, color = c.ink, style = MaterialTheme.typography.titleMedium)
            Text(sub, color = c.mute, style = MaterialTheme.typography.bodySmall)
        }
        if (canDelete) Text("حذف", color = c.redDeep, style = MaterialTheme.typography.labelLarge, modifier = Modifier.heightIn(min = 44.dp).clickable(onClick = onDelete).padding(10.dp))
        Text("إغلاق", color = c.mute, style = MaterialTheme.typography.labelLarge, modifier = Modifier.heightIn(min = 44.dp).clickable(onClick = onClose).padding(10.dp))
    }
}
