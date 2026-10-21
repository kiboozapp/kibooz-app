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
