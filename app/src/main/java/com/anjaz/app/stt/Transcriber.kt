package com.anjaz.app.stt

import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** تحويل الصوت إلى نص بـ Whisper على الجهاز؛ المحرّك يُحمَّل عند أول استخدام ويُحرَّر عند الطلب. */
@Singleton
class Transcriber @Inject constructor(private val model: WhisperModel) {
    private val lock = Mutex()
    private var rec: OfflineRecognizer? = null

    private fun engine(): OfflineRecognizer = rec ?: OfflineRecognizer(
        config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = AudioPcm.RATE, featureDim = 80),
            modelConfig = OfflineModelConfig(
                whisper = OfflineWhisperModelConfig(encoder = model.encoder, decoder = model.decoder, language = "ar", task = "transcribe", tailPaddings = 300),
                tokens = model.tokens, numThreads = 2, provider = "cpu", modelType = "whisper",
            ),
            decodingMethod = "greedy_search",
        ),
    ).also { rec = it }

    suspend fun fromFile(path: String): String = fromPcm(AudioPcm.decode(path))

    suspend fun fromPcm(pcm: FloatArray): String = withContext(Dispatchers.Default) {
        lock.withLock {
            val e = engine()
            AudioPcm.chunks(pcm).joinToString(" ") { part ->
                val s = e.createStream()
                try { s.acceptWaveform(part, AudioPcm.RATE); e.decode(s); e.getResult(s).text.trim() } finally { s.release() }
            }.trim()
        }
    }

    suspend fun release() = lock.withLock { rec?.release(); rec = null }
}
