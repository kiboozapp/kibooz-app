package com.nthb.kibooz.data.remote

data class GuruHistoryEvent(
    val id: String,
    val type: String,
    val classId: String,
    val className: String,
    val studentId: String? = null,
    val studentName: String? = null,
    val actorName: String? = null,
    val moodType: String? = null,
    val confidenceScore: Float? = null,
    val title: String,
    val description: String? = null,
    val occurredAt: String
)

data class GuruHistoryResponse(
    val events: List<GuruHistoryEvent> = emptyList(),
    val page: Int = 1,
    val limit: Int = 20,
    val total: Int = 0
)

data class WaliHistoryStudent(
    val id: String,
    val fullName: String,
    val className: String
)

data class WaliHistoryEvent(
    val id: String,
    val type: String,
    val moodType: String? = null,
    val trashType: String? = null,
    val pointsAwarded: Int? = null,
    val guidanceId: String? = null,
    val guidanceTitle: String? = null,
    val guidanceCategory: String? = null,
    val title: String,
    val description: String? = null,
    val occurredAt: String
)

data class WaliHistoryResponse(
    val student: WaliHistoryStudent,
    val events: List<WaliHistoryEvent> = emptyList(),
    val page: Int = 1,
    val limit: Int = 20,
    val total: Int = 0
)
