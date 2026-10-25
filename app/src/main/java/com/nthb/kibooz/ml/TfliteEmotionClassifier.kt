package com.nthb.kibooz.ml

import android.content.Context
import android.graphics.Bitmap

/**
 * [EmotionClassifier] di atas model TFLite dari `assets/`. Probabilitas label model dijumlahkan per kelompok
 * di konfigurasinya (misalnya happy dan neutral menjadi SENANG), dan keyakinannya adalah jumlah itu.
 */
class TfliteEmotionClassifier private constructor(private val model: LoadedModel) : EmotionClassifier {

    override val isPlaceholder: Boolean get() = model.config.placeholder

    override fun classify(face: Bitmap): EmotionResult {
        val probabilities = model.runner.probabilities(face)
        val mass = ModelMath.groupMass(probabilities, model.runner.labels, model.config.groups)
        val (group, confidence) = ModelMath.best(mass)

        return EmotionResult(Emotion.valueOf(group), confidence)
    }

    override fun close() = model.close()

    companion object {
        const val ASSET_BASE = "kibooz_emotion_v1"

        fun fromAssets(context: Context, base: String = ASSET_BASE) =
            TfliteEmotionClassifier(ModelAssets.load(context, base, Emotion.entries.map { it.name }.toSet()))
    }
}
