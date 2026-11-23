package com.nthb.kibooz.ui.common

import android.animation.ObjectAnimator
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator

/**
 * Kedipan pelan pada tampilan pengganti selagi data dimuat (FE-11). Panggil [start] saat memuat dan [stop]
 * begitu data tiba atau layar ditutup, supaya tampilan kembali utuh dan animasi tidak menahan layar.
 */
class PulseAnimator(private val views: List<View>) {

    private var animators: List<ObjectAnimator> = emptyList()

    /** Aman dipanggil berulang, kedipan yang sedang berjalan tidak dimulai ulang. */
    fun start() {
        if (animators.isNotEmpty()) return

        animators = views.map { view ->
            ObjectAnimator.ofFloat(view, View.ALPHA, 1f, MIN_ALPHA).apply {
                duration = DURATION_MILLIS
                repeatMode = ObjectAnimator.REVERSE
                repeatCount = ObjectAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
                start()
            }
        }
    }

    fun stop() {
        animators.forEach { it.cancel() }
        animators = emptyList()
        views.forEach { it.alpha = 1f }
    }

    private companion object {
        const val MIN_ALPHA = 0.35f
        const val DURATION_MILLIS = 800L
    }
}
