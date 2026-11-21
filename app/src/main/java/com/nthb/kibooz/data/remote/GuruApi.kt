package com.nthb.kibooz.data.remote

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface GuruApi {
    /** Tanpa [classId] server memakai kelas pertama Guru. */
    @GET("guru/dashboard")
    suspend fun dashboard(@Query("classId") classId: String?): Response<ApiEnvelope<GuruDashboard>>

    /** [range] bernilai `weekly` atau `monthly`. `monthlyDistribution` hanya ada untuk `monthly`. */
    @GET("guru/mood/analytics")
    suspend fun moodAnalytics(
        @Query("classId") classId: String?,
        @Query("range") range: String
    ): Response<ApiEnvelope<MoodAnalytics>>
}
