package com.anjaz.app.update

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anjaz.app.BuildConfig
import com.anjaz.app.ui.components.AnjazIcon
import com.anjaz.app.ui.components.PrimaryButton
import com.anjaz.app.ui.components.QuietButton
import com.anjaz.app.ui.components.rememberReduceMotion
import com.anjaz.app.ui.icons.AnjazIcons
import com.anjaz.app.ui.theme.Anjaz
import com.anjaz.app.ui.theme.Display
import com.anjaz.app.ui.theme.ltr
import com.anjaz.app.update.UpdateManager.State

private fun State.release(): AppRelease? = when (this) {
    is State.Available -> release
    is State.Downloading -> release
    is State.Ready -> release
    is State.Installing -> release
    is State.Failed -> release
    State.Idle -> null
}

/** البانر العلوي والورقة السفلية للتحديث، فوق كل الشاشات. */
@Composable
fun UpdateLayer(vm: UpdateViewModel = hiltViewModel()) {
    val manager = vm.manager
    val state by manager.state.collectAsStateWithLifecycle()
    val sheet by manager.sheet.collectAsStateWithLifecycle()
    val hidden by manager.bannerHidden.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val reduce = rememberReduceMotion()
    val visible = state !is State.Idle && !sheet && !(hidden && state is State.Available)
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = if (reduce) fadeIn(tween(0)) else slideInVertically(tween(420, easing = FastOutSlowInEasing)) { -it } + fadeIn(),
            exit = if (reduce) fadeOut(tween(0)) else slideOutVertically(tween(260)) { -it } + fadeOut(),
        ) {
            UpdateBanner(state, onOpen = { manager.showSheet(true) }, onAction = {
                when (state) {
                    is State.Ready -> manager.install(context)
                    is State.Available, is State.Failed -> manager.start()
                    else -> manager.showSheet(true)
                }
            })
        }
    }
    if (sheet && state !is State.Idle) UpdateSheet(state, manager)
}

@Composable
private fun UpdateBanner(state: State, onOpen: () -> Unit, onAction: () -> Unit) {
    val c = Anjaz.colors
    val release = state.release() ?: return
    val v = ltr(release.version)
    val (title, sub, action) = when (state) {
        is State.Available -> Triple("تحديث جديد $v", "مزايا وإصلاحات جاهزة للتحميل", "تحميل")
        is State.Downloading -> Triple("جارٍ التنزيل", "يستمر في الخلفية حتى لو أغلقت التطبيق", null)
        is State.Ready -> Triple("تم التحميل", "النسخة $v محفوظة وجاهزة للتثبيت", "تثبيت")
        is State.Installing -> Triple("جارٍ التثبيت", "أكّد من نافذة النظام إن ظهرت", null)
        is State.Failed -> Triple("تعذّر التنزيل", "تحقق من الاتصال ثم أعد المحاولة", "إعادة")
        State.Idle -> return
    }
    val pct = (state as? State.Downloading)?.percent ?: if (state is State.Ready) 100 else 0
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier.statusBarsPadding().padding(horizontal = 14.dp, vertical = 8.dp).fillMaxWidth()
            .shadow(14.dp, shape, ambientColor = c.navy, spotColor = c.navy)
            .clip(shape).background(Brush.linearGradient(listOf(c.navy, c.navyHigh)))
            .border(1.dp, c.emerald.copy(alpha = .35f), shape)
            .clickable(onClick = onOpen)
            .semantics { role = Role.Button; contentDescription = "$title. $sub" },
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp).heightIn(min = 52.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(c.emerald.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
                when (state) {
                    is State.Downloading -> Ring(pct, 44, c.emerald, c.onNavy.copy(alpha = .18f))
                    is State.Installing -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp, color = c.emerald)
                    is State.Ready -> AnjazIcon(AnjazIcons.Check, c.emerald, 26.dp)
                    else -> AnjazIcon(AnjazIcons.Cloud, c.emerald, 26.dp)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(title, color = c.onNavy, style = MaterialTheme.typography.titleMedium.copy(fontFamily = Display, fontWeight = FontWeight.SemiBold))
                Text(
                    if (state is State.Downloading) "$sub — ${ltr("$pct%")}" else sub,
                    color = c.onNavy.copy(alpha = .82f), style = MaterialTheme.typography.bodySmall,
                )
            }
            if (action != null) {
                Box(
                    Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(16.dp)).background(c.emerald)
                        .clickable(onClick = onAction).padding(horizontal = 18.dp)
                        .semantics { role = Role.Button },
                    contentAlignment = Alignment.Center,
                ) { Text(action, color = c.onEmerald, style = MaterialTheme.typography.labelLarge) }
            }
        }
        if (state is State.Downloading) LineProgress(pct)
    }
}

