package com.nthb.kibooz.ml

import android.graphics.Bitmap

/**
 * Pengganti sementara sampai model `kibooz_trash_classifier_v1.tflite` ada. Selalu mengembalikan hasil yang
 * sama. Ubah [result] untuk mencoba batas keyakinan 75% atau kategori B3.
 */
class StubTrashClassifier(
    private val result: TrashResult = TrashResult(TrashCategory.ANORGANIK, 0.9f)
) : TrashClassifier {

    override fun classify(frame: Bitmap): TrashResult = result
}
