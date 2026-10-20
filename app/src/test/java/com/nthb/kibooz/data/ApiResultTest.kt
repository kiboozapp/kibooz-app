package com.nthb.kibooz.data

import com.nthb.kibooz.data.remote.ApiEnvelope
import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.ERROR_MALFORMED_RESPONSE
import com.nthb.kibooz.data.remote.EmptyData
import com.nthb.kibooz.data.remote.safeApiCall
import com.nthb.kibooz.data.remote.safeApiCallEmpty
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.IOException
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.Headers.Companion.headersOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class ApiResultTest {

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val json = "application/json".toMediaType()

    private fun <T> error(code: Int, body: String, retryAfter: String? = null): Response<ApiEnvelope<T>> {
        val raw = okhttp3.Response.Builder()
            .request(okhttp3.Request.Builder().url("http://localhost/").build())
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .code(code)
            .message("error")
            .headers(if (retryAfter == null) headersOf() else headersOf("Retry-After", retryAfter))
            .build()
        return Response.error(body.toResponseBody(json), raw)
    }

    @Test
    fun `a success carries the data and the message`() = runBlocking {
        val result = safeApiCall(moshi) { Response.success(ApiEnvelope(true, "OK", "payload")) }

        assertEquals(ApiResult.Success("payload", "OK"), result)
    }

    @Test
    fun `a success without data is malformed`() = runBlocking {
        val result = safeApiCall<String>(moshi) { Response.success(ApiEnvelope(true)) }

        assertEquals(ERROR_MALFORMED_RESPONSE, (result as ApiResult.Failure).errorCode)
    }

    @Test
    fun `an empty success is fine for endpoints without data`() = runBlocking {
        val result = safeApiCallEmpty(moshi) { Response.success(ApiEnvelope<EmptyData>(true, "Logged out")) }

        assertEquals(ApiResult.Success(Unit, "Logged out"), result)
    }

    @Test
    fun `a failure keeps the error code, details and retry delay`() = runBlocking {
        val body = """{"success":false,"message":"slow down","errorCode":"RATE_LIMITED",
            "details":{"entries[0].moodType":"must be one of"}}"""

        val result = safeApiCall<String>(moshi) { error(429, body, retryAfter = "12") } as ApiResult.Failure

        assertEquals(429, result.status)
        assertEquals("RATE_LIMITED", result.errorCode)
        assertEquals("must be one of", result.details!!["entries[0].moodType"])
        assertEquals(12L, result.retryAfterSeconds)
    }

    @Test
    fun `a proxy error without a json body has no error code`() = runBlocking {
        val result = safeApiCall<String>(moshi) { error(429, "<html>Too many</html>") } as ApiResult.Failure

        assertEquals(429, result.status)
        assertNull(result.errorCode)
        assertNull(result.retryAfterSeconds)
    }

    @Test
    fun `a retry-after date is ignored instead of crashing`() = runBlocking {
        val result = safeApiCall<String>(moshi) {
            error(429, "", retryAfter = "Wed, 21 Oct 2026 07:28:00 GMT")
        } as ApiResult.Failure

        assertNull(result.retryAfterSeconds)
    }

    @Test
    fun `an io error becomes a network error`() = runBlocking {
        val result = safeApiCall<String>(moshi) { throw IOException("offline") }

        assertTrue(result is ApiResult.NetworkError)
    }
}
