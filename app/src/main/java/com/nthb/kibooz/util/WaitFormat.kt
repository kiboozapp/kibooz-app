package com.nthb.kibooz.util

/** Menulis sisa waktu tunggu untuk pengguna, misalnya "45 detik" atau "1 menit 30 detik". */
fun formatWaitSeconds(seconds: Int): String {
    val total = seconds.coerceAtLeast(0)
    val minutes = total / 60
    val rest = total % 60

    return when {
        minutes == 0 -> "$rest detik"
        rest == 0 -> "$minutes menit"
        else -> "$minutes menit $rest detik"
    }
}
