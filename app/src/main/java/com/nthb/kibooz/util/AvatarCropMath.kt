package com.nthb.kibooz.util

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Batas persegi piksel pada bitmap sumber untuk hasil crop foto profil. */
data class CropRectPx(val left: Int, val top: Int, val size: Int)

/**
 * Fungsi matematika murni untuk downsampling, rotasi EXIF, pembatasan skala/posisi pan, dan perhitungan kotak
 * crop gambar profil agar mudah diuji unit test.
 */
object AvatarCropMath {

    const val MAX_DECODE_SIDE = 2048
    const val MAX_OUTPUT_SIDE = 720
    const val MAX_UPLOAD_BYTES = 2 * 1024 * 1024 // 2 MiB sesuai batas POST /users/avatar

    /** Faktor pembagi pangkat 2 agar dimensi terbesar gambar setelah dekode tidak jauh melebihi [maxSide]. */
    fun computeSampleSize(width: Int, height: Int, maxSide: Int = MAX_DECODE_SIDE): Int {
        if (width <= 0 || height <= 0 || maxSide <= 0) return 1
        var sample = 1
        var halfW = width
        var halfH = height
        while ((halfW / 2 >= maxSide) || (halfH / 2 >= maxSide)) {
            sample *= 2
            halfW /= 2
            halfH /= 2
        }
        return sample
    }

    /** Skala minimum agar bitmap berukuran [bitmapW]x[bitmapH] selalu menutupi seluruh kotak crop [cropSizePx]. */
    fun minCoverScale(bitmapW: Int, bitmapH: Int, cropSizePx: Float): Float {
        if (bitmapW <= 0 || bitmapH <= 0 || cropSizePx <= 0f) return 1f
        return max(cropSizePx / bitmapW.toFloat(), cropSizePx / bitmapH.toFloat())
    }

    /** Membatasi posisi [origin] (left atau top gambar terskala) agar sisi gambar tidak lepas dari rentang `cropStart..cropEnd`. */
    fun clampAxisOrigin(origin: Float, scaledLength: Float, cropStart: Float, cropEnd: Float): Float {
        val minOrigin = cropEnd - scaledLength
        val maxOrigin = cropStart
        return if (minOrigin > maxOrigin) {
            cropStart + ((cropEnd - cropStart) - scaledLength) / 2f
        } else {
            origin.coerceIn(minOrigin, maxOrigin)
        }
    }

    /** Mengamankan koordinat kotak crop hasil inversi matriks agar berada dalam dimensi bitmap [bitmapW]x[bitmapH]. */
    fun clampCropRect(
        rawLeft: Float,
        rawTop: Float,
        rawRight: Float,
        rawBottom: Float,
        bitmapW: Int,
        bitmapH: Int
    ): CropRectPx {
        if (bitmapW <= 0 || bitmapH <= 0) return CropRectPx(0, 0, 1)
        val rawSize = min(rawRight - rawLeft, rawBottom - rawTop).roundToInt().coerceAtLeast(1)
        val size = min(rawSize, min(bitmapW, bitmapH)).coerceAtLeast(1)
        val maxLeft = (bitmapW - size).coerceAtLeast(0)
        val maxTop = (bitmapH - size).coerceAtLeast(0)
        val left = rawLeft.roundToInt().coerceIn(0, maxLeft)
        val top = rawTop.roundToInt().coerceIn(0, maxTop)
        return CropRectPx(left = left, top = top, size = size)
    }

    /** Memilih sisi keluaran persegi terbaik tanpa memperbesar gambar kecil secara berlebihan. */
    fun outputSidePx(croppedSizePx: Int, maxOutputSide: Int = MAX_OUTPUT_SIDE): Int =
        croppedSizePx.coerceIn(1, maxOutputSide)
}
