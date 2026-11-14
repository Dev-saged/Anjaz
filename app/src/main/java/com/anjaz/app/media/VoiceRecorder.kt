package com.anjaz.app.media

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import java.io.File

/** تسجيل صوت AAC أحادي بمعدل 32kbps: ≈240KB للدقيقة بجودة كلام واضحة. */
class VoiceRecorder(private val ctx: Context) {
    private var rec: MediaRecorder? = null
    private var file: File? = null
    private var started = 0L

    fun start(): Boolean {
        val f = File(ctx.cacheDir, "rec_${System.nanoTime()}.m4a")
        @Suppress("DEPRECATION")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else MediaRecorder()
        return runCatching {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioChannels(1); r.setAudioSamplingRate(22_050); r.setAudioEncodingBitRate(32_000)
            r.setMaxDuration(5 * 60_000)
            r.setOutputFile(f.path)
            r.prepare(); r.start()
            rec = r; file = f; started = SystemClock.elapsedRealtime(); true
        }.getOrElse { runCatching { r.release() }; f.delete(); false }
    }

    /** يُنهي التسجيل ويعيد الملف والمدة، أو null عند الإلغاء/الفشل. */
    fun stop(cancel: Boolean): Pair<File, Long>? {
        val r = rec ?: return null
        rec = null
        val dur = SystemClock.elapsedRealtime() - started
        val ok = runCatching { r.stop() }.isSuccess
        runCatching { r.release() }
        val f = file ?: return null
        file = null
        if (cancel || !ok) { f.delete(); return null }
        return f to dur
    }
}

/** مشغّل واحد مشترك: تشغيل رسالة يوقف السابقة. */
object VoicePlayer {
    private var mp: MediaPlayer? = null
    var playingId: String? = null
        private set

    fun toggle(id: String, path: String, onChange: () -> Unit) {
        if (playingId == id) { stop(); onChange(); return }
        stop()
        mp = runCatching {
            MediaPlayer().apply {
                setDataSource(path); prepare()
                setOnCompletionListener { stop(); onChange() }
                start()
            }
        }.getOrNull()
        playingId = if (mp != null) id else null
        onChange()
    }

    fun stop() {
        runCatching { mp?.stop() }; runCatching { mp?.release() }
        mp = null; playingId = null
    }
}
