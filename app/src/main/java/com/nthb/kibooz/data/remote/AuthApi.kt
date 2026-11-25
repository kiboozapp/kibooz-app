package com.nthb.kibooz.data.remote

import retrofit2.Call
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.PUT

interface AuthApi {
    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequest): Response<ApiEnvelope<AuthData>>

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): Response<ApiEnvelope<AuthData>>

    @POST("auth/verify-email")
    suspend fun verifyEmail(@Body body: VerifyEmailRequest): Response<ApiEnvelope<AuthData>>

    @POST("auth/resend-verification")
    suspend fun resendVerification(@Body body: ResendVerificationRequest): Response<ApiEnvelope<VerificationDto>>

    @POST("auth/google")
    suspend fun googleLogin(@Body body: GoogleLoginRequest): Response<ApiEnvelope<GoogleAuthData>>

    @POST("auth/google/register")
    suspend fun googleRegister(@Body body: GoogleRegisterRequest): Response<ApiEnvelope<AuthData>>

    // refresh dan logout dijalankan dengan execute() di thread pemanggil, bukan lewat antrean dispatcher
    // OkHttp (maksimal 5 request per host). Kalau lewat antrean, 5 request yang sama-sama 401 menahan semua
    // slot sambil menunggu refresh, dan request refresh tidak pernah kebagian slot.
    @POST("auth/refresh-token")
    fun refreshToken(@Body body: RefreshRequest): Call<ApiEnvelope<AuthData>>

    @POST("auth/logout")
    fun logout(@Body body: LogoutRequest): Call<ApiEnvelope<EmptyData>>

    @PUT("users/me/password")
    suspend fun changePassword(@Body body: ChangePasswordRequest): Response<ApiEnvelope<EmptyData>>
}
