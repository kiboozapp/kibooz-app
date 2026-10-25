package com.nthb.kibooz.ml

import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy

/**
 * Membaca setiap frame kamera dan menebak kategori sampah di depannya lewat [TrashClassifier].
 *
 * Gunakan dengan ImageAnalysis berformat YUV_420_888 dan strategi KEEP_ONLY_LATEST. Semua pekerjaan berjalan di
 * thread analyzer, termasuk callback [Listener], jadi pindahkan ke thread utama sebelum menyentuh tampilan.
 *
 * Gambar frame hanya ada di memori selama satu pemanggilan dan langsung di-recycle. Tidak ada yang disimpan
 * atau dikirim, hanya kategori dan keyakinan yang dilaporkan.
 */
class TrashAnalyzer(
    private val classifier: TrashClassifier,
    private val listener: Listener,
    private val smoother: TrashSmoother = TrashSmoother(),
    private val nowMillis: () -> Long = SystemClock::elapsedRealtime
) : ImageAnalysis.Analyzer, AutoCloseable {

    interface Listener {
        /** Hasil yang sudah stabil, atau null selama belum ada kategori yang bertahan. */
        fun onResult(result: TrashResult?)

        /** Dipanggil hanya saat keadaan berubah, termasuk sekali pada frame pertama. */
        fun onLowLight(isLow: Boolean)
    }

    private var lowLight: Boolean? = null

    override fun analyze(imageProxy: ImageProxy) {
        try {
            reportLight(imageProxy)

            val frame = imageProxy.toUprightBitmap(imageProxy.imageInfo.rotationDegrees)
            val result = try {
                classifier.classify(frame)
            } finally {
                frame.recycle()
            }

            listener.onResult(smoother.update(result, nowMillis()))
        } catch (e: Exception) {
            // Galat model TFLite, bitmap tidak valid atau callback yang melempar tidak boleh menjatuhkan proses
            Log.w(TAG, "Analisis frame gagal, frame dilewati", e)
        } catch (e: OutOfMemoryError) {
            // Bitmap satu frame penuh bisa gagal dialokasikan di ponsel kecil. Lewati frame ini dan coba lagi di frame berikutnya
            Log.w(TAG, "Memori habis saat menganalisis frame, frame dilewati", e)
        } finally {
            imageProxy.close()
        }
    }

    override fun close() {
        classifier.close()
    }

    private fun reportLight(imageProxy: ImageProxy) {
        val plane = imageProxy.planes[0]
        val low = isLowLight(
            meanLuma(plane.buffer, imageProxy.width, imageProxy.height, plane.rowStride, plane.pixelStride)
        )

        if (low != lowLight) {
            lowLight = low
            listener.onLowLight(low)
        }
    }

    private companion object {
        const val TAG = "TrashAnalyzer"
    }
}
