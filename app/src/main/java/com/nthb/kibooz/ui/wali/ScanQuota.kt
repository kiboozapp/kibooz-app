package com.nthb.kibooz.ui.wali

import androidx.annotation.StringRes
import com.nthb.kibooz.R
import com.nthb.kibooz.data.remote.TrashStats

/** Kuota scan sampah seorang anak untuk hari ini, ditampilkan sebagai ajakan di dasbor dan statistik Wali. */
data class ScanQuota(val remaining: Int, val dailyLimit: Int) {

    /** Ajakan scan selama kuota masih ada, atau pesan habis saat sudah 0. Keduanya diformat dengan [remaining], [dailyLimit]. */
    @get:StringRes
    val messageRes: Int
        get() = if (remaining > 0) R.string.sampah_sisa_scan_ajakan else R.string.sampah_sisa_scan_habis
}

fun TrashStats.scanQuota() = ScanQuota(remainingDailyScans, dailyLimit)
