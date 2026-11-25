package com.nthb.kibooz.data.remote

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.HTTP
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface AccountApi {
    // DELETE membawa body JSON (kata sandi), sedangkan @DELETE Retrofit tidak boleh punya body
    @HTTP(method = "DELETE", path = "users/me", hasBody = true)
    suspend fun deleteAccount(@Body body: DeleteAccountRequest): Response<ApiEnvelope<EmptyData>>

    /** Unggah foto profil pengguna atau anak (jika [studentId] diisi oleh Wali). */
    @Multipart
    @POST("users/avatar")
    suspend fun uploadAvatar(
        @Part file: MultipartBody.Part,
        @Part("studentId") studentId: RequestBody? = null
    ): Response<ApiEnvelope<AvatarData>>
}
