package com.nthb.kibooz.data.remote

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface NotificationApi {
    @GET("notifications")
    suspend fun listNotifications(
        @Query("unreadOnly") unreadOnly: Boolean = false,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 30
    ): Response<ApiEnvelope<NotificationList>>

    @POST("notifications/read-all")
    suspend fun markAllRead(): Response<ApiEnvelope<EmptyData>>

    @POST("notifications/{notificationId}/read")
    suspend fun markRead(@Path("notificationId") notificationId: String): Response<ApiEnvelope<EmptyData>>
}
