package com.nthb.kibooz.data.repository

import com.nthb.kibooz.data.failureJson
import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.TrashApi
import com.nthb.kibooz.ml.TrashCategory
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout

class TrashRepositoryTest {

    @get:Rule
    val timeout: Timeout = Timeout(30, TimeUnit.SECONDS)

    private lateinit var server: MockWebServer
    private lateinit var repository: TrashRepository

    private val claimed = """{"success":true,"message":"Points claimed","data":{"pointsAdded":10,"totalPoints":120,
        "seasonPoints":40,"newRank":3,"remainingDailyScans":4}}"""

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        val client = testClient(server)
        repository = TrashRepository(client.create(TrashApi::class.java), client.moshi)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `claims with the token, the key and labels only`() = runBlocking {
        server.enqueue(jsonResponse(claimed))
        val claim = PendingTrashClaim("student-1", TrashCategory.ORGANIK, 0.91f)

        val result = repository.claim(claim) as ApiResult.Success

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/trash/scan-claim", request.path)
        assertEquals("Bearer jwt", request.getHeader("Authorization"))
        assertEquals(claim.idempotencyKey, request.getHeader("Idempotency-Key"))
        val body = request.body.readUtf8()
        assertTrue(body, body.contains("\"studentId\":\"student-1\""))
        assertTrue(body, body.contains("\"trashType\":\"ORGANIK\""))
        assertTrue(body, body.contains("\"confidenceScore\":0.91"))
        assertTrue("no photo ever leaves the phone", !body.contains("photo"))

        assertEquals(10, result.data.pointsAdded)
        assertEquals(4, result.data.remainingDailyScans)
        assertEquals(3, result.data.newRank)
    }

    @Test
    fun `a zero confidence is still sent because the server would default it to 0 anyway`() = runBlocking {
        server.enqueue(jsonResponse(claimed))

        repository.claim(PendingTrashClaim("student-1", TrashCategory.B3, 0f))

        assertTrue(server.takeRequest().body.readUtf8().contains("\"confidenceScore\":0.0"))
    }

    @Test
    fun `a B3 claim that earns nothing is still a success`() = runBlocking {
        server.enqueue(
            jsonResponse(
                """{"success":true,"data":{"pointsAdded":0,"totalPoints":120,"seasonPoints":40,"newRank":3,"remainingDailyScans":3}}"""
            )
        )

        val result = repository.claim(PendingTrashClaim("student-1", TrashCategory.B3, 0.8f)) as ApiResult.Success

        assertEquals(0, result.data.pointsAdded)
    }

    @Test
    fun `retrying the same claim sends the same key`() = runBlocking {
        server.enqueue(jsonResponse(failureJson("INTERNAL_ERROR"), 500))
        server.enqueue(jsonResponse(claimed))
        val claim = PendingTrashClaim("student-1", TrashCategory.ANORGANIK, 0.8f)

        repository.claim(claim)
        repository.claim(claim)

        assertEquals(
            server.takeRequest().getHeader("Idempotency-Key"),
            server.takeRequest().getHeader("Idempotency-Key")
        )
    }

    @Test
    fun `the daily limit is a 429 without a retry delay`() = runBlocking {
        server.enqueue(jsonResponse(failureJson("TRASH_DAILY_LIMIT_REACHED"), 429))

        val result = repository.claim(PendingTrashClaim("student-1", TrashCategory.ORGANIK, 0.9f)) as ApiResult.Failure

        assertEquals(429, result.status)
        assertEquals("TRASH_DAILY_LIMIT_REACHED", result.errorCode)
        assertNull(result.retryAfterSeconds)
    }

    @Test
    fun `a rate limit carries the retry delay`() = runBlocking {
        server.enqueue(jsonResponse(failureJson("RATE_LIMITED"), 429).setHeader("Retry-After", "30"))

        val result = repository.claim(PendingTrashClaim("student-1", TrashCategory.ORGANIK, 0.9f)) as ApiResult.Failure

        assertEquals("RATE_LIMITED", result.errorCode)
        assertEquals(30L, result.retryAfterSeconds)
    }

    @Test
    fun `a child of another parent is not found`() = runBlocking {
        server.enqueue(jsonResponse(failureJson("STUDENT_NOT_FOUND"), 404))

        val result = repository.claim(PendingTrashClaim("other", TrashCategory.ORGANIK, 0.9f)) as ApiResult.Failure

        assertEquals(404, result.status)
    }

    @Test
    fun `offline is a network error`() = runBlocking {
        server.shutdown()

        assertTrue(repository.claim(PendingTrashClaim("student-1", TrashCategory.ORGANIK, 0.9f)) is ApiResult.NetworkError)
    }
}
