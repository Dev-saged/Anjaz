package com.anjaz.app.pack

import android.content.Context
import android.os.Process
import com.anjaz.app.BrandConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface PackState {
    data object Idle : PackState
    data class Downloading(val percent: Int) : PackState
    data object Ready : PackState
    data class Failed(val reason: String) : PackState
}

/**
 * مكتبات أصلية ثقيلة (الإملاء الصوتي والمكالمات) خارج الـ APK لتقليل التحميل؛
 * تُنزَّل مرة واحدة من Release ثابت وتُفحص ببصمة SHA-256 ثم تُحمَّل عند الحاجة.
 */
@Singleton
class NativePacks @Inject constructor(@ApplicationContext private val ctx: Context) {
    val state = MutableStateFlow<PackState>(if (File(ctx.filesDir, "packs/$PACK_ID/${if (Process.is64Bit()) "arm64-v8a" else "armeabi-v7a"}/.ok").exists()) PackState.Ready else PackState.Idle)
    private val lock = Mutex()
    @Volatile private var installed = false

    private val abi get() = if (Process.is64Bit()) "arm64-v8a" else "armeabi-v7a"
    private val dir get() = File(ctx.filesDir, "packs/$PACK_ID/$abi")
    private val marker get() = File(dir, ".ok")

    fun present() = marker.exists()

    fun remove() {
        if (state.value is PackState.Downloading) return
        File(ctx.filesDir, "packs").deleteRecursively()
        state.value = PackState.Idle
    }

    suspend fun ensure(): Result<Unit> = lock.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                if (!present()) download()
                install()
                state.value = PackState.Ready
            }.onFailure { state.value = PackState.Failed(it.message ?: "تعذّر تنزيل المكوّن") }
        }
    }

    /** محمّل مكتبة WebRTC من الحزمة بدل مسار الـ APK. */
    val webrtcLoader = org.webrtc.NativeLibraryLoader { name ->
        runCatching { System.load(File(dir, System.mapLibraryName(name)).absolutePath) }.isSuccess
    }

    private fun download() {
        val base = "https://github.com/${BrandConfig.GITHUB_REPO}/releases/download/$PACK_TAG/Anjaz-libs-$abi.zip"
        val tmp = File(ctx.cacheDir, "pack-$abi.zip")
        val expected = String(fetch("$base.sha256").readBytes()).trim().substringBefore(' ').lowercase()
        state.value = PackState.Downloading(0)
        val conn = open(base)
        val total = conn.contentLengthLong
        val md = MessageDigest.getInstance("SHA-256")
        conn.inputStream.use { i ->
            tmp.outputStream().use { o ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                while (true) {
                    val n = i.read(buf)
                    if (n < 0) break
                    o.write(buf, 0, n); md.update(buf, 0, n); done += n
                    if (total > 0) state.value = PackState.Downloading((done * 100 / total).toInt())
                }
            }
        }
        val sum = md.digest().joinToString("") { "%02x".format(it) }
        if (sum != expected) { tmp.delete(); throw IOException("الملف المنزَّل تالف، أعد المحاولة") }
        val stage = File(dir.parentFile, "$abi.tmp").apply { deleteRecursively(); mkdirs() }
        ZipInputStream(tmp.inputStream()).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                if (e.isDirectory || e.name.contains('/') || e.name.contains("..") || !e.name.endsWith(".so")) continue
                File(stage, e.name).outputStream().use { z.copyTo(it) }
            }
        }
        tmp.delete()
        if (stage.list().isNullOrEmpty()) throw IOException("الحزمة فارغة")
        File(stage, ".ok").writeText(PACK_ID)
        dir.deleteRecursively()
        if (!stage.renameTo(dir)) throw IOException("تعذّر حفظ المكوّن")
    }

    private fun install() {
        if (installed) return
        addNativeDir(dir)
        ORDER.forEach { n -> File(dir, n).takeIf { it.exists() }?.let { runCatching { System.load(it.absolutePath) } } }
        installed = true
    }

    private fun addNativeDir(d: File) = runCatching {
        val cl = ctx.classLoader
        val pl = dalvik.system.BaseDexClassLoader::class.java.getDeclaredField("pathList").apply { isAccessible = true }.get(cl)!!
        val m = pl.javaClass.getDeclaredMethod("addNativePath", Collection::class.java).apply { isAccessible = true }
        m.invoke(pl, listOf(d))
    }

    private fun open(url: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000; readTimeout = 30_000; instanceFollowRedirects = true
        if (responseCode != HttpURLConnection.HTTP_OK) throw IOException("تعذّر الوصول للخادم ($responseCode)")
    }

    private fun fetch(url: String) = open(url).inputStream

    companion object {
        const val PACK_ID = "libs-1"
        const val PACK_TAG = "packs-v1"
        private val ORDER = listOf("libonnxruntime.so", "libsherpa-onnx-c-api.so", "libsherpa-onnx-cxx-api.so", "libsherpa-onnx-jni.so")
    }
}
