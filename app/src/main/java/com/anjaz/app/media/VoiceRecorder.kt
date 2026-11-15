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

    /** مستوى الصوت اللحظي 0..1 لرسم الموجة. */
    fun level(): Float = runCatching { ((rec?.maxAmplitude ?: 0) / 24_000f).coerceIn(0f, 1f) }.getOrDefault(0f)

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

/** مشغّل واحد مشترك: تشغيل رسالة يوقف السابقة، مع إيقاف مؤقت وتقديم وسرعة 1× / 1.5× / 2×. */
object VoicePlayer {
    private var mp: MediaPlayer? = null
    var playingId: String? by androidx.compose.runtime.mutableStateOf(null)
        private set
    var playing: Boolean by androidx.compose.runtime.mutableStateOf(false)
        private set
    var speed: Float by androidx.compose.runtime.mutableFloatStateOf(1f)
        private set

    fun position(): Int = runCatching { mp?.currentPosition ?: 0 }.getOrDefault(0)

    fun play(id: String, path: String, startMs: Int = 0) {
        if (playingId == id && mp != null) {
            if (startMs > 0) runCatching { mp?.seekTo(startMs) }
            if (playing && startMs == 0) { runCatching { mp?.pause() }; playing = false } else { runCatching { mp?.start(); mp?.let(::applySpeed) }; playing = true }
            return
        }
        stop()
        mp = runCatching {
            MediaPlayer().apply {
                setDataSource(path); prepare()
                setOnCompletionListener { stop() }
                if (startMs > 0) seekTo(startMs)
                start()
                applySpeed(this)
            }
        }.getOrNull()
        playingId = if (mp != null) id else null
        playing = mp != null
    }

    fun seek(id: String, ms: Int) { if (playingId == id) runCatching { mp?.seekTo(ms) } }

    fun cycleSpeed() {
        speed = when (speed) { 1f -> 1.5f; 1.5f -> 2f; else -> 1f }
        mp?.let { if (playing) applySpeed(it) }
    }

    private fun applySpeed(p: MediaPlayer) {
        runCatching { p.playbackParams = p.playbackParams.setSpeed(speed) }
    }

    fun stop() {
        runCatching { mp?.stop() }; runCatching { mp?.release() }
        mp = null; playingId = null; playing = false
    }
}
