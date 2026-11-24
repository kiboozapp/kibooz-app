package com.nthb.kibooz.data.remote

import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * Menangani 401 dengan satu kali refresh token lalu mengulang request. [refresh] menerima access
 * token yang ditolak dan mengembalikan token baru, atau null jika sesi tidak bisa diperbarui. Dalam
 * kasus itu balasan 401 asli diteruskan ke pemanggil.
 *
 * Tidak dipakai untuk request tanpa token (misalnya login yang salah sandi) maupun rute `/auth/`.
 */
class TokenAuthenticator(
    private val refresh: suspend (failedAccessToken: String) -> String?
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        val request = response.request
        val sentToken = request.header("Authorization")?.removePrefix("Bearer ")?.trim()

        if (sentToken.isNullOrEmpty() || sentToken.startsWith("mock-") || "auth" in request.url.pathSegments || attempts(response) > 1) {
            return null
        }

        val newToken = runBlocking { refresh(sentToken) } ?: return null

        return request.newBuilder().header("Authorization", "Bearer $newToken").build()
    }

    private fun attempts(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
