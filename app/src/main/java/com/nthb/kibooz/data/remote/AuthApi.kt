package com.nthb.kibooz.data.remote

import retrofit2.Call
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {
    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): Response<ApiEnvelope<AuthData>>

    // refresh dan logout dijalankan dengan execute() di thread pemanggil, bukan lewat antrean dispatcher
    // OkHttp (maksimal 5 request per host). Kalau lewat antrean, 5 request yang sama-sama 401 menahan semua
    // slot sambil menunggu refresh, dan request refresh tidak pernah kebagian slot.
    @POST("auth/refresh-token")
    fun refreshToken(@Body body: RefreshRequest): Call<ApiEnvelope<AuthData>>

    @POST("auth/logout")
    fun logout(@Body body: LogoutRequest): Call<ApiEnvelope<EmptyData>>
}
