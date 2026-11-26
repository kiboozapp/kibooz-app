package com.nthb.kibooz.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.InputStream

/**
 * Dekode gambar dengan sampling supaya gambar besar tidak menghabiskan memori, lalu diputar sesuai EXIF.
 * [open] dipanggil beberapa kali dan harus memberi aliran baru dari awal. Null jika gambarnya tidak terbaca.
 * Panggil di luar main thread.
 */
fun decodeOrientedBitmap(maxSide: Int = AvatarCropMath.MAX_DECODE_SIDE, open: () -> InputStream?): Bitmap? = try {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    open()?.use { BitmapFactory.decodeStream(it, null, bounds) }

    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
        null
    } else {
        val options = BitmapFactory.Options().apply {
            inSampleSize = AvatarCropMath.computeSampleSize(bounds.outWidth, bounds.outHeight, maxSide)
        }
        open()?.use { BitmapFactory.decodeStream(it, null, options) }?.rotated(exifDegrees(open))
    }
} catch (_: Exception) {
    null
} catch (_: OutOfMemoryError) {
    null
}

// Format tanpa EXIF bisa membuat pembacanya melempar, dan gambarnya tetap dipakai apa adanya
private fun exifDegrees(open: () -> InputStream?): Float {
    val orientation = try {
        open()?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }
    } catch (_: Exception) {
        null
    }

    return when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
}

private fun Bitmap.rotated(degrees: Float): Bitmap {
    if (degrees == 0f) return this

    val matrix = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true).also { if (it !== this) recycle() }
}
