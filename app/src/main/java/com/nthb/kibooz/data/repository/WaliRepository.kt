package com.nthb.kibooz.data.repository

import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.GuidanceApplyRequest
import com.nthb.kibooz.data.remote.MoveRequest
import com.nthb.kibooz.data.remote.MoveRequestInput
import com.nthb.kibooz.data.remote.WaliApi
import com.nthb.kibooz.data.remote.WaliDashboard
import com.nthb.kibooz.data.remote.newIdempotencyKey
import com.nthb.kibooz.data.remote.safeApiCall
import com.nthb.kibooz.data.remote.safeApiCallEmpty
import com.squareup.moshi.Moshi

/**
 * Satu aksi "terapkan panduan". Kirim objek yang sama lagi saat mencoba ulang supaya [idempotencyKey] tetap
 * sama dan laporan tidak masuk dua kali ke guru.
 */
class PendingGuidanceApply(
    val guidanceId: String,
    val studentId: String,
    val parentNotes: String?,
    val idempotencyKey: String = newIdempotencyKey()
)

/** Dasbor Wali. Semua angka dan pilihan panduan dihitung server, aplikasi hanya menampilkan. */
class WaliRepository(private val api: WaliApi, private val moshi: Moshi) {

    suspend fun dashboard(studentId: String? = null): ApiResult<WaliDashboard> =
        safeApiCall(moshi) { api.dashboard(studentId) }

    suspend fun applyGuidance(apply: PendingGuidanceApply): ApiResult<Unit> {
        val request = GuidanceApplyRequest(apply.guidanceId, apply.studentId, apply.parentNotes?.trim()?.takeIf { it.isNotEmpty() })

        return safeApiCallEmpty(moshi) { api.applyGuidance(apply.idempotencyKey, request) }
    }

    suspend fun requestMove(
        studentId: String,
        classCode: String,
        idempotencyKey: String = newIdempotencyKey()
    ): ApiResult<MoveRequest> =
        safeApiCall(moshi) { api.requestMove(studentId, idempotencyKey, MoveRequestInput(classCode)) }
}
