package com.nthb.kibooz.data.remote

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Menambahkan `Authorization: Bearer <jwt>` ke request ke API jika ada token. Header yang sudah diisi
 * pemanggil tidak ditimpa. Request ke host lain (misalnya alamat bertanda tangan untuk storage) dan rute
 * `/auth/` tidak pernah membawa token. Refresh token saat 401 bukan tugas kelas ini (lihat [TokenAuthenticator]).
 */
class AuthTokenInterceptor(
    private val tokenProvider: TokenProvider,
    private val apiBaseUrl: HttpUrl
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        if (!isApiRequest(request.url) || request.header(AUTHORIZATION) != null) {
            return chain.proceed(request)
        }

        val token = tokenProvider.accessToken()
        if (token.isNullOrBlank()) {
            return chain.proceed(request)
        }

        return chain.proceed(request.newBuilder().header(AUTHORIZATION, "Bearer $token").build())
    }

    private fun isApiRequest(url: HttpUrl): Boolean =
        url.scheme == apiBaseUrl.scheme &&
            url.host == apiBaseUrl.host &&
            url.port == apiBaseUrl.port &&
            "auth" !in url.pathSegments

    private companion object {
        const val AUTHORIZATION = "Authorization"
    }
}
