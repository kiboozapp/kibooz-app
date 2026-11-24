package com.nthb.kibooz.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ForumApi {
    @GET("classes/{classId}/forum")
    suspend fun listThreads(
        @Path("classId") classId: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): Response<ApiEnvelope<ForumThreadList>>

    @POST("classes/{classId}/forum")
    suspend fun createThread(
        @Path("classId") classId: String,
        @Header(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String,
        @Body body: CreateForumThreadRequest
    ): Response<ApiEnvelope<ForumThreadItem>>

    @GET("classes/{classId}/forum/{postId}/replies")
    suspend fun listReplies(
        @Path("classId") classId: String,
        @Path("postId") postId: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): Response<ApiEnvelope<ForumReplyList>>

    @POST("classes/{classId}/forum/{postId}/replies")
    suspend fun createReply(
        @Path("classId") classId: String,
        @Path("postId") postId: String,
        @Header(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String,
        @Body body: CreateForumReplyRequest
    ): Response<ApiEnvelope<ForumReplyItem>>
}
