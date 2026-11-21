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
        assertEquals(Emotion.BINGUNG, result.data.todayMood?.moodType)
        assertEquals("Ragu saat berpisah", result.data.todayMood?.teacherNotes)
        assertEquals(48, result.data.pointsSummary.sortedCount)
        assertEquals(3, result.data.pointsSummary.classRank)
        assertEquals("g1", result.data.recommendedGuidance?.id)
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
}
