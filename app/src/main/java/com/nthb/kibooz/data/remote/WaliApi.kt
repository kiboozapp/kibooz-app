package com.nthb.kibooz.data.remote

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface WaliApi {
    /** Tanpa [studentId] server memakai anak yang paling lama terdaftar. */
    @GET("wali/dashboard")
    suspend fun dashboard(@Query("studentId") studentId: String?): Response<ApiEnvelope<WaliDashboard>>
}
