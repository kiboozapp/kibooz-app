package com.nthb.kibooz.data.remote

data class ModuleItem(
    val id: String,
    val meetingNumber: Int,
    val title: String,
    val description: String? = null,
    val status: String = "upcoming",
    val scheduledAt: String? = null,
    val createdAt: String = "",
    val updatedAt: String = ""
)

data class ModuleList(
    val modules: List<ModuleItem> = emptyList(),
    val page: Int = 1,
    val limit: Int = 20,
    val total: Int = 0
)

data class CreateModuleRequest(
    val meetingNumber: Int,
    val title: String,
    val description: String? = null,
    val status: String = "upcoming",
    val scheduledAt: String? = null
)

data class UpdateModuleRequest(
    val meetingNumber: Int? = null,
    val title: String? = null,
    val description: String? = null,
    val status: String? = null,
    val scheduledAt: String? = null
)
