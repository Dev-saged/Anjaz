package com.anjaz.app.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

class CompressedVideo(val file: File, val durMs: Long, val width: Int, val height: Int)

/** ضغط الفيديو H.264 + AAC: الدقة والمعدل يُختاران من المدة ليبقى الناتج تحت 9MB بأعلى جودة ممكنة. */
@UnstableApi
object VideoCompressor {
    const val MAX_DURATION_MS = 90_000L
    const val TARGET_BYTES = 9_000_000L
    private const val AUDIO_BPS = 64_000

    /** معدل بتات الفيديو: الأعلى المسموح بحيث لا يتجاوز الحجم الهدف، بين 350kbps و2.5Mbps. */
    fun videoBitrate(durMs: Long): Int {
        val bits = TARGET_BYTES * 8 * 0.92
        val total = (bits / (durMs.coerceAtLeast(1000) / 1000.0)).toLong() - AUDIO_BPS
        return total.coerceIn(350_000, 2_500_000).toInt()
    }

    fun heightFor(bps: Int) = if (bps >= 1_200_000) 720 else if (bps >= 700_000) 540 else 480

    fun probe(ctx: Context, uri: Uri): Long {
        val r = MediaMetadataRetriever()
        return try { r.setDataSource(ctx, uri); r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L } finally { r.release() }
    }

    suspend fun compress(ctx: Context, uri: Uri, out: File): CompressedVideo {
        val dur = withContext(Dispatchers.IO) { probe(ctx, uri) }
        require(dur > 0) { "فيديو غير صالح" }
        require(dur <= MAX_DURATION_MS) { "الحد الأقصى للفيديو 90 ثانية" }
        val bps = videoBitrate(dur)
        val h = heightFor(bps)
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val encoder = DefaultEncoderFactory.Builder(ctx).setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(bps).build()).build()
                val t = Transformer.Builder(ctx).setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC).setEncoderFactory(encoder)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: androidx.media3.transformer.Composition, exportResult: ExportResult) { if (cont.isActive) cont.resume(Unit) }
                        override fun onError(composition: androidx.media3.transformer.Composition, exportResult: ExportResult, exportException: ExportException) { if (cont.isActive) cont.resumeWithException(exportException) }
                    }).build()
                val item = EditedMediaItem.Builder(MediaItem.fromUri(uri)).setEffects(Effects(emptyList(), listOf(Presentation.createForHeight(h)))).build()
                out.delete()
                t.start(item, out.path)
                cont.invokeOnCancellation { t.cancel() }
            }
        }
        require(out.length() in 1..(TARGET_BYTES + 1_000_000)) { "تعذّر ضغط الفيديو إلى الحجم المسموح" }
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(out.path)
            CompressedVideo(out, r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: dur,
                r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0, r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0)
        } finally { r.release() }
    }
}
