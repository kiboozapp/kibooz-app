package com.nthb.kibooz.ml

import android.graphics.Bitmap

/**
 * Pengganti sementara sampai model `kibooz_emotion_v1.tflite` ada. Selalu mengembalikan hasil yang sama,
 * supaya label bisa bertahan cukup lama untuk diterima analyzer. Ubah [result] untuk mencoba batas
 * keyakinan 75%.
 */
class StubEmotionClassifier(
    private val result: EmotionResult = EmotionResult(Emotion.SENANG, 0.92f)
) : EmotionClassifier {

    override fun classify(face: Bitmap): EmotionResult = result
}
