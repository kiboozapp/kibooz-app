package com.nthb.kibooz.ml

import android.graphics.Bitmap

/** Nama konstanta sama dengan nilai `trashType` di API, jadi jangan diubah. */
enum class TrashCategory { ORGANIK, ANORGANIK, B3 }

data class TrashResult(val category: TrashCategory, val confidence: Float)

/**
 * Menebak kategori sampah dari satu frame kamera.
 *
 * Dipanggil dari thread analyzer kamera, jadi boleh memblokir. Bitmap hanya dipakai selama pemanggilan
 * dan tidak boleh disimpan atau ditulis ke berkas.
 */
interface TrashClassifier : AutoCloseable {
    fun classify(frame: Bitmap): TrashResult

    /** True selama modelnya pengganti sementara (lihat [ModelConfig.placeholder]). Pengganti selalu dianggap contoh. */
    val isPlaceholder: Boolean get() = true

    override fun close() {}
}
