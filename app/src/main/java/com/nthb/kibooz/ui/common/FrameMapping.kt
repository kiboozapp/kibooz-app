package com.nthb.kibooz.ui.common

import kotlin.math.max

/** Kotak dalam piksel tampilan. */
data class ViewBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun contains(x: Float, y: Float) = x in left..right && y in top..bottom
}

/**
 * Memetakan koordinat frame kamera ke koordinat tampilan untuk PreviewView ber-scaleType FILL_CENTER:
 * frame diskalakan sampai menutupi seluruh tampilan, lalu bagian yang berlebih terpotong rata di tengah.
 *
 * Frame harus sudah tegak, yaitu ukuran dan kotak dari MoodAnalysisAnalyzer.
 */
class FrameMapping(frameWidth: Int, frameHeight: Int, viewWidth: Int, viewHeight: Int) {

    private val scale: Float
    private val offsetX: Float
    private val offsetY: Float

    init {
        require(frameWidth > 0 && frameHeight > 0 && viewWidth > 0 && viewHeight > 0) {
            "Ukuran frame dan tampilan harus positif"
        }
        scale = max(viewWidth.toFloat() / frameWidth, viewHeight.toFloat() / frameHeight)
        offsetX = (viewWidth - frameWidth * scale) / 2f
        offsetY = (viewHeight - frameHeight * scale) / 2f
    }

    fun map(left: Int, top: Int, right: Int, bottom: Int) = ViewBox(
        left * scale + offsetX,
        top * scale + offsetY,
        right * scale + offsetX,
        bottom * scale + offsetY
    )
}
