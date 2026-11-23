package com.nthb.kibooz.data.remote

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServiceOutageTest {

    private fun failure(status: Int, errorCode: String? = null, retryAfter: Long? = null) =
        ApiResult.Failure(status, errorCode, null, null, retryAfter)

    private val offline = ApiResult.NetworkError(IOException("offline"))

    @Test
    fun `no connection is an offline outage`() {
        assertEquals(OutageKind.OFFLINE, offline.outageKind())
    }

    @Test
    fun `502, 503 and 504 without a JSON body come from the network or proxy`() {
        assertEquals(OutageKind.UNAVAILABLE, failure(502).outageKind())
        assertEquals(OutageKind.UNAVAILABLE, failure(503).outageKind())
        assertEquals(OutageKind.UNAVAILABLE, failure(504).outageKind())
    }

    @Test
    fun `a 503 with SERVER_TIMEOUT counts as unavailable`() {
        assertEquals(OutageKind.UNAVAILABLE, failure(503, "SERVER_TIMEOUT", retryAfter = 5).outageKind())
    }

    @Test
    fun `errors that carry an API error code are not outages`() {
        assertNull(failure(502, "INTERNAL_ERROR").outageKind())
        assertNull(failure(500, "INTERNAL_ERROR").outageKind())
        assertNull(failure(403, "AUTH_FORBIDDEN").outageKind())
        assertNull(failure(404, "CLASS_NOT_FOUND").outageKind())
        assertNull(failure(429, "RATE_LIMITED", retryAfter = 4).outageKind())
    }

    @Test
    fun `a success is not an outage`() {
        assertNull(ApiResult.Success(Unit).outageKind())
        assertNull(ApiResult.Success(Unit).outage(1))
    }

    @Test
    fun `without Retry-After the wait starts at 30 seconds and doubles each attempt`() {
        assertEquals(30L, offline.outage(1)?.waitSeconds)
        assertEquals(60L, offline.outage(2)?.waitSeconds)
        assertEquals(120L, offline.outage(3)?.waitSeconds)
        assertEquals(240L, offline.outage(4)?.waitSeconds)
    }

    @Test
    fun `the growing wait stops at five minutes`() {
        assertEquals(300L, offline.outage(5)?.waitSeconds)
        assertEquals(300L, offline.outage(12)?.waitSeconds)
    }

    @Test
    fun `an attempt below 1 is treated as the first`() {
        assertEquals(30L, offline.outage(0)?.waitSeconds)
    }

    @Test
    fun `Retry-After is obeyed and the jitter is added on top`() {
        val timeout = failure(503, "SERVER_TIMEOUT", retryAfter = 10)

        assertEquals(10L, timeout.outage(1)?.waitSeconds)
        assertEquals(14L, timeout.outage(1, jitterSeconds = 4)?.waitSeconds)
        assertEquals(14L, timeout.outage(3, jitterSeconds = 4)?.waitSeconds)
    }

    @Test
    fun `jitter is not added when there is no Retry-After`() {
        assertEquals(30L, failure(503).outage(1, jitterSeconds = 4)?.waitSeconds)
    }

    @Test
    fun `the wait is at least one second`() {
        assertEquals(1L, failure(503, "SERVER_TIMEOUT", retryAfter = 0).outage(1)?.waitSeconds)
    }

    @Test
    fun `the outage carries its kind`() {
        assertEquals(OutageKind.OFFLINE, offline.outage(1)?.kind)
        assertEquals(OutageKind.UNAVAILABLE, failure(504).outage(1)?.kind)
    }
}
