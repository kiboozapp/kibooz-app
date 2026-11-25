package com.nthb.kibooz.data.remote

data class NotificationItem(
    val id: String,
    val kind: String,
    val title: String,
    val body: String,
    val referenceType: String? = null,
    val referenceId: String? = null,
    val readAt: String? = null,
    val createdAt: String = ""
)

data class NotificationList(
    val notifications: List<NotificationItem> = emptyList(),
    val unreadCount: Int = 0,
    val page: Int = 1,
    val limit: Int = 20,
    val total: Int = 0
)
