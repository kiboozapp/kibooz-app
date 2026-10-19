package com.nthb.kibooz.data.remote

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Menambahkan `Authorization: Bearer <jwt>` ke setiap request jika ada token. Header yang sudah diisi
 * pemanggil tidak ditimpa. Refresh token saat 401 bukan tugas kelas ini (lihat FE-03).
 */
class AuthTokenInterceptor(private val tokenProvider: TokenProvider) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val token = tokenProvider.accessToken()

        if (token.isNullOrBlank() || request.header(AUTHORIZATION) != null) {
            return chain.proceed(request)
        }

        return chain.proceed(request.newBuilder().header(AUTHORIZATION, "Bearer $token").build())
    }

    private companion object {
        const val AUTHORIZATION = "Authorization"
    }
}
