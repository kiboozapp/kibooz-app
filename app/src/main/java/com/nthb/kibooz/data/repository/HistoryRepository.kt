package com.nthb.kibooz.data.repository

import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.GuruHistoryResponse
import com.nthb.kibooz.data.remote.HistoryApi
import com.nthb.kibooz.data.remote.WaliHistoryResponse
import com.nthb.kibooz.data.remote.safeApiCall
import com.squareup.moshi.Moshi

class HistoryRepository(private val api: HistoryApi, private val moshi: Moshi) {

    suspend fun guruHistory(
        classId: String? = null,
        type: String = "all",
        page: Int = 1,
        limit: Int = 30
    ): ApiResult<GuruHistoryResponse> = safeApiCall(moshi) { api.guruHistory(classId, type, page, limit) }

    suspend fun waliChildHistory(
        studentId: String,
        type: String = "all",
        page: Int = 1,
        limit: Int = 30
    ): ApiResult<WaliHistoryResponse> = safeApiCall(moshi) { api.waliChildHistory(studentId, type, page, limit) }
}
