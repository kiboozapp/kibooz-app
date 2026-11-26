package com.nthb.kibooz.ui.guru

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import com.nthb.kibooz.R
import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.ui.auth.RoleSelectionActivity
import com.nthb.kibooz.util.formatWaitSeconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Alasan layar-layar sebuah kelas harus ditutup. */
enum class ClassScreenEnd {
    /** Guru keluar dari kelas atas kemauannya sendiri. */
    LEFT,

    /** Kelas tidak ada lagi atau Guru sudah tidak mengajar di sana. */
    LOST,

    /** Akunnya sudah tidak ada di server. */
    ACCOUNT_GONE
}

/** Akhir layar yang diminta satu `errorCode` dari rute kelas Guru, atau null jika layar boleh tetap terbuka. */
internal fun classScreenEndFor(errorCode: String?): ClassScreenEnd? = when (errorCode) {
    "CLASS_NOT_FOUND" -> ClassScreenEnd.LOST
    "PROFILE_NOT_FOUND" -> ClassScreenEnd.ACCOUNT_GONE
    else -> null
}

private const val DEFAULT_COOLDOWN_SECONDS = 30L
private const val MAX_COOLDOWN_SECONDS = 3_600L

/** 429 dari API, atau dari proxy yang tidak mengirim amplop JSON (jadi tanpa `errorCode`). */
internal fun ApiResult.Failure.isRateLimited(): Boolean =
    errorCode == "RATE_LIMITED" || (errorCode == null && status == 429)

/** Detik yang harus ditunggu setelah 429, mengikuti `Retry-After`. */
internal fun ApiResult.Failure.cooldownSeconds(): Long =
    (retryAfterSeconds ?: DEFAULT_COOLDOWN_SECONDS).coerceIn(1L, MAX_COOLDOWN_SECONDS)

/** Pesan 429 yang menyebut lama tunggunya. */
internal fun rateLimitNotice(seconds: Long) =
    KelolaNotice(R.string.pengajar_batas_percobaan, listOf(formatWaitSeconds(seconds.toInt())))

/**
 * Hitung mundur setelah 429. Server ikut menghitung percobaan yang ditolak, jadi tombol aksi dikunci sampai
 * waktunya habis. [onTick] menerima sisa detik, lalu null saat selesai.
 */
internal class RateLimitCooldown(
    private val scope: CoroutineScope,
    private val pause: suspend (millis: Long) -> Unit,
    private val onTick: (secondsLeft: Int?) -> Unit
) {
    private var job: Job? = null

    fun start(seconds: Long) {
        job?.cancel()
        job = scope.launch {
            for (left in seconds.toInt() downTo 1) {
                onTick(left)
                pause(1_000)
            }
            onTick(null)
        }
    }
}

/**
 * Menutup semua layar sebuah kelas dan kembali ke daftar kelas, yang memuat ulang daftarnya saat tampil. Pesannya
 * memakai Toast milik aplikasi supaya tetap tampil setelah layar ditutup.
 */
internal fun Activity.closeClassScreens(message: String) {
    Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
    startActivity(
        Intent(this, RuangKelasGuruActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    )
    finish()
}

/** Akunnya sudah hilang di server, jadi tumpukan layar dibersihkan dan pengguna kembali ke pilihan peran. */
internal fun Activity.openRoleSelectionForGoneAccount() {
    Toast.makeText(applicationContext, R.string.pengajar_akun_sudah_tidak_ada, Toast.LENGTH_LONG).show()
    startActivity(
        Intent(this, RoleSelectionActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
    )
    finish()
}
