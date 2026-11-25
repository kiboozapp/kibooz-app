package com.nthb.kibooz.data.repository

import com.nthb.kibooz.data.failureJson
import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.WaliApi
import com.nthb.kibooz.ml.Emotion
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

class WaliRepositoryTest {

    @get:Rule
    val timeout: Timeout = Timeout(30, TimeUnit.SECONDS)

    private lateinit var server: MockWebServer
    private lateinit var repository: WaliRepository

    private val full = """{"success":true,"data":{
        "student":{"id":"s1","fullName":"Amelia","classId":"c1","className":"Class A • TK Pertiwi","avatarUrl":null,"nisn":"0012345678"},
        "todayMood":{"moodType":"BINGUNG","label":"Bingung","confidence":0.82,"recordedAt":"2026-11-03T00:30:00Z","teacherNotes":"Ragu saat berpisah"},
        "pointsSummary":{"totalPoints":120,"seasonPoints":40,"classRank":3,"organicCount":28,"anorganicCount":20},
        "recommendedGuidance":{"id":"g1","title":"Berikan Rasa Aman","category":"BINGUNG","bannerUrl":""}}}"""

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        val client = testClient(server)
        repository = WaliRepository(client.create(WaliApi::class.java), client.moshi)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `reads the dashboard of the oldest child when no child is named`() = runBlocking {
        server.enqueue(jsonResponse(full))

        val result = repository.dashboard() as ApiResult.Success

        val request = server.takeRequest()
        assertEquals("/api/v1/wali/dashboard", request.path)
        assertEquals("Bearer jwt", request.getHeader("Authorization"))
        assertEquals("Amelia", result.data.student.fullName)
        assertEquals("Class A • TK Pertiwi", result.data.student.className)
        assertNull(result.data.student.joinCode)
        assertEquals(Emotion.BINGUNG, result.data.todayMood?.moodType)
        assertEquals("Ragu saat berpisah", result.data.todayMood?.teacherNotes)
        assertEquals(48, result.data.pointsSummary.sortedCount)
        assertEquals(3, result.data.pointsSummary.classRank)
        assertEquals("g1", result.data.recommendedGuidance?.id)
    }

    @Test
    fun `reads joinCode from dashboard student when returned by backend`() = runBlocking {
        val withJoinCode = full.replace(
            """"className":"Class A • TK Pertiwi"""",
            """"className":"Class A • TK Pertiwi","joinCode":"K7M2QX4R""""
        )
        server.enqueue(jsonResponse(withJoinCode))

        val result = repository.dashboard() as ApiResult.Success

        assertEquals("K7M2QX4R", result.data.student.joinCode)
    }

    @Test
    fun `a named child is sent as studentId`() = runBlocking {
        server.enqueue(jsonResponse(full))

        repository.dashboard("s2")

        assertEquals("/api/v1/wali/dashboard?studentId=s2", server.takeRequest().path)
    }

    @Test
    fun `before the teacher records a mood there is no mood and no guidance`() = runBlocking {
        val noMood = full
            .replace(Regex("\"todayMood\":\\{[^}]*\\}"), "\"todayMood\":null")
            .replace(Regex("\"recommendedGuidance\":\\{[^}]*\\}"), "\"recommendedGuidance\":null")
        server.enqueue(jsonResponse(noMood))

        val result = repository.dashboard() as ApiResult.Success

        assertNull(result.data.todayMood)
        assertNull(result.data.recommendedGuidance)
        assertEquals(120, result.data.pointsSummary.totalPoints)
    }

    @Test
    fun `a parent without children gets the error code`() = runBlocking {
        server.enqueue(jsonResponse(failureJson("NO_CHILD_REGISTERED"), 404))

        val result = repository.dashboard() as ApiResult.Failure

        assertEquals("NO_CHILD_REGISTERED", result.errorCode)
    }

    @Test
    fun `applying a guidance sends the key, the ids and the trimmed notes`() = runBlocking {
        server.enqueue(jsonResponse("""{"success":true,"message":"Guidance applied"}""", 200))
        val apply = PendingGuidanceApply("g1", "s1", "  Sudah dipeluk  ")

        val result = repository.applyGuidance(apply)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/wali/guidance/apply", request.path)
        assertEquals("Bearer jwt", request.getHeader("Authorization"))
        assertEquals(apply.idempotencyKey, request.getHeader("Idempotency-Key"))
        val body = request.body.readUtf8()
        assertEquals("""{"guidanceId":"g1","studentId":"s1","parentNotes":"Sudah dipeluk"}""", body)
        assertTrue(result is ApiResult.Success)
    }

    @Test
    fun `empty notes are left out of the request`() = runBlocking {
        server.enqueue(jsonResponse("""{"success":true}"""))

        repository.applyGuidance(PendingGuidanceApply("g1", "s1", "   "))

        assertEquals("""{"guidanceId":"g1","studentId":"s1"}""", server.takeRequest().body.readUtf8())
    }

    @Test
    fun `retrying the same action sends the same key`() = runBlocking {
        server.enqueue(jsonResponse(failureJson("INTERNAL_ERROR"), 500))
        server.enqueue(jsonResponse("""{"success":true}"""))
        val apply = PendingGuidanceApply("g1", "s1", null)

        repository.applyGuidance(apply)
        repository.applyGuidance(apply)

        assertEquals(server.takeRequest().getHeader("Idempotency-Key"), server.takeRequest().getHeader("Idempotency-Key"))
    }

    @Test
    fun `requestMove sends studentId, idempotency key and normalized classCode, returning MoveRequest on success`() = runBlocking {
        val json = """{
            "success": true,
            "message": "Move requested",
            "data": {
                "id": "req-1",
                "studentId": "s1",
                "classId": "c2",
                "className": "Kelas Bintang",
                "status": "pending",
                "requestedAt": "2026-11-24T15:41:17Z"
            }
        }"""
        server.enqueue(jsonResponse(json, 201))

        val result = repository.requestMove("s1", "P3TFA84K") as ApiResult.Success

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/wali/children/s1/move-requests", request.path)
        assertEquals("Bearer jwt", request.getHeader("Authorization"))
        assertTrue(!request.getHeader("Idempotency-Key").isNullOrBlank())
        assertEquals("""{"classCode":"P3TFA84K"}""", request.body.readUtf8())
        assertEquals("req-1", result.data.id)
        assertEquals("Kelas Bintang", result.data.className)
        assertEquals("pending", result.data.status)
    }

    @Test
    fun `requestMove handles MOVE_REQUEST_PENDING failure`() = runBlocking {
        val errorJson = """{
            "success": false,
            "message": "This child already has a request that waits for a teacher",
            "errorCode": "MOVE_REQUEST_PENDING"
        }"""
        server.enqueue(jsonResponse(errorJson, 409))

        val result = repository.requestMove("s1", "P3TFA84K") as ApiResult.Failure
        assertEquals(409, result.status)
        assertEquals("MOVE_REQUEST_PENDING", result.errorCode)
    }

    @Test
    fun `requestMove handles CLASS_CODE_NOT_FOUND failure`() = runBlocking {
        val errorJson = """{
            "success": false,
            "message": "Class code not found",
            "errorCode": "CLASS_CODE_NOT_FOUND"
        }"""
        server.enqueue(jsonResponse(errorJson, 404))

        val result = repository.requestMove("s1", "INVALID") as ApiResult.Failure
        assertEquals(404, result.status)
        assertEquals("CLASS_CODE_NOT_FOUND", result.errorCode)
    }
}
