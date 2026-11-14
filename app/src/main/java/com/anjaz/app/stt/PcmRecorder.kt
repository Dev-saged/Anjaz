package com.anjaz.app.stt

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder

/** تسجيل خام 16kHz أحادي للإملاء الصوتي؛ يُجمع في الذاكرة (حد أقصى دقيقتان) ثم يُمرَّر إلى Whisper. */
class PcmRecorder {
    private var rec: AudioRecord? = null
    private var thread: Thread? = null
    @Volatile private var running = false
    private val data = java.io.ByteArrayOutputStream()

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        val min = AudioRecord.getMinBufferSize(AudioPcm.RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (min <= 0) return false
        val r = runCatching { AudioRecord(MediaRecorder.AudioSource.MIC, AudioPcm.RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, min * 4) }.getOrNull() ?: return false
        if (r.state != AudioRecord.STATE_INITIALIZED) { r.release(); return false }
        rec = r; data.reset(); running = true; r.startRecording()
        thread = Thread {
            val buf = ByteArray(min)
            while (running && data.size() < AudioPcm.RATE * 2 * 120) { val n = r.read(buf, 0, buf.size); if (n > 0) data.write(buf, 0, n) else if (n < 0) break }
        }.also { it.start() }
        return true
    }

    fun stop(): FloatArray {
        running = false
        thread?.join(500)
        rec?.let { runCatching { it.stop() }; it.release() }
        rec = null
        val b = data.toByteArray()
        val s = ShortArray(b.size / 2) { ((b[it * 2 + 1].toInt() shl 8) or (b[it * 2].toInt() and 0xff)).toShort() }
        return FloatArray(s.size) { s[it] / 32768f }
    }
}
