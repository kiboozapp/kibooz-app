package com.nthb.kibooz.ui.auth

import android.os.SystemClock

/**
 * Hitung mundur tombol kirim ulang kode. Yang disimpan adalah waktu tenggatnya, bukan sisa detik, jadi hitungan
 * tetap benar setelah layar diputar dan setelah aplikasi lama di latar belakang. Jam bawaannya SystemClock.elapsedRealtime,
 * yang tetap jalan saat perangkat tidur. Pengujian memberi jam sendiri supaya bisa memajukan waktu.
 */
class ResendCooldown(private val nowMillis: () -> Long = { SystemClock.elapsedRealtime() }) {

    private var deadlineMillis = 0L
    private var running = false

    /** Mulai menghitung [seconds] detik dari sekarang. Nol atau negatif berarti boleh kirim ulang sekarang. */
    fun start(seconds: Long) {
        val wait = seconds.coerceIn(0, MAX_SECONDS)
        running = wait > 0
        deadlineMillis = nowMillis() + wait * MILLIS_PER_SECOND
    }

    fun clear() {
        running = false
    }

    /** Sisa detik dibulatkan ke atas, jadi 0 hanya saat tenggatnya sudah lewat. */
    fun secondsLeft(): Int {
        if (!running) return 0

        val left = deadlineMillis - nowMillis()
        return if (left <= 0) 0 else ((left + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND).toInt()
    }

    companion object {
        /** Batas kirim ulang per jam di server, jadi tunggu yang lebih lama dari ini tidak masuk akal. */
        const val MAX_SECONDS = 3_600L

        private const val MILLIS_PER_SECOND = 1_000L
    }
}
