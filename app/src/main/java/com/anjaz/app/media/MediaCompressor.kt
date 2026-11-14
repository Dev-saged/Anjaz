package com.anjaz.app.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

class Compressed(val bytes: ByteArray, val width: Int, val height: Int)

/** ضغط الصور بحجم ≤1600px وجودة JPEG 82 للحفاظ على الوضوح مع حجم صغير. */
object MediaCompressor {
    const val MAX_SIDE = 1600
    const val QUALITY = 82
    const val FILE_MAX = 6_000_000L

    fun fit(w: Int, h: Int, max: Int = MAX_SIDE): Pair<Int, Int> {
        val m = maxOf(w, h)
        if (m <= max) return w to h
        val s = max.toDouble() / m
        return maxOf(1, (w * s).roundToInt()) to maxOf(1, (h * s).roundToInt())
    }

    fun sampleSize(w: Int, h: Int, max: Int = MAX_SIDE): Int {
        var s = 1
        while (maxOf(w, h) / (s * 2) >= max) s *= 2
        return s
    }

    fun image(ctx: Context, uri: Uri): Compressed {
        val cr = ctx.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "صورة غير صالحة" }
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight) }
        var bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: error("تعذّر قراءة الصورة")
        val degrees = runCatching {
            cr.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            }
        }.getOrNull() ?: 0f
        val (fw, fh) = fit(bmp.width, bmp.height)
        if (fw != bmp.width || degrees != 0f) {
            val m = Matrix().apply { postScale(fw.toFloat() / bmp.width, fh.toFloat() / bmp.height); postRotate(degrees) }
            val out = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            if (out !== bmp) bmp.recycle()
            bmp = out
        }
        val buf = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, QUALITY, buf)
        return Compressed(buf.toByteArray(), bmp.width, bmp.height).also { bmp.recycle() }
    }
}
