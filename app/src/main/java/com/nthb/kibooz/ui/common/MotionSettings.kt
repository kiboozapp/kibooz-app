package com.nthb.kibooz.ui.common

import android.animation.ValueAnimator
import android.content.Context
import android.os.Build
import android.provider.Settings

/**
 * False jika pengguna mematikan animasi (Opsi developer "Skala durasi animator" = mati, atau Aksesibilitas
 * "Hapus animasi"). Animator Android sudah mengikuti setelan ini sendiri, tetapi efek dengan timer sendiri seperti
 * confetti Konfetti tidak, jadi efek semacam itu wajib memeriksanya dulu.
 */
fun Context.animationsEnabled(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        ValueAnimator.areAnimatorsEnabled()
    } else {
        Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
