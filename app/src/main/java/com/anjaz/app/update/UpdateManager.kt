package com.anjaz.app.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.content.pm.PackageInfoCompat
import com.anjaz.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/** حالة التحديث كاملة: الاكتشاف، التنزيل في الخلفية داخل مجلد التطبيق، التثبيت، ثم التنظيف. */
@Singleton
class UpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val checker: UpdateChecker,
) {
    sealed interface State {
        data object Idle : State
        data class Available(val release: AppRelease) : State
        data class Downloading(val release: AppRelease, val percent: Int) : State
        data class Ready(val release: AppRelease) : State
        data class Installing(val release: AppRelease) : State
        data class Failed(val release: AppRelease, val reason: String) : State
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _sheet = MutableStateFlow(false)
    val sheet: StateFlow<Boolean> = _sheet.asStateFlow()

    private val _bannerHidden = MutableStateFlow(false)
    val bannerHidden: StateFlow<Boolean> = _bannerHidden.asStateFlow()

    private var lastCheck = 0L

    fun showSheet(show: Boolean) {
        _sheet.value = show && _state.value !is State.Idle
        if (show) _bannerHidden.value = false
    }

    fun hideBanner() {
        _bannerHidden.value = true
    }

    /** فحص تلقائي مخنوق زمنيًا؛ الفحص اليدوي يتجاوز الخنق. */
    suspend fun check(force: Boolean = false): UpdateChecker.Result? {
        val now = System.currentTimeMillis()
        if (!force && now - lastCheck < MIN_GAP_MS) return null
        lastCheck = now
        val result = checker.check()
        if (result is UpdateChecker.Result.Available) {
            val busy = _state.value.let { it is State.Downloading || it is State.Installing }
            if (!busy) {
                val r = result.release
                _state.value = if (valid(apkFile(r))) State.Ready(r) else State.Available(r)
                _bannerHidden.value = false
            }
        }
        return result
    }

    private fun release(): AppRelease? = when (val s = _state.value) {
        is State.Available -> s.release
        is State.Failed -> s.release
        else -> null
    }

    /** يبدأ التنزيل في خدمة أمامية فيستمر بعد إغلاق التطبيق. */
    fun start() {
        val r = release() ?: return
        if (valid(apkFile(r))) {
            _state.value = State.Ready(r)
            return
        }
        _state.value = State.Downloading(r, 0)
        runCatching { ContextCompat.startForegroundService(context, Intent(context, UpdateDownloadService::class.java)) }
            .onFailure { _state.value = State.Failed(r, it.javaClass.simpleName) }
    }

    /** يعمل داخل الخدمة: استئناف عبر Range وإعادة محاولة بتأخير متدرج كي لا يتوقف التنزيل. */
    suspend fun download() = withContext(Dispatchers.IO) {
        val r = (_state.value as? State.Downloading)?.release ?: return@withContext
        val part = File(AppFolder.updates(context), apkFile(r).name + ".part")
        try {
            var attempt = 0
            while (true) {
                try {
                    fetch(r, part)
                    break
                } catch (e: IOException) {
                    if (++attempt >= MAX_ATTEMPTS) throw e
                    delay(1_500L * attempt)
                }
            }
            if (!valid(part)) {
                part.delete()
                throw IOException("invalid apk")
            }
            val file = apkFile(r)
            file.delete()
            if (!part.renameTo(file)) throw IOException("rename")
            _state.value = State.Ready(r)
        } catch (e: CancellationException) {
            _state.value = State.Available(r)
            throw e
        } catch (e: Exception) {
            _state.value = State.Failed(r, e.message ?: e.javaClass.simpleName)
        }
    }

    private suspend fun fetch(r: AppRelease, part: File) {
        val have = if (part.isFile) part.length() else 0L
        val conn = (URL(r.apkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", "Anjaz-App")
            if (have > 0) setRequestProperty("Range", "bytes=$have-")
        }
        try {
            val code = conn.responseCode
            if (code == 416 && have > 0) return
            if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_PARTIAL) throw IOException("HTTP $code")
            val resume = code == HttpURLConnection.HTTP_PARTIAL
            val start = if (resume) have else 0L
            val length = conn.contentLengthLong
            val total = if (length > 0) start + length else r.size
            RandomAccessFile(part, "rw").use { out ->
                if (!resume) out.setLength(0)
                out.seek(start)
                var done = start
                var last = -1
                val buf = ByteArray(64 * 1024)
                conn.inputStream.use { input ->
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        val pct = if (total > 0) (done * 100 / total).toInt().coerceIn(0, 99) else 0
                        if (pct != last) {
                            last = pct
                            _state.value = State.Downloading(r, pct)
                        }
                    }
                }
                if (total > 0 && done != total) throw IOException("incomplete")
            }
        } finally {
            conn.disconnect()
        }
    }

    /** بدون صلاحية «تثبيت التطبيقات المجهولة» تُفتح صفحتها أولًا ثم يضغط المستخدم تثبيت ثانية. */
    fun install(from: Context = context) {
        val r = (_state.value as? State.Ready)?.release ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            runCatching {
                from.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            return
        }
        val file = apkFile(r)
        if (!valid(file)) {
            _state.value = State.Failed(r, "missing file")
            return
        }
        _state.value = State.Installing(r)
        scope.launch {
            runCatching { commit(file) }.onFailure { _state.value = State.Failed(r, it.message ?: it.javaClass.simpleName) }
        }
    }

    private fun commit(file: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            if (Build.VERSION.SDK_INT >= 33) setPackageSource(PackageInstaller.PACKAGE_SOURCE_STORE)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            file.inputStream().use { input ->
                session.openWrite("anjaz.apk", 0, file.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            val result = PendingIntent.getBroadcast(
                context, id, Intent(context, UpdateInstallReceiver::class.java).setPackage(context.packageName), flags,
            )
            session.commit(result.intentSender)
        }
    }

    fun installEnded(status: Int, message: String?) {
        val r = (_state.value as? State.Installing)?.release ?: return
        _state.value = if (status == PackageInstaller.STATUS_FAILURE_ABORTED) State.Ready(r)
        else State.Failed(r, "$status${message?.let { ": $it" }.orEmpty()}")
    }

    /**
     * عند كل تشغيل: ما ثُبّت يُحذف فورًا لاسترجاع المساحة، وما حُمّل ولم يُثبّت يظهر جاهزًا للتثبيت.
     * ملفات .part القديمة (> 24 ساعة) تُحذف أما الحديثة فتُترك للاستئناف.
     */
    fun restore() {
        val installed = BuildConfig.VERSION_CODE.toLong()
        val now = System.currentTimeMillis()
        var ready: AppRelease? = null
        AppFolder.updates(context).listFiles().orEmpty().forEach { f ->
            if (f.name.endsWith(".part")) {
                if (now - f.lastModified() > PART_TTL_MS && _state.value !is State.Downloading) f.delete()
                return@forEach
            }
            val info = archive(f)
            val code = info?.let { PackageInfoCompat.getLongVersionCode(it) }
            if (info == null || code == null || code <= installed) {
                f.delete()
            } else if (_state.value is State.Idle) {
                ready = AppRelease(info.versionName.orEmpty(), (code % 10000).toInt(), code.toInt(), "", "")
            }
        }
        ready?.let { _state.value = State.Ready(it) }
        if (_state.value is State.Idle) UpdateNotifications.cancelAll(context)
    }

    private fun apkFile(r: AppRelease) = File(AppFolder.updates(context), "Anjaz-${r.version}-b${r.build}.apk")

    @Suppress("DEPRECATION")
    private fun archive(file: File) = runCatching {
        if (Build.VERSION.SDK_INT >= 33) context.packageManager.getPackageArchiveInfo(file.path, PackageManager.PackageInfoFlags.of(0))
        else context.packageManager.getPackageArchiveInfo(file.path, 0)
    }.getOrNull()

    /** حزمة إنجاز حقيقية وأحدث من المثبّتة، وغير ذلك لا يُعرض للتثبيت أبدًا. */
    private fun valid(file: File): Boolean {
        if (!file.isFile) return false
        val info = archive(file) ?: return false
        return info.packageName == context.packageName && PackageInfoCompat.getLongVersionCode(info) > BuildConfig.VERSION_CODE
    }

    private companion object {
        const val PART_TTL_MS = 24 * 60 * 60 * 1000L
        const val MIN_GAP_MS = 3 * 60 * 60 * 1000L
        const val MAX_ATTEMPTS = 5
    }
}
