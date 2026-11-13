package com.nthb.kibooz.data.remote

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface GuruApi {
    @GET("guru/dashboard")
    suspend fun dashboard(@Query("classId") classId: String?): Response<ApiEnvelope<GuruDashboard>>
}
