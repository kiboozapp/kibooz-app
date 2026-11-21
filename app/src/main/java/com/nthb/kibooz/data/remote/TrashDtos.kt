package com.nthb.kibooz.data.remote

import com.nthb.kibooz.ml.TrashCategory

/**
 * Klaim poin sampah. [confidenceScore] sengaja tidak boleh null, karena server memakai 0 jika kosong
 * (berbeda dengan mood log). `photoBase64` sengaja tidak ada: aplikasi tidak pernah mengirim foto.
 */
data class ScanClaimRequest(
    val studentId: String,
    val trashType: TrashCategory,
    val confidenceScore: Float
)

data class ScanClaimData(
    val pointsAdded: Int,
    val totalPoints: Int,
    val seasonPoints: Int,
    val newRank: Int,
    val remainingDailyScans: Int
)

/** `data` dari `GET /trash/stats`: pindaian dan poin seorang anak per kategori, plus sisa pindaian hari ini. */
data class TrashStats(
    val student: TrashStatsStudent,
    val totalPoints: Int,
    val seasonPoints: Int,
    val classRank: Int,
    val totalScans: Int,
    val breakdown: List<TrashBreakdown>,
    val todayScans: Int,
    val dailyLimit: Int,
    val remainingDailyScans: Int
) {
    /** Jumlah pindaian satu kategori, 0 jika kategorinya tidak ada di balasan. */
    fun scansOf(category: TrashCategory): Int = breakdown.filter { it.trashType == category }.sumOf { it.scans }

    /** Bagian satu kategori dari semua pindaian, 0 sampai 100, dibulatkan. 0 jika belum ada pindaian. */
    fun percentOf(category: TrashCategory): Int =
        if (totalScans <= 0) 0 else Math.round(scansOf(category) * 100f / totalScans)
}

data class TrashStatsStudent(val id: String, val fullName: String)

data class TrashBreakdown(val trashType: TrashCategory, val scans: Int, val points: Int)
