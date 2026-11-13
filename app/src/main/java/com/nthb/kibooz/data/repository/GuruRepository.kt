package com.nthb.kibooz.data.repository

import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.GuruApi
import com.nthb.kibooz.data.remote.GuruDashboard
import com.nthb.kibooz.data.remote.safeApiCall
import com.squareup.moshi.Moshi

/** Ringkasan kelas untuk dasbor Guru. */
class GuruRepository(private val api: GuruApi, private val moshi: Moshi) {

    /** Ringkasan hari ini. [classId] kosong memakai kelas bawaan Guru, isi untuk Guru yang mengajar beberapa kelas. */
    suspend fun dashboard(classId: String? = null): ApiResult<GuruDashboard> =
        safeApiCall(moshi) { api.dashboard(classId) }
}
