package com.nthb.kibooz.ml

import java.nio.ByteBuffer

/** Rata-rata luma di bawah angka ini dianggap terlalu gelap (lihat 04_AI_ML_AND_CAMERA_PIPELINE.md, bagian 5). */
const val LOW_LIGHT_LUMA = 40f

/**
 * Menghitung rata-rata luma dari bidang Y sebuah frame YUV, dengan mengambil satu piksel tiap [step] baris
 * dan kolom supaya murah dipanggil di setiap frame. Mengembalikan 0 jika tidak ada piksel yang terbaca.
 */
fun meanLuma(
    plane: ByteBuffer,
    width: Int,
    height: Int,
    rowStride: Int,
    pixelStride: Int,
    step: Int = 16
): Float {
    val buffer = plane.duplicate()
    var sum = 0L
    var count = 0

    for (y in 0 until height step step) {
        for (x in 0 until width step step) {
            val index = y * rowStride + x * pixelStride
            if (index >= buffer.limit()) continue
            sum += buffer.get(index).toInt() and 0xFF
            count++
        }
    }

    return if (count == 0) 0f else sum.toFloat() / count
}

fun isLowLight(meanLuma: Float): Boolean = meanLuma < LOW_LIGHT_LUMA
