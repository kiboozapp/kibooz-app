package com.nthb.kibooz.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

interface WaliApi {
    /** Tanpa [studentId] server memakai anak yang paling lama terdaftar. */
    @GET("wali/dashboard")
    suspend fun dashboard(@Query("studentId") studentId: String?): Response<ApiEnvelope<WaliDashboard>>

    /** Memberi tahu guru bahwa panduan sudah dijalankan di rumah. Balasannya tanpa `data`. */
    @POST("wali/guidance/apply")
    suspend fun applyGuidance(
        @Header(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String,
        @Body body: GuidanceApplyRequest
    ): Response<ApiEnvelope<EmptyData>>
}
