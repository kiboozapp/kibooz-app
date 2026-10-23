package com.nthb.kibooz.ml

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.ExecutionException

/** Ukuran frame setelah diputar tegak, sama dengan sistem koordinat [TrackedFace.box]. */
data class FrameSize(val width: Int, val height: Int)

/** [emotion] kosong selama label belum bertahan cukup lama (lihat [EmotionSmoother]). */
data class TrackedFace(val trackingId: Int, val box: Rect, val emotion: EmotionResult?)

/**
 * Membaca setiap frame kamera: mendeteksi banyak wajah, menebak emosi tiap wajah dan melaporkan hasilnya.
 *
 * Gunakan dengan ImageAnalysis berformat YUV_420_888 dan strategi KEEP_ONLY_LATEST. Semua pekerjaan berjalan di
 * thread analyzer, termasuk callback [Listener], jadi pindahkan ke thread utama sebelum menyentuh tampilan.
 *
 * Gambar wajah hanya ada di memori selama satu frame dan langsung di-recycle. Tidak ada yang disimpan atau dikirim,
 * hanya label dan keyakinan yang dilaporkan.
 */
class MoodAnalysisAnalyzer(
    private val classifier: EmotionClassifier,
    private val listener: Listener,
    private val smoother: EmotionSmoother = EmotionSmoother(),
    private val faceDetector: FaceDetector = createFaceDetector(),
    private val nowMillis: () -> Long = SystemClock::elapsedRealtime
) : ImageAnalysis.Analyzer, AutoCloseable {

    interface Listener {
        fun onFaces(frame: FrameSize, faces: List<TrackedFace>)

        /** Dipanggil hanya saat keadaan berubah, termasuk sekali pada frame pertama. */
        fun onLowLight(isLow: Boolean)
    }

    private var lowLight: Boolean? = null

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        try {
            reportLight(imageProxy)

            val mediaImage = imageProxy.image ?: return
            val rotation = imageProxy.imageInfo.rotationDegrees
            val upright = if (rotation % 180 == 0) {
                FrameSize(imageProxy.width, imageProxy.height)
            } else {
                FrameSize(imageProxy.height, imageProxy.width)
            }

            val faces = Tasks.await(faceDetector.process(InputImage.fromMediaImage(mediaImage, rotation)))
                .filter { it.trackingId != null }
                .sortedByDescending { it.boundingBox.width() * it.boundingBox.height() }
                .take(MAX_FACES)

            val tracked = if (faces.isEmpty()) emptyList() else classify(imageProxy, rotation, faces)

            smoother.retain(tracked.mapTo(mutableSetOf()) { it.trackingId })
            listener.onFaces(upright, tracked)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (e: ExecutionException) {
            Log.w(TAG, "Deteksi wajah gagal, frame dilewati", e)
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
        faceDetector.close()
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

    private fun classify(imageProxy: ImageProxy, rotation: Int, faces: List<Face>): List<TrackedFace> {
        val frame = uprightBitmap(imageProxy, rotation)

        try {
            return faces.mapNotNull { classifyFace(frame, it) }
        } finally {
            frame.recycle()
        }
    }

    private fun classifyFace(frame: Bitmap, face: Face): TrackedFace? {
        val trackingId = face.trackingId ?: return null
        val box = Rect(face.boundingBox)

        // Kotak wajah bisa sedikit keluar dari frame, dan potongan yang terlalu kecil tidak bisa diklasifikasi
        if (!box.intersect(0, 0, frame.width, frame.height) || box.width() < MIN_CROP_PIXELS || box.height() < MIN_CROP_PIXELS) {
            return null
        }

        val crop = Bitmap.createBitmap(frame, box.left, box.top, box.width(), box.height())

        val result = try {
            classifier.classify(crop)
        } finally {
            // createBitmap mengembalikan frame itu sendiri jika kotaknya menutupi seluruh frame
            if (crop !== frame) crop.recycle()
        }

        return TrackedFace(trackingId, box, smoother.update(trackingId, result, nowMillis()))
    }

    // ImageProxy.toBitmap tidak memutar gambar, sedangkan kotak wajah ML Kit sudah dalam arah tegak
    private fun uprightBitmap(imageProxy: ImageProxy, rotation: Int): Bitmap {
        val bitmap = imageProxy.toBitmap()
        if (rotation == 0) return bitmap

        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()

        return rotated
    }

    companion object {
        private const val TAG = "MoodAnalysisAnalyzer"

        /** Batas wajah yang dianalisis per frame, diambil dari yang kotaknya paling besar. */
        const val MAX_FACES = 10

        private const val MIN_CROP_PIXELS = 16

        fun createFaceDetector(): FaceDetector = FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .setMinFaceSize(0.1f)
                .enableTracking()
                .build()
        )
    }
}
