package com.nthb.kibooz.data.remote

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/** Rute `/chat` lewat REST. Frame socket ada di [ChatSocket]. */
interface ChatApi {
    @GET("chat/contacts")
    suspend fun listContacts(
        @Query("page") page: Int,
        @Query("limit") limit: Int
    ): Response<ApiEnvelope<ChatContactList>>

    @GET("chat/conversations")
    suspend fun listConversations(
        @Query("page") page: Int,
        @Query("limit") limit: Int
    ): Response<ApiEnvelope<ChatConversationList>>

    /** Membuka percakapan langsung (`userId`) atau ruang kelas (`classId`). Balasannya 201 saat baru dibuat dan 200 jika sudah ada. */
    @POST("chat/conversations")
    suspend fun startConversation(@Body body: StartChatRequest): Response<ApiEnvelope<ChatConversation>>

    /** Tanpa [after] dan [before] server memberi pesan terbaru. Kirim salah satu, tidak keduanya. Query yang null tidak dikirim. */
    @GET("chat/conversations/{conversationId}/messages")
    suspend fun listMessages(
        @Path("conversationId") conversationId: String,
        @Query("after") after: Long?,
        @Query("before") before: Long?,
        @Query("limit") limit: Int?
    ): Response<ApiEnvelope<ChatMessageList>>

    @POST("chat/conversations/{conversationId}/messages")
    suspend fun sendMessage(
        @Path("conversationId") conversationId: String,
        @Header(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String,
        @Body body: SendChatMessageRequest
    ): Response<ApiEnvelope<ChatMessage>>

    @Multipart
    @POST("chat/conversations/{conversationId}/media/photo")
    suspend fun uploadPhoto(
        @Path("conversationId") conversationId: String,
        @Part file: MultipartBody.Part
    ): Response<ApiEnvelope<ChatPhotoUpload>>

    /** Hanya meminta alamat unggah. Berkas videonya dikirim langsung ke storage lewat [ChatStorage]. */
    @POST("chat/conversations/{conversationId}/media/video-upload")
    suspend fun createVideoUpload(
        @Path("conversationId") conversationId: String,
        @Body body: ChatVideoUploadRequest
    ): Response<ApiEnvelope<ChatVideoUpload>>

    @DELETE("chat/conversations/{conversationId}/messages/{messageId}")
    suspend fun deleteMessage(
        @Path("conversationId") conversationId: String,
        @Path("messageId") messageId: Long
    ): Response<ApiEnvelope<ChatMessage>>

    @PUT("chat/conversations/{conversationId}/read")
    suspend fun markRead(
        @Path("conversationId") conversationId: String,
        @Body body: MarkChatReadRequest
    ): Response<ApiEnvelope<ChatReadState>>
}
