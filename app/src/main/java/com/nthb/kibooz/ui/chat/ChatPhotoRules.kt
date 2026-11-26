package com.nthb.kibooz.ui.chat

import com.nthb.kibooz.data.remote.CHAT_PHOTO_MAX_BYTES
import com.nthb.kibooz.data.remote.CHAT_PHOTO_TYPES
import kotlin.math.max
import kotlin.math.roundToInt

/** Aturan foto chat sebelum diunggah, dipisah dari Android supaya bisa diuji. */
object ChatPhotoRules {

    /** Sisi terpanjang yang disarankan `docs/client-guide.md` backend, cukup untuk layar. */
    const val MAX_SIDE = 1600

    /**
     * Foto dikodekan ulang menjadi JPEG yang lebih kecil jika jenisnya ditolak server (misalnya HEIC), ukurannya di
     * atas batas atau tidak diketahui, atau sisinya lebih panjang dari [MAX_SIDE].
     */
    fun needsReencode(contentType: String, sizeBytes: Long?, width: Int, height: Int): Boolean =
        contentType !in CHAT_PHOTO_TYPES ||
            sizeBytes == null ||
            sizeBytes !in 1..CHAT_PHOTO_MAX_BYTES ||
            max(width, height) > MAX_SIDE

    /** Ukuran dengan perbandingan yang sama dan sisi terpanjang paling banyak [maxSide]. Gambar kecil tidak diperbesar. */
    fun fitWithin(width: Int, height: Int, maxSide: Int): Pair<Int, Int> {
        val longest = max(width, height)
        if (longest <= maxSide) return width to height

        val scale = maxSide.toDouble() / longest
        return (width * scale).roundToInt().coerceAtLeast(1) to (height * scale).roundToInt().coerceAtLeast(1)
    }
}
