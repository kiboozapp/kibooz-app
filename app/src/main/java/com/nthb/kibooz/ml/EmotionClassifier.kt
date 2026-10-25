package com.nthb.kibooz.ml

import android.graphics.Bitmap

/** Nama konstanta sama dengan nilai `moodType` di API, jadi jangan diubah. */
enum class Emotion { SENANG, SEDIH, MARAH, BINGUNG }

data class EmotionResult(val emotion: Emotion, val confidence: Float)

/**
 * Menebak emosi dari satu potongan wajah.
 *
 * Dipanggil dari thread analyzer kamera, jadi boleh memblokir. Bitmap hanya dipakai selama pemanggilan
 * dan tidak boleh disimpan atau ditulis ke berkas.
 */
interface EmotionClassifier : AutoCloseable {
    fun classify(face: Bitmap): EmotionResult

    /** True selama modelnya pengganti sementara (lihat [ModelConfig.placeholder]). Pengganti selalu dianggap contoh. */
    val isPlaceholder: Boolean get() = true

    override fun close() {}
}
