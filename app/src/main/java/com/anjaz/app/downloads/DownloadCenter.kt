package com.anjaz.app.downloads

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.anjaz.app.MainActivity
import com.anjaz.app.R
import com.anjaz.app.pack.NativePacks
import com.anjaz.app.pack.PackState
import com.anjaz.app.push.Notifications
import com.anjaz.app.stt.ModelState
import com.anjaz.app.stt.Transcriber
import com.anjaz.app.stt.WhisperModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

enum class DlStatus { MISSING, DOWNLOADING, READY, FAILED }

data class DlItem(val id: String, val title: String, val desc: String, val status: DlStatus, val percent: Int = 0, val error: String? = null)

/** مركز موحّد لما يُنزَّل بعد التثبيت: المكتبات الصوتية ونموذج الإملاء، بحالة ونسبة وإعادة محاولة. */
@Singleton
class DownloadCenter @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val packs: NativePacks,
    private val model: WhisperModel,
    private val transcriber: Transcriber,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val items: Flow<List<DlItem>> = combine(packs.state, model.state) { p, m ->
        listOf(
            DlItem(
                PACK, "المكتبات الصوتية", "مكوّن الإملاء الصوتي والمكالمات (حوالي 14 ميغابايت)",
                when (p) { is PackState.Ready -> DlStatus.READY; is PackState.Downloading -> DlStatus.DOWNLOADING; is PackState.Failed -> DlStatus.FAILED; else -> if (packs.present()) DlStatus.READY else DlStatus.MISSING },
                (p as? PackState.Downloading)?.percent ?: 0, (p as? PackState.Failed)?.reason,
            ),
            DlItem(
                MODEL, "نموذج الإملاء: ${model.info.label}", "يعمل بلا إنترنت بعد التنزيل (${model.sizeLabel})",
                when (m) { ModelState.Ready -> DlStatus.READY; is ModelState.Downloading -> DlStatus.DOWNLOADING; is ModelState.Failed -> DlStatus.FAILED; else -> DlStatus.MISSING },
                (m as? ModelState.Downloading)?.percent ?: 0, (m as? ModelState.Failed)?.reason,
            ),
        )
    }

    val variants get() = WhisperModel.VARIANTS
    val variant: String get() = model.variant

    fun start(id: String) {
        scope.launch { if (id == PACK) packs.ensure() else model.download() }
    }

    fun remove(id: String) {
        scope.launch {
            if (id == PACK) packs.remove() else { transcriber.release(); model.delete() }
        }
    }

    fun selectVariant(key: String) {
        scope.launch { transcriber.release(); model.select(key) }
    }

    /** يتابع الحالات ويعرض إشعار تقدّم ثم يخفيه عند الانتهاء، وإشعار فشل يفتح صفحة التنزيلات. */
    fun startNotifier() {
        scope.launch {
            var last = mapOf<String, DlStatus>()
            var lastPercent = -1
            items.collect { list ->
                val nm = NotificationManagerCompat.from(ctx)
                if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return@collect
                val active = list.firstOrNull { it.status == DlStatus.DOWNLOADING }
                if (active != null) {
                    if (active.percent != lastPercent || last[active.id] != DlStatus.DOWNLOADING) {
                        lastPercent = active.percent
                        nm.notify(NOTE_ID, base(if (active.id == PACK) "جارٍ تحميل المكتبات الصوتية" else "جارٍ تحميل نموذج الإملاء", "${active.percent}٪")
                            .setOngoing(true).setOnlyAlertOnce(true).setProgress(100, active.percent, false).build())
                    }
                } else {
                    nm.cancel(NOTE_ID); lastPercent = -1
                }
                list.forEach { it ->
                    val was = last[it.id]
                    if (was == DlStatus.DOWNLOADING && it.status == DlStatus.READY)
                        nm.notify(NOTE_ID + 1, base("اكتمل التحميل", it.title).setTimeoutAfter(4_000).setOnlyAlertOnce(true).build())
                    if (was == DlStatus.DOWNLOADING && it.status == DlStatus.FAILED)
                        nm.notify(NOTE_ID + 2, base("تعذّر التحميل: ${it.title}", "اضغط لإعادة المحاولة من الإعدادات").build())
                    if (it.status == DlStatus.DOWNLOADING) nm.cancel(NOTE_ID + 2)
                }
                last = list.associate { it.id to it.status }
            }
        }
    }

    private fun base(title: String, text: String): NotificationCompat.Builder {
        val open = PendingIntent.getActivity(
            ctx, NOTE_ID, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("route", "downloads"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(ctx, Notifications.DOWNLOADS)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle(title).setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW).setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setAutoCancel(true).setContentIntent(open)
    }

    companion object {
        const val PACK = "pack"
        const val MODEL = "model"
        private const val NOTE_ID = 7100
    }
}
