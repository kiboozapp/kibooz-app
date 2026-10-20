package com.nthb.kibooz.data.remote

import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonEncodingException
import com.squareup.moshi.Moshi
import java.io.IOException
import retrofit2.Response

/** Data kosong untuk endpoint yang hanya membalas `{success, message}`. */
class EmptyData

/** Hasil satu panggilan API. Percabangan error memakai [Failure.errorCode], bukan pesan. */
sealed interface ApiResult<out T> {
    data class Success<T>(val data: T, val message: String? = null) : ApiResult<T>

    /** Server membalas non-2xx. [errorCode] null jika balasan bukan JSON amplop (misalnya dari proxy). */
    data class Failure(
        val status: Int,
        val errorCode: String?,
        val message: String?,
        val details: Map<String, String>? = null,
        val retryAfterSeconds: Long? = null
    ) : ApiResult<Nothing>

    /** Tidak ada balasan sama sekali (offline, timeout, koneksi putus). */
    data class NetworkError(val cause: IOException) : ApiResult<Nothing>
}

const val ERROR_MALFORMED_RESPONSE = "MALFORMED_RESPONSE"

private class ErrorBody(val message: String?, val errorCode: String?, val details: Map<String, String>?)

/** Menjalankan panggilan Retrofit dan mengubahnya menjadi [ApiResult]. Tidak melempar kecuali dibatalkan. */
suspend fun <T : Any> safeApiCall(
    moshi: Moshi,
    block: suspend () -> Response<ApiEnvelope<T>>
): ApiResult<T> = execute(moshi, block) { envelope ->
    envelope.data?.let { ApiResult.Success(it, envelope.message) }
}

/** Seperti [safeApiCall] untuk endpoint tanpa `data`, misalnya logout. */
suspend fun safeApiCallEmpty(
    moshi: Moshi,
    block: suspend () -> Response<ApiEnvelope<EmptyData>>
): ApiResult<Unit> = execute(moshi, block) { envelope -> ApiResult.Success(Unit, envelope.message) }

private suspend fun <T, R> execute(
    moshi: Moshi,
    block: suspend () -> Response<ApiEnvelope<T>>,
    onSuccess: (ApiEnvelope<T>) -> ApiResult<R>?
): ApiResult<R> {
    val response = try {
        block()
    } catch (e: JsonEncodingException) {
        return ApiResult.Failure(0, ERROR_MALFORMED_RESPONSE, e.message)
    } catch (e: IOException) {
        return ApiResult.NetworkError(e)
    } catch (e: JsonDataException) {
        return ApiResult.Failure(0, ERROR_MALFORMED_RESPONSE, e.message)
    }

    if (response.isSuccessful) {
        val envelope = response.body()
        return envelope?.let(onSuccess)
            ?: ApiResult.Failure(response.code(), ERROR_MALFORMED_RESPONSE, "Response has no data")
    }

    val error = try {
        response.errorBody()?.source()?.let { moshi.adapter(ErrorBody::class.java).fromJson(it) }
    } catch (e: IOException) {
        null // JsonEncodingException ikut di sini: body bukan JSON
    } catch (e: JsonDataException) {
        null
    }
    val retryAfter = response.headers()["Retry-After"]?.trim()?.toLongOrNull()

    return ApiResult.Failure(response.code(), error?.errorCode, error?.message, error?.details, retryAfter)
}
