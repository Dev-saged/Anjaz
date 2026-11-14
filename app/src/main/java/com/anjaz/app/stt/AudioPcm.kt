package com.anjaz.app.stt

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.nio.ByteOrder

/** يفك أي ملف صوت (m4a/mp3/ogg…) إلى عينات Float أحادية 16kHz كما يتطلب Whisper. */
object AudioPcm {
    const val RATE = 16_000

    fun decode(path: String): FloatArray {
        val ex = MediaExtractor()
        ex.setDataSource(path)
        val track = (0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
            ?: error("لا مسار صوتي")
        ex.selectTrack(track)
        val fmt = ex.getTrackFormat(track)
        val codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(fmt, null, null, 0); codec.start()
        var rate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val out = ShortList()
        val info = MediaCodec.BufferInfo()
        var inputDone = false; var outputDone = false
        try {
            while (!outputDone) {
                if (!inputDone) {
                    val i = codec.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        val buf = codec.getInputBuffer(i)!!
                        val n = ex.readSampleData(buf, 0)
                        if (n < 0) { codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true }
                        else { codec.queueInputBuffer(i, 0, n, ex.sampleTime, 0); ex.advance() }
                    }
                }
                val o = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    o >= 0 -> {
                        val buf = codec.getOutputBuffer(o)!!.order(ByteOrder.LITTLE_ENDIAN)
                        val sb = buf.asShortBuffer()
                        val arr = ShortArray(info.size / 2); sb.get(arr)
                        out.addAll(arr)
                        codec.releaseOutputBuffer(o, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                    o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val f = codec.outputFormat
                        rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE); channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                }
            }
        } finally { runCatching { codec.stop() }; codec.release(); ex.release() }
        return toMono16k(out.toArray(), rate, channels)
    }

    fun toMono16k(pcm: ShortArray, rate: Int, channels: Int): FloatArray {
        val frames = pcm.size / channels
        val mono = FloatArray(frames) { f -> var s = 0f; for (c in 0 until channels) s += pcm[f * channels + c]; s / channels / 32768f }
        return resample(mono, rate, RATE)
    }

    fun resample(x: FloatArray, from: Int, to: Int): FloatArray {
        if (from == to || x.isEmpty()) return x
        val n = (x.size.toLong() * to / from).toInt()
        val ratio = from.toDouble() / to
        return FloatArray(n) { i ->
            val p = i * ratio; val k = p.toInt(); val f = (p - k).toFloat()
            val a = x[minOf(k, x.size - 1)]; val b = x[minOf(k + 1, x.size - 1)]
            a + (b - a) * f
        }
    }

    private class ShortList {
        private var a = ShortArray(1 shl 16); private var n = 0
        fun addAll(s: ShortArray) { if (n + s.size > a.size) a = a.copyOf(maxOf(a.size * 2, n + s.size)); System.arraycopy(s, 0, a, n, s.size); n += s.size }
        fun toArray() = a.copyOf(n)
    }

    /** قصّ الصوت إلى مقاطع ≤28 ثانية لأن Whisper يعالج نافذة 30 ثانية. */
    fun chunks(x: FloatArray, seconds: Int = 28): List<FloatArray> =
        if (x.size <= seconds * RATE) listOf(x) else x.toList().chunked(seconds * RATE).map { it.toFloatArray() }
}
