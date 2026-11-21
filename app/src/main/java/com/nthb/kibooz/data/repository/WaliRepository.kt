package com.nthb.kibooz.data.repository

import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.WaliApi
import com.nthb.kibooz.data.remote.WaliDashboard
import com.nthb.kibooz.data.remote.safeApiCall
import com.squareup.moshi.Moshi

/** Dasbor Wali. Semua angka dan pilihan panduan dihitung server, aplikasi hanya menampilkan. */
class WaliRepository(private val api: WaliApi, private val moshi: Moshi) {

    suspend fun dashboard(studentId: String? = null): ApiResult<WaliDashboard> =
        safeApiCall(moshi) { api.dashboard(studentId) }
}
