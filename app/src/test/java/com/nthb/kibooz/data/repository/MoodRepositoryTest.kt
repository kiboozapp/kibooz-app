package com.nthb.kibooz.data.repository

import com.nthb.kibooz.data.failureJson
import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.LogMoodBatchEntry
import com.nthb.kibooz.data.remote.MoodApi
import com.nthb.kibooz.data.remote.MoodSource
import com.nthb.kibooz.ml.Emotion
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout

class MoodRepositoryTest {

    @get:Rule
    val timeout: Timeout = Timeout(30, TimeUnit.SECONDS)

    private lateinit var server: MockWebServer
    private lateinit var repository: MoodRepository

    private val classId = "7d9f0a52-6c1e-4b8a-9d61-2c3f9a5f1d02"
    private val ani = "c4b3a981-5502-4f32-8231-10c970a03bd7"
    private val budi = "a1b2c3d4-0000-4f32-8231-10c970a03bd8"

    private val savedBody = """{"success":true,"message":"Mood logs saved","data":{"logs":[
        {"logId":"l1","studentId":"$ani","recordedAt":"2026-10-20T08:30:00Z"},
        {"logId":"l2","studentId":"$budi","recordedAt":"2026-10-20T08:30:00Z"}]}}"""

    private fun camera(studentId: String, mood: Emotion = Emotion.SENANG, confidence: Float = 0.94f) =
        LogMoodBatchEntry(studentId, mood, MoodSource.AI_CAMERA, confidence)

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        val client = testClient(server)
        repository = MoodRepository(client.create(MoodApi::class.java), client.moshi)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `saves the batch with the token, the idempotency key and the documented body`() = runBlocking {
        server.enqueue(jsonResponse(savedBody, 201))
        val batch = PendingMoodBatch(
            classId,
            listOf(camera(ani), LogMoodBatchEntry(budi, Emotion.SEDIH, MoodSource.MANUAL_INPUT, notes = "Menangis"))
        )

        val result = repository.saveBatch(batch)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/guru/mood/log/batch", request.path)
        assertEquals("Bearer jwt", request.getHeader("Authorization"))
        assertEquals(batch.idempotencyKey, request.getHeader("Idempotency-Key"))

        val body = request.body.readUtf8()
        assertTrue(body, body.contains("\"classId\":\"$classId\""))
        assertTrue(body, body.contains("\"moodType\":\"SENANG\""))
        assertTrue(body, body.contains("\"source\":\"AI_CAMERA\""))
        assertTrue(body, body.contains("\"confidenceScore\":0.94"))
        assertTrue(body, body.contains("\"moodType\":\"SEDIH\""))
        assertTrue(body, body.contains("\"notes\":\"Menangis\""))
        assertTrue("recordedAt is left to the server", !body.contains("recordedAt"))

        val logs = (result as ApiResult.Success).data
        assertEquals(listOf("l1", "l2"), logs.map { it.logId })
        assertEquals(listOf(ani, budi), logs.map { it.studentId })
    }

    @Test
    fun `a manual entry sends no confidence score so the server applies its default of 1`() = runBlocking {
        server.enqueue(jsonResponse(savedBody, 201))

        repository.saveBatch(
            PendingMoodBatch(classId, listOf(LogMoodBatchEntry(ani, Emotion.MARAH, MoodSource.MANUAL_INPUT)))
        )

        assertTrue(!server.takeRequest().body.readUtf8().contains("confidenceScore"))
    }

    @Test
    fun `retrying the same batch sends the same key and a new batch sends a new one`() = runBlocking {
        server.enqueue(jsonResponse(failureJson("INTERNAL_ERROR"), 500))
        server.enqueue(jsonResponse(savedBody, 201))
        val batch = PendingMoodBatch(classId, listOf(camera(ani)))

        repository.saveBatch(batch)
        repository.saveBatch(batch)

        val first = server.takeRequest().getHeader("Idempotency-Key")
        val second = server.takeRequest().getHeader("Idempotency-Key")
        assertEquals(first, second)
        assertNotEquals(batch.idempotencyKey, PendingMoodBatch(classId, listOf(camera(ani))).idempotencyKey)
    }

