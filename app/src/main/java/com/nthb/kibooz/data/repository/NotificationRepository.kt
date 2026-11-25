package com.nthb.kibooz.data.repository

import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.NotificationApi
import com.nthb.kibooz.data.remote.NotificationList
import com.nthb.kibooz.data.remote.safeApiCall
import com.nthb.kibooz.data.remote.safeApiCallEmpty
import com.squareup.moshi.Moshi

class NotificationRepository(private val api: NotificationApi, private val moshi: Moshi) {

    suspend fun notifications(
        unreadOnly: Boolean = false,
        page: Int = 1,
        limit: Int = 30
    ): ApiResult<NotificationList> = safeApiCall(moshi) { api.listNotifications(unreadOnly, page, limit) }

    suspend fun markRead(notificationId: String): ApiResult<Unit> =
        safeApiCallEmpty(moshi) { api.markRead(notificationId) }

    suspend fun markAllRead(): ApiResult<Unit> =
        safeApiCallEmpty(moshi) { api.markAllRead() }
}
