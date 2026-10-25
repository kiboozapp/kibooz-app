package com.nthb.kibooz.data.remote

import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RateLimitTest {

    private fun failure(status: Int = 429, code: String? = "RATE_LIMITED", retryAfter: Long? = null) =
        ApiResult.Failure(status, code, "x", retryAfterSeconds = retryAfter)

    @Test
    fun `Retry-After is honoured`() {
        assertEquals(12L, failure(retryAfter = 12).rateLimitWaitSeconds(1))
        assertEquals(12L, failure(retryAfter = 12).rateLimitWaitSeconds(3))
    }

    @Test
    fun `a Retry-After of zero still waits one second`() {
        assertEquals(1L, failure(retryAfter = 0).rateLimitWaitSeconds(1))
    }

    @Test
    fun `without Retry-After it waits 30 seconds and doubles on each repeat`() {
        assertEquals(30L, failure().rateLimitWaitSeconds(1))
        assertEquals(60L, failure().rateLimitWaitSeconds(2))
        assertEquals(120L, failure().rateLimitWaitSeconds(3))
    }

    @Test
    fun `a proxy 429 without a JSON body follows the same default`() {
        assertEquals(30L, failure(code = null).rateLimitWaitSeconds(1))
    }

    @Test
    fun `a wait of more than two minutes is left to the user`() {
        assertNull(failure(retryAfter = 600).rateLimitWaitSeconds(1))
        assertNull(failure().rateLimitWaitSeconds(4))
    }

    @Test
    fun `the daily trash limit and other statuses are never retried`() {
        assertNull(failure(code = "TRASH_DAILY_LIMIT_REACHED").rateLimitWaitSeconds(1))
        assertNull(failure(status = 500, code = "INTERNAL_ERROR").rateLimitWaitSeconds(1))
        assertNull(failure(status = 409, code = "IDEMPOTENCY_IN_PROGRESS").rateLimitWaitSeconds(1))
    }

    private fun <T> sequence(vararg results: ApiResult<T>): suspend () -> ApiResult<T> {
        var next = 0
        return { results[minOf(next++, results.size - 1)] }
    }

    @Test
    fun `the call is repeated after the wait and the countdown is reported`() = runBlocking {
        val pauses = mutableListOf<Long>()
        val ticks = mutableListOf<Int>()

        val result = retryWhenRateLimited(onWait = { ticks += it }, pause = { pauses += it },
            call = sequence(failure(retryAfter = 3), ApiResult.Success("ok")))

        assertEquals(ApiResult.Success("ok"), result)
        assertEquals(listOf(1000L, 1000L, 1000L), pauses)
        assertEquals(listOf(3, 2, 1, 0), ticks)
    }

    @Test
    fun `it gives up after three retries and returns the last failure`() = runBlocking {
        var calls = 0
        val result = retryWhenRateLimited<String>(pause = {}) {
            calls++
            failure(retryAfter = 1)
        }

        assertEquals(4, calls)
        assertTrue(result is ApiResult.Failure)
    }

    @Test
    fun `only 429 is retried`() = runBlocking {
        var calls = 0
        val failed = retryWhenRateLimited<String>(pause = {}) { calls++; failure(status = 400, code = "VALIDATION_ERROR") }
        val offline = retryWhenRateLimited<String>(pause = {}) { calls++; ApiResult.NetworkError(IOException("offline")) }

        assertEquals(2, calls)
        assertTrue(failed is ApiResult.Failure)
        assertTrue(offline is ApiResult.NetworkError)
    }

    @Test
    fun `a success on the first try never waits`() = runBlocking {
        val pauses = mutableListOf<Long>()

        retryWhenRateLimited(pause = { pauses += it }) { ApiResult.Success(1) }

        assertTrue(pauses.isEmpty())
    }
}
