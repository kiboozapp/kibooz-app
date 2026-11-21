package com.nthb.kibooz.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

interface TrashApi {
    @POST("trash/scan-claim")
    suspend fun scanClaim(
        @Header(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String,
        @Body body: ScanClaimRequest
    ): Response<ApiEnvelope<ScanClaimData>>

    /** Tanpa [studentId] server memakai anak yang paling lama terdaftar. */
    @GET("trash/stats")
    suspend fun stats(@Query("studentId") studentId: String?): Response<ApiEnvelope<TrashStats>>
}
