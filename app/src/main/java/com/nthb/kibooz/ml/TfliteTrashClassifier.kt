package com.nthb.kibooz.ml

import android.content.Context
import android.graphics.Bitmap

/**
 * [TrashClassifier] di atas model TFLite dari `assets/`. Model pengganti mengenali 1000 kelas ImageNet, dan
 * konfigurasinya mengelompokkan label yang relevan menjadi ORGANIK, ANORGANIK atau B3. Label di luar kelompok tidak
 * dihitung, jadi keyakinan benda yang tidak dikenal rendah dan BR-04 meminta pilihan manual.
 */
class TfliteTrashClassifier private constructor(private val model: LoadedModel) : TrashClassifier {

    override val isPlaceholder: Boolean get() = model.config.placeholder

    override fun classify(frame: Bitmap): TrashResult {
        val probabilities = model.runner.probabilities(frame)
        val mass = ModelMath.groupMass(probabilities, model.runner.labels, model.config.groups)
        val (group, confidence) = ModelMath.best(mass)

        return TrashResult(TrashCategory.valueOf(group), confidence)
    }

    override fun close() = model.close()

    companion object {
        const val ASSET_BASE = "kibooz_trash_classifier_v1"

        fun fromAssets(context: Context, base: String = ASSET_BASE) =
            TfliteTrashClassifier(ModelAssets.load(context, base, TrashCategory.entries.map { it.name }.toSet()))
    }
}
