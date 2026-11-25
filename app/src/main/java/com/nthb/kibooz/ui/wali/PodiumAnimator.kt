package com.nthb.kibooz.ui.wali

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.TextView

/**
 * Animasi masuk podium peringkat: kolom juara 3, 2, lalu 1 naik bergantian dari balik bottom sheet, avatar membesar,
 * poin menghitung naik, dan mahkota jatuh ke atas juara 1.
 *
 * Animator Android mengikuti setelan "Skala durasi animator" di perangkat. Jika animasi dimatikan, semuanya langsung
 * lompat ke tampilan akhir dan [play] tetap memanggil `onFinished`.
 */
class PodiumAnimator(
    /** Urutan juara 1, 2, 3. Slot yang kolomnya GONE (kelas dengan kurang dari tiga anak) dilewati. */
    private val slots: List<Slot>,
    private val crown: View
) {

    class Slot(val column: View, val avatar: View, val points: TextView)

    private var running: AnimatorSet? = null

    /**
     * Memutar animasi dari awal. [points] berisi poin juara 1, 2, 3 sesuai urutan [slots], dan [formatPoints] menulis
     * teks poin tiap frame. [onFinished] dipanggil sekali saat animasi selesai wajar, tidak saat dibatalkan.
     */
    fun play(points: List<Int>, formatPoints: (Int) -> String, onFinished: () -> Unit) {
        cancel()

        // Kolom naik mulai dari juara 3, supaya juara 1 muncul paling akhir sebagai puncak
        val order = listOf(2, 1, 0).filter { it < points.size && slots[it].column.visibility == View.VISIBLE }
        val animators = mutableListOf<Animator>()

        order.forEachIndexed { step, rank ->
            val slot = slots[rank]
            val delay = step * STAGGER_MILLIS
            val rise = slot.column.height.takeIf { it > 0 }?.toFloat() ?: FALLBACK_RISE_PX

            // Nilai awal dipasang langsung, karena animator yang masih menunggu startDelay belum menerapkannya
            slot.column.translationY = rise
            slot.column.alpha = 0f
            slot.avatar.scaleX = AVATAR_START_SCALE
            slot.avatar.scaleY = AVATAR_START_SCALE
            slot.points.text = formatPoints(0)

            animators += ObjectAnimator.ofFloat(slot.column, View.TRANSLATION_Y, rise, 0f).apply {
                startDelay = delay
                duration = RISE_MILLIS
                interpolator = OvershootInterpolator(RISE_OVERSHOOT)
            }
            animators += ObjectAnimator.ofFloat(slot.column, View.ALPHA, 0f, 1f).apply {
                startDelay = delay
                duration = RISE_MILLIS / 2
            }
            animators += avatarPop(slot.avatar, delay + RISE_MILLIS * 2 / 3)
            animators += ValueAnimator.ofInt(0, points[rank]).apply {
                startDelay = delay
                duration = RISE_MILLIS + COUNT_EXTRA_MILLIS
                interpolator = DecelerateInterpolator()
                addUpdateListener { slot.points.text = formatPoints(it.animatedValue as Int) }
            }
        }

        if (crown.visibility == View.VISIBLE && 0 in order) {
            animators += crownDrop(order.indexOf(0) * STAGGER_MILLIS + RISE_MILLIS)
        }

        if (animators.isEmpty()) {
            onFinished()
            return
        }

        running = AnimatorSet().apply {
            playTogether(animators)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    running = null
                    onFinished()
                }
            })
            start()
        }
    }

    /**
     * Menghentikan animasi dan memastikan semua tampilan di posisi akhirnya. `end()` melompatkan setiap animator ke
     * nilai akhir, dan listener dilepas dulu supaya `onFinished` (misalnya confetti) tidak ikut terpanggil.
     */
    fun cancel() {
        running?.let {
            it.removeAllListeners()
            it.end()
        }
        running = null
    }

    private fun avatarPop(avatar: View, delay: Long): Animator = AnimatorSet().apply {
        playTogether(
            ObjectAnimator.ofFloat(avatar, View.SCALE_X, AVATAR_START_SCALE, 1f),
            ObjectAnimator.ofFloat(avatar, View.SCALE_Y, AVATAR_START_SCALE, 1f)
        )
        startDelay = delay
        duration = POP_MILLIS
        interpolator = OvershootInterpolator(POP_OVERSHOOT)
    }

    private fun crownDrop(delay: Long): Animator {
        val drop = crown.resources.displayMetrics.density * CROWN_DROP_DP
        crown.translationY = -drop
        crown.alpha = 0f
        crown.rotation = CROWN_START_ROTATION
        return AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(crown, View.TRANSLATION_Y, -drop, 0f),
                ObjectAnimator.ofFloat(crown, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(crown, View.ROTATION, CROWN_START_ROTATION, 0f)
            )
            startDelay = delay
            duration = CROWN_MILLIS
            interpolator = OvershootInterpolator(POP_OVERSHOOT)
        }
    }

    private companion object {
        const val STAGGER_MILLIS = 150L
        const val RISE_MILLIS = 550L
        const val RISE_OVERSHOOT = 0.8f
        const val COUNT_EXTRA_MILLIS = 250L
        const val POP_MILLIS = 350L
        const val POP_OVERSHOOT = 2.5f
        const val AVATAR_START_SCALE = 0.6f
        const val CROWN_MILLIS = 450L
        const val CROWN_DROP_DP = 40f
        const val CROWN_START_ROTATION = -15f

        /** Jarak naik jika kolom belum pernah diukur, misalnya baru berubah dari GONE ke VISIBLE. */
        const val FALLBACK_RISE_PX = 600f
    }
}