@Composable
private fun LineProgress(percent: Int) {
    val c = Anjaz.colors
    val reduce = rememberReduceMotion()
    val p by animateFloatAsState(percent / 100f, tween(if (reduce) 0 else 300), label = "line")
    Box(Modifier.fillMaxWidth().size(width = 0.dp, height = 4.dp).background(c.onNavy.copy(alpha = .14f))) {
        Box(Modifier.fillMaxWidth(p).size(width = 0.dp, height = 4.dp).background(c.emerald))
    }
}

@Composable
private fun Ring(percent: Int, size: Int, arc: Color, track: Color) {
    val reduce = rememberReduceMotion()
    val p by animateFloatAsState(percent / 100f, tween(if (reduce) 0 else 300), label = "ring")
    Canvas(Modifier.size(size.dp)) {
        val w = 3.5.dp.toPx()
        val box = Size(this.size.width - w, this.size.height - w)
        drawArc(track, 0f, 360f, false, Offset(w / 2, w / 2), box, style = Stroke(w))
        drawArc(arc, -90f, 360f * p, false, Offset(w / 2, w / 2), box, style = Stroke(w, cap = StrokeCap.Round))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UpdateSheet(state: State, manager: UpdateManager) {
    val c = Anjaz.colors
    val context = LocalContext.current
    val release = state.release() ?: return
    val close = { manager.showSheet(false); if (state is State.Available) manager.hideBanner() }
    ModalBottomSheet(
        onDismissRequest = { close() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.card,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("تحديث جديد لإنجاز", color = c.ink, style = MaterialTheme.typography.headlineMedium)
            Text(
                "من ${ltr(BuildConfig.VERSION_NAME)} إلى ${ltr(release.version)}",
                color = c.mute, style = MaterialTheme.typography.bodyMedium,
            )
            if (release.notes.isNotBlank()) {
                Text("ما الجديد", color = c.ink, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                Column(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
                    Text(release.notes, color = c.ink, style = MaterialTheme.typography.bodyMedium)
                }
            }
            when (state) {
                is State.Downloading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Ring(state.percent, 44, c.emeraldDeep, c.line)
                    Column {
                        Text(ltr("${state.percent}%"), color = c.ink, style = MaterialTheme.typography.titleMedium)
                        Text("يستمر في الخلفية ويُحفظ داخل مجلد التطبيق", color = c.mute, style = MaterialTheme.typography.bodySmall)
                    }
                }
                is State.Failed -> Text("تعذّر التنزيل: ${ltr(state.reason)}", color = c.redDeep, style = MaterialTheme.typography.bodyMedium)
                is State.Ready -> Text("تم حفظ النسخة داخل التطبيق، وتُحذف تلقائيًا بعد التثبيت.", color = c.mute, style = MaterialTheme.typography.bodySmall)
                is State.Installing -> Text("جارٍ التثبيت…", color = c.mute, style = MaterialTheme.typography.bodyMedium)
                else -> Text("لن يتوقف التنزيل عند إغلاق التطبيق.", color = c.mute, style = MaterialTheme.typography.bodySmall)
            }
            when (state) {
                is State.Available -> PrimaryButton("تحميل التحديث", { manager.start() }, Modifier.padding(top = 6.dp))
                is State.Failed -> PrimaryButton("إعادة المحاولة", { manager.start() }, Modifier.padding(top = 6.dp))
                is State.Ready -> PrimaryButton("تثبيت الآن", { manager.install(context) }, Modifier.padding(top = 6.dp))
                else -> Unit
            }
            QuietButton(if (state is State.Downloading) "إخفاء" else "لاحقًا", Modifier.fillMaxWidth()) { close() }
        }
    }
}
