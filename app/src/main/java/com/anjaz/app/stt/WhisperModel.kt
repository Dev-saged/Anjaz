package com.anjaz.app.stt

import android.content.Context
import com.anjaz.app.update.AppFolder
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

sealed interface ModelState {
    data object Missing : ModelState
    data class Downloading(val percent: Int) : ModelState
    data object Ready : ModelState
    data class Failed(val reason: String) : ModelState
}

/** نموذج Whisper (base int8 ≈160MB) يُنزَّل مرة واحدة ثم يعمل بلا إنترنت؛ التنزيل يستأنف من حيث توقف. */
@Singleton
class WhisperModel @Inject constructor(@ApplicationContext private val ctx: Context) {
    companion object {
        const val NAME = "base"
        private const val BASE = "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-$NAME/resolve/main/"
        val FILES = listOf("$NAME-encoder.int8.onnx", "$NAME-decoder.int8.onnx", "$NAME-tokens.txt")
    }

    val dir: File get() = File(AppFolder.root(ctx), "models/whisper-$NAME").apply { mkdirs() }
    fun file(n: String) = File(dir, n)
    val encoder get() = file(FILES[0]).path
    val decoder get() = file(FILES[1]).path
    val tokens get() = file(FILES[2]).path

    private val _state = MutableStateFlow<ModelState>(if (installed()) ModelState.Ready else ModelState.Missing)
    val state: StateFlow<ModelState> = _state

    private fun installed() = File(dir, ".ok").exists() && FILES.all { file(it).length() > 0 }

    suspend fun download() = withContext(Dispatchers.IO) {
        if (_state.value is ModelState.Downloading || installed()) { if (installed()) _state.value = ModelState.Ready; return@withContext }
        _state.value = ModelState.Downloading(0)
        try {
            val sizes = FILES.map { head(it) }
            val total = sizes.sum().coerceAtLeast(1)
            var done = 0L
            FILES.forEachIndexed { idx, name ->
                val dest = file(name)
                if (dest.exists() && dest.length() == sizes[idx]) { done += sizes[idx]; return@forEachIndexed }
                val part = File(dest.path + ".part")
                var have = if (part.exists()) part.length() else 0L
                val c = (URL(BASE + name).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 20_000; readTimeout = 30_000; instanceFollowRedirects = true
                    if (have > 0) setRequestProperty("Range", "bytes=$have-")
                }
                try {
                    val code = c.responseCode
                    if (code == 200) { have = 0 } else if (code != 206) error("HTTP $code")
                    java.io.FileOutputStream(part, code == 206).use { out ->
                        c.inputStream.use { i ->
                            val buf = ByteArray(64 * 1024); var last = -1
                            var got = have
                            while (true) {
                                val n = i.read(buf); if (n < 0) break
                                out.write(buf, 0, n); got += n
                                val pct = ((done + got) * 100 / total).toInt()
                                if (pct != last) { last = pct; _state.value = ModelState.Downloading(pct) }
                            }
                        }
                    }
                } finally { c.disconnect() }
                check(part.length() == sizes[idx]) { "حجم ملف غير مكتمل" }
                dest.delete(); part.renameTo(dest); done += sizes[idx]
            }
            File(dir, ".ok").writeText(NAME)
            _state.value = ModelState.Ready
        } catch (e: Exception) {
            _state.value = ModelState.Failed(e.message ?: "تعذّر التنزيل")
        }
    }

    private fun head(name: String): Long {
        val c = (URL(BASE + name).openConnection() as HttpURLConnection).apply { requestMethod = "HEAD"; connectTimeout = 20_000; instanceFollowRedirects = true }
        return try { c.contentLengthLong.also { check(it > 0) { "حجم غير معروف" } } } finally { c.disconnect() }
    }

    fun delete() { dir.deleteRecursively(); _state.value = ModelState.Missing }
}