    @Test
    fun `generated keys fit the pattern the server accepts`() {
        val key = PendingMoodBatch(classId, listOf(camera(ani))).idempotencyKey

        assertTrue(key, Regex("^[A-Za-z0-9_-]{8,64}$").matches(key))
    }

    @Test
    fun `a batch of 30 is allowed and 31 or none is refused before any request`() {
        PendingMoodBatch(classId, List(30) { camera(ani) })

        for (entries in listOf(emptyList(), List(31) { camera(ani) })) {
            try {
                PendingMoodBatch(classId, entries)
                fail("expected ${entries.size} entries to be refused")
            } catch (expected: IllegalArgumentException) {
                // diharapkan
            }
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a camera entry without a confidence score is refused before any request`() {
        try {
            PendingMoodBatch(classId, listOf(LogMoodBatchEntry(ani, Emotion.SENANG, MoodSource.AI_CAMERA)))
            fail("expected the missing confidence to be refused")
        } catch (expected: IllegalArgumentException) {
            assertEquals(0, server.requestCount)
        }
    }

    @Test
    fun `a full day for a child is reported with the entry that has no room`() = runBlocking {
        server.enqueue(
            jsonResponse(
                """{"success":false,"message":"limit","errorCode":"MOOD_LOG_LIMIT_REACHED",
                   "details":{"entries[1]":"has 20 mood logs today"}}""",
                409
            )
        )

        val result = repository.saveBatch(PendingMoodBatch(classId, listOf(camera(ani), camera(budi))))

        result as ApiResult.Failure
        assertEquals(409, result.status)
        assertEquals("MOOD_LOG_LIMIT_REACHED", result.errorCode)
        assertEquals("has 20 mood logs today", result.details?.get("entries[1]"))
    }

    @Test
    fun `validation errors keep the field paths`() = runBlocking {
        server.enqueue(
            jsonResponse(
                """{"success":false,"message":"bad","errorCode":"VALIDATION_ERROR",
                   "details":{"entries[0].studentId":"is not in this class"}}""",
                400
            )
        )

        val result = repository.saveBatch(PendingMoodBatch(classId, listOf(camera(ani)))) as ApiResult.Failure

        assertEquals("is not in this class", result.details?.get("entries[0].studentId"))
    }

    @Test
    fun `a reused key and a running request are told apart by error code`() = runBlocking {
        server.enqueue(jsonResponse(failureJson("IDEMPOTENCY_KEY_REUSED"), 422))
        server.enqueue(jsonResponse(failureJson("IDEMPOTENCY_IN_PROGRESS"), 409))
        val batch = PendingMoodBatch(classId, listOf(camera(ani)))

        val reused = repository.saveBatch(batch) as ApiResult.Failure
        val running = repository.saveBatch(batch) as ApiResult.Failure

        assertEquals(422 to "IDEMPOTENCY_KEY_REUSED", reused.status to reused.errorCode)
        assertEquals(409 to "IDEMPOTENCY_IN_PROGRESS", running.status to running.errorCode)
    }

    @Test
    fun `a rate limit carries the retry delay`() = runBlocking {
        server.enqueue(jsonResponse(failureJson("RATE_LIMITED"), 429).setHeader("Retry-After", "20"))

        val result = repository.saveBatch(PendingMoodBatch(classId, listOf(camera(ani)))) as ApiResult.Failure

        assertEquals(20L, result.retryAfterSeconds)
    }

    @Test
    fun `being offline is a network error and not a failure`() = runBlocking {
        server.shutdown()

        val result = repository.saveBatch(PendingMoodBatch(classId, listOf(camera(ani))))

        assertTrue(result is ApiResult.NetworkError)
        assertNull((result as? ApiResult.Success)?.data)
    }
}
