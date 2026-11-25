package com.nthb.kibooz.data.repository

import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.CreateModuleRequest
import com.nthb.kibooz.data.remote.ModuleApi
import com.nthb.kibooz.data.remote.ModuleItem
import com.nthb.kibooz.data.remote.ModuleList
import com.nthb.kibooz.data.remote.UpdateModuleRequest
import com.nthb.kibooz.data.remote.newIdempotencyKey
import com.nthb.kibooz.data.remote.safeApiCall
import com.nthb.kibooz.data.remote.safeApiCallEmpty
import com.squareup.moshi.Moshi

class ModuleRepository(private val api: ModuleApi, private val moshi: Moshi) {

    suspend fun modules(classId: String, page: Int = 1, limit: Int = 50): ApiResult<ModuleList> =
        safeApiCall(moshi) { api.listModules(classId, page, limit) }

    suspend fun createModule(
        classId: String,
        meetingNumber: Int,
        title: String,
        description: String? = null,
        status: String = "upcoming",
        scheduledAt: String? = null
    ): ApiResult<ModuleItem> = safeApiCall(moshi) {
        api.createModule(
            classId = classId,
            idempotencyKey = newIdempotencyKey(),
            body = CreateModuleRequest(
                meetingNumber = meetingNumber,
                title = title.trim(),
                description = description?.trim()?.takeIf { it.isNotEmpty() },
                status = status,
                scheduledAt = scheduledAt?.trim()?.takeIf { it.isNotEmpty() }
            )
        )
    }

    suspend fun updateModule(
        classId: String,
        moduleId: String,
        meetingNumber: Int? = null,
        title: String? = null,
        description: String? = null,
        status: String? = null,
        scheduledAt: String? = null
    ): ApiResult<ModuleItem> = safeApiCall(moshi) {
        api.updateModule(
            classId = classId,
            moduleId = moduleId,
            body = UpdateModuleRequest(
                meetingNumber = meetingNumber,
                title = title?.trim()?.takeIf { it.isNotEmpty() },
                description = description?.trim(),
                status = status,
                scheduledAt = scheduledAt?.trim()?.takeIf { it.isNotEmpty() }
            )
        )
    }

    suspend fun deleteModule(classId: String, moduleId: String): ApiResult<Unit> =
        safeApiCallEmpty(moshi) { api.deleteModule(classId, moduleId) }
}
