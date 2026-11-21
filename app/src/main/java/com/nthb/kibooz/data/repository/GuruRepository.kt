package com.nthb.kibooz.data.repository

import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.GuruApi
import com.nthb.kibooz.data.remote.GuruDashboard
import com.nthb.kibooz.data.remote.MoodAnalytics
import com.nthb.kibooz.data.remote.safeApiCall
import com.squareup.moshi.Moshi

/** Ringkasan kelas dan analitik mood untuk Guru. Semua angka dihitung server, aplikasi hanya menampilkan. */
class GuruRepository(private val api: GuruApi, private val moshi: Moshi) {

    suspend fun dashboard(classId: String? = null): ApiResult<GuruDashboard> =
        safeApiCall(moshi) { api.dashboard(classId) }

    suspend fun analytics(range: AnalyticsRange, classId: String? = null): ApiResult<MoodAnalytics> =
        safeApiCall(moshi) { api.moodAnalytics(classId, range.query) }
}

enum class AnalyticsRange(val query: String) { WEEKLY("weekly"), MONTHLY("monthly") }
