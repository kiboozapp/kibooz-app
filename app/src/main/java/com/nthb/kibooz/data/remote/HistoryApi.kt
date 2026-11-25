package com.nthb.kibooz.data.remote

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface HistoryApi {
    @GET("guru/history")
    suspend fun guruHistory(
        @Query("classId") classId: String? = null,
        @Query("type") type: String = "all",
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 30
    ): Response<ApiEnvelope<GuruHistoryResponse>>

    @GET("wali/children/{studentId}/history")
    suspend fun waliChildHistory(
        @Path("studentId") studentId: String,
        @Query("type") type: String = "all",
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 30
    ): Response<ApiEnvelope<WaliHistoryResponse>>
}
