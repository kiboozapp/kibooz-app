package com.nthb.kibooz.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface VideoApi {
    @GET("classes/{classId}/videos")
    suspend fun listVideos(
        @Path("classId") classId: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): Response<ApiEnvelope<VideoList>>

    @POST("classes/{classId}/videos")
    suspend fun createVideo(
        @Path("classId") classId: String,
        @Header(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String,
        @Body body: CreateVideoRequest
    ): Response<ApiEnvelope<VideoItem>>

    @PATCH("classes/{classId}/videos/{videoId}")
    suspend fun updateVideo(
        @Path("classId") classId: String,
        @Path("videoId") videoId: String,
        @Body body: UpdateVideoRequest
    ): Response<ApiEnvelope<VideoItem>>

    @DELETE("classes/{classId}/videos/{videoId}")
    suspend fun deleteVideo(
        @Path("classId") classId: String,
        @Path("videoId") videoId: String
    ): Response<ApiEnvelope<EmptyData>>
}
