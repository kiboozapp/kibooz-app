package com.nthb.kibooz.data.repository

import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.ScanClaimData
import com.nthb.kibooz.data.remote.ScanClaimRequest
import com.nthb.kibooz.data.remote.TrashApi
import com.nthb.kibooz.data.remote.newIdempotencyKey
import com.nthb.kibooz.data.remote.safeApiCall
import com.nthb.kibooz.ml.TrashCategory
import com.squareup.moshi.Moshi

/**
 * Satu aksi klaim poin sampah. Kirim objek yang sama lagi saat mencoba ulang supaya [idempotencyKey]
 * tetap sama dan klaim tidak terhitung dua kali. Pilihan manual di bawah 75% membuat objek baru.
 */
class PendingTrashClaim(
    val studentId: String,
    val category: TrashCategory,
    val confidence: Float,
    val idempotencyKey: String = newIdempotencyKey()
)

class TrashRepository(private val api: TrashApi, private val moshi: Moshi) {

    /**
     * Klaim tanpa foto. `TRASH_DAILY_LIMIT_REACHED` (429) tidak punya `Retry-After`: batas direset
     * tengah malam, jadi jangan dicoba ulang otomatis.
     */
    suspend fun claim(claim: PendingTrashClaim): ApiResult<ScanClaimData> {
        val request = ScanClaimRequest(claim.studentId, claim.category, claim.confidence)

        return safeApiCall(moshi) { api.scanClaim(claim.idempotencyKey, request) }
    }
}
