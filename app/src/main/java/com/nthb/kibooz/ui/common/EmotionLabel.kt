package com.nthb.kibooz.ui.common

import com.nthb.kibooz.ml.Emotion
import com.nthb.kibooz.ml.EmotionResult
import kotlin.math.roundToInt

fun Emotion.displayName(): String = when (this) {
    Emotion.SENANG -> "Senang"
    Emotion.SEDIH -> "Sedih"
    Emotion.MARAH -> "Marah"
    Emotion.BINGUNG -> "Bingung"
}

fun Emotion.emoji(): String = when (this) {
    Emotion.SENANG -> "😊"
    Emotion.SEDIH -> "😢"
    Emotion.MARAH -> "😠"
    Emotion.BINGUNG -> "😕"
}

/** Teks lencana emosi, misalnya "😊 Senang (92%)". Emosi selalu ditulis, tidak hanya diwarnai. */
fun EmotionResult.badgeText(): String = "${emotion.emoji()} ${emotion.displayName()} (${(confidence * 100).roundToInt()}%)"
