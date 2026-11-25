package com.nthb.kibooz.data.repository

import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.GuruProfile
import com.nthb.kibooz.data.remote.GuruProfileApi
import com.nthb.kibooz.data.remote.GuruProfileDetail
import com.nthb.kibooz.data.remote.UpdateGuruProfileRequest
import com.nthb.kibooz.data.remote.safeApiCall
import com.squareup.moshi.Moshi

/** Pengelolaan profil Guru: memuat ringkasan, detail lengkap, dan menyimpan perubahan melalui `PUT /guru/profile`. */
class GuruProfileRepository(private val api: GuruProfileApi, private val moshi: Moshi) {

    suspend fun profile(): ApiResult<GuruProfile> =
        safeApiCall(moshi) { api.profile() }

    suspend fun profileDetail(): ApiResult<GuruProfileDetail> =
        safeApiCall(moshi) { api.profileDetail() }

    suspend fun updateProfile(
        fullName: String? = null,
        phoneNumber: String? = null,
        address: String? = null
    ): ApiResult<GuruProfileDetail> =
        safeApiCall(moshi) {
            api.updateProfile(
                UpdateGuruProfileRequest(
                    fullName = fullName?.trim(),
                    phoneNumber = phoneNumber?.trim(),
                    address = address?.trim()
                )
            )
        }
}
