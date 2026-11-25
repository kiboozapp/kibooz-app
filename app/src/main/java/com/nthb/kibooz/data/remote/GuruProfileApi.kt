package com.nthb.kibooz.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT

/** Profil akun Guru (baca ringkas, detail kontak, dan pembaruan nama/telepon/alamat). */
interface GuruProfileApi {
    @GET("guru/profile")
    suspend fun profile(): Response<ApiEnvelope<GuruProfile>>

    @GET("guru/profile/detail")
    suspend fun profileDetail(): Response<ApiEnvelope<GuruProfileDetail>>

    @PUT("guru/profile")
    suspend fun updateProfile(@Body body: UpdateGuruProfileRequest): Response<ApiEnvelope<GuruProfileDetail>>
}
