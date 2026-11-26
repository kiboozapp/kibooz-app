package com.nthb.kibooz.data.remote

import com.nthb.kibooz.BuildConfig
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** Mengirim dan mengambil byte lewat alamat bertanda tangan milik storage, bukan lewat API. */
interface ChatStorage {
    /** `PUT` isi [source] ke [url] dengan header dari server. */
    suspend fun upload(
        url: String,
        headers: Map<String, String>,
        source: UploadSource,
        onProgress: (sentBytes: Long) -> Unit = {}
    ): ApiResult<Unit>

    /** Mengambil isi [url] ke memori, paling banyak [maxBytes]. Hanya untuk foto. */
    suspend fun download(url: String, maxBytes: Long): ApiResult<ByteArray>
}

/**
 * Klien sendiri yang sengaja dibangun dari nol. Tanpa interseptor, authenticator, cookie, dan log, jadi token
 * aplikasi tidak pernah sampai ke host storage dan alamat bertanda tangan tidak pernah tercetak. Pengalihan
 * tidak diikuti. Alamat http hanya diterima jika alamat API juga http, yaitu build debug ke server lokal.
 */
class OkHttpChatStorage(apiBaseUrl: String = BuildConfig.API_BASE_URL) : ChatStorage {

    private val httpsOnly = apiBaseUrl.startsWith("https://", ignoreCase = true)

    internal val client: OkHttpClient = OkHttpClient.Builder()
        .connectionSpecs(connectionSpecsFor(apiBaseUrl))
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TRANSFER_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TRANSFER_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    override suspend fun upload(
        url: String,
        headers: Map<String, String>,
        source: UploadSource,
        onProgress: (sentBytes: Long) -> Unit
    ): ApiResult<Unit> {
        val target = accepted(url) ?: return ApiResult.Failure(0, ERROR_INSECURE_ADDRESS, null)
        val request = try {
            Request.Builder()
                .url(target)
                .apply {
                    headers.filterKeys { name -> FORBIDDEN_HEADERS.none { it.equals(name, ignoreCase = true) } }
                        .forEach { (name, value) -> addHeader(name, value) }
                }
                .put(ExactLengthRequestBody(source, mediaType = null, onProgress = onProgress))
                .build()
        } catch (_: IllegalArgumentException) {
            return ApiResult.Failure(0, ERROR_UPLOAD_REJECTED, null)
        }

        return try {
            client.newCall(request).await<ApiResult<Unit>> { response ->
                if (response.isSuccessful) ApiResult.Success(Unit) else response.rejected(ERROR_UPLOAD_REJECTED)
            }
        } catch (_: ChatUploadSourceException) {
            ApiResult.Failure(0, ERROR_UPLOAD_SOURCE, null)
        } catch (e: IOException) {
            ApiResult.NetworkError(e)
        }
    }

    override suspend fun download(url: String, maxBytes: Long): ApiResult<ByteArray> {
        val target = accepted(url) ?: return ApiResult.Failure(0, ERROR_INSECURE_ADDRESS, null)

        return try {
            client.newCall(Request.Builder().url(target).get().build()).await<ApiResult<ByteArray>> { response ->
                val source = response.body?.source()
                if (!response.isSuccessful || source == null) {
                    response.rejected(ERROR_DOWNLOAD_REJECTED)
                } else if (source.request(maxBytes + 1)) {
                    ApiResult.Failure(response.code, "FILE_TOO_LARGE", null)
                } else {
                    ApiResult.Success(source.readByteArray())
                }
            }
        } catch (e: IOException) {
            ApiResult.NetworkError(e)
        }
    }

    private fun accepted(url: String): HttpUrl? = url.toHttpUrlOrNull()?.takeIf { it.isHttps || !httpsOnly }

    private fun Response.rejected(errorCode: String): ApiResult.Failure =
        ApiResult.Failure(code, errorCode, null, retryAfterSeconds = header("Retry-After")?.trim()?.toLongOrNull())

    /** Menjalankan panggilan tanpa memblokir, dan membatalkannya saat coroutine dibatalkan. */
    private suspend fun <T> Call.await(read: (Response) -> T): T = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                val value = try {
                    response.use(read)
                } catch (e: IOException) {
                    continuation.resumeWithException(e)
                    return
                }
                continuation.resume(value)
            }
        })
    }

    private companion object {
        const val CONNECT_TIMEOUT_SECONDS = 15L

        // Batas diam per baca atau tulis, bukan batas seluruh unggahan
        const val TRANSFER_TIMEOUT_SECONDS = 60L

        val FORBIDDEN_HEADERS = listOf("Authorization", "Cookie", "Proxy-Authorization")
    }
}
