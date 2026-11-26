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
        assertTrue("a server without the field means full points", !result.data.reducedPoints)
    }

    @Test
    fun `a claim past the daily limit reads the reduced points flag`() = runBlocking {
        server.enqueue(
            jsonResponse(
                """{"success":true,"data":{"pointsAdded":1,"totalPoints":121,"seasonPoints":41,"newRank":3,""" +
                    """"remainingDailyScans":0,"reducedPoints":true}}"""
            )
        )

        val result = repository.claim(PendingTrashClaim("student-1", TrashCategory.ANORGANIK, 0.9f)) as ApiResult.Success

        assertEquals(1, result.data.pointsAdded)
        assertEquals(0, result.data.remainingDailyScans)
        assertTrue(result.data.reducedPoints)
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

    private val stats = """{"success":true,"data":{"student":{"id":"s1","fullName":"Amelia"},
        "totalPoints":120,"seasonPoints":40,"classRank":3,"totalScans":10,
        "breakdown":[{"trashType":"ORGANIK","scans":5,"points":50},{"trashType":"ANORGANIK","scans":4,"points":60},
                     {"trashType":"B3","scans":1,"points":0}],
        "todayScans":2,"dailyLimit":5,"remainingDailyScans":3}}"""

    @Test
    fun `stats are read for the oldest child by default and for a chosen child`() = runBlocking {
        server.enqueue(jsonResponse(stats))
        server.enqueue(jsonResponse(stats))

        val result = repository.stats() as ApiResult.Success
        repository.stats("s2")

        assertEquals("/api/v1/trash/stats", server.takeRequest().path)
        assertEquals("/api/v1/trash/stats?studentId=s2", server.takeRequest().path)
        assertEquals(5, result.data.scansOf(TrashCategory.ORGANIK))
        assertEquals(1, result.data.scansOf(TrashCategory.B3))
        assertEquals(50, result.data.percentOf(TrashCategory.ORGANIK))
        assertEquals(40, result.data.percentOf(TrashCategory.ANORGANIK))
        assertEquals(3, result.data.remainingDailyScans)
    }

    @Test
    fun `a child without scans has zero percent and a missing category counts as zero`() = runBlocking {
        server.enqueue(
            jsonResponse(
                """{"success":true,"data":{"student":{"id":"s1","fullName":"Amelia"},"totalPoints":0,"seasonPoints":0,
                "classRank":0,"totalScans":0,"breakdown":[],"todayScans":0,"dailyLimit":5,"remainingDailyScans":5}}"""
            )
        )

        val result = repository.stats() as ApiResult.Success

        assertEquals(0, result.data.scansOf(TrashCategory.ANORGANIK))
        assertEquals(0, result.data.percentOf(TrashCategory.ORGANIK))
    }
}
