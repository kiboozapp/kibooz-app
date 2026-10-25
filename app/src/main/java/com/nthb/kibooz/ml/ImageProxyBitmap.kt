package com.nthb.kibooz.ml

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageProxy

/**
 * Bitmap frame dalam arah tegak. ImageProxy.toBitmap tidak memutar gambar, sedangkan kotak wajah ML Kit dan
 * model klasifikasi memakai arah tegak. Pemanggil wajib me-recycle hasilnya.
 */
fun ImageProxy.toUprightBitmap(rotation: Int): Bitmap {
    val bitmap = toBitmap()
    if (rotation == 0) return bitmap

    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
    val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    if (rotated !== bitmap) bitmap.recycle()

    return rotated
}
