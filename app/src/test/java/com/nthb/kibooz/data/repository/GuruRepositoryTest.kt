package com.nthb.kibooz.data.repository

import com.nthb.kibooz.data.failureJson
import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.GuruApi
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

class GuruRepositoryTest {

    @get:Rule
    val timeout: Timeout = Timeout(30, TimeUnit.SECONDS)

    private lateinit var server: MockWebServer
    private lateinit var repository: GuruRepository

    private val dashboard = """{"success":true,"data":{
        "teacher":{"fullName":"Siti Rahayu","nip":"1987","avatarUrl":null},
        "classOverview":{"classId":"c1","className":"Class A (Bunga)","joinCode":"ABC123","joinOpen":true,
            "totalStudents":20,"presentStudents":18,"dominantMood":"SENANG"},
        "dailyMoodDistribution":{"senang":10,"sedih":2,"marah":1,"bingung":5}}}"""

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        val client = testClient(server)
        repository = GuruRepository(client.create(GuruApi::class.java), client.moshi)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `reads the dashboard with the token and no class id by default`() = runBlocking {
        server.enqueue(jsonResponse(dashboard))

        val result = repository.dashboard() as ApiResult.Success

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/v1/guru/dashboard", request.path)
        assertEquals("Bearer jwt", request.getHeader("Authorization"))
        assertEquals("Siti Rahayu", result.data.teacher.fullName)
        assertEquals("Class A (Bunga)", result.data.classOverview.className)
        assertEquals(18, result.data.classOverview.presentStudents)
        assertEquals(Emotion.SENANG, result.data.classOverview.dominantMood)
        assertEquals(18, result.data.dailyMoodDistribution.total)
    }

    @Test
    fun `a class without mood records today has no dominant mood`() = runBlocking {
        server.enqueue(jsonResponse(dashboard.replace("\"SENANG\"", "null")))

        val result = repository.dashboard("c1") as ApiResult.Success

        assertNull(result.data.classOverview.dominantMood)
        assertEquals("/api/v1/guru/dashboard?classId=c1", server.takeRequest().path)
    }

    @Test
    fun `weekly analytics has no monthly distribution`() = runBlocking {
        server.enqueue(
            jsonResponse(
                """{"success":true,"data":{
                "donutSummary":[{"mood":"SENANG","percentage":60,"colorHex":"#FFC107"},
                    {"mood":"SEDIH","percentage":40,"colorHex":"#2196F3"}],
                "weeklyTrend":[{"day":"Monday","averageHappyScore":50},{"day":"Tuesday","averageHappyScore":70}]}}"""
            )
        )

        val result = repository.analytics(AnalyticsRange.WEEKLY) as ApiResult.Success

        assertEquals("/api/v1/guru/mood/analytics?range=weekly", server.takeRequest().path)
        assertEquals(2, result.data.donutSummary.size)
        assertEquals(70, result.data.weeklyTrend[1].averageHappyScore)
        assertNull(result.data.monthlyDistribution)
    }

    @Test
    fun `monthly analytics adds the distribution of the month`() = runBlocking {
        server.enqueue(
            jsonResponse(
                """{"success":true,"data":{"donutSummary":[],"weeklyTrend":[],
                "monthlyDistribution":[{"mood":"MARAH","count":3},{"mood":"BINGUNG","count":7}]}}"""
            )
        )

        val result = repository.analytics(AnalyticsRange.MONTHLY, "c1") as ApiResult.Success

        assertEquals("/api/v1/guru/mood/analytics?classId=c1&range=monthly", server.takeRequest().path)
        assertEquals(7, result.data.monthlyDistribution?.single { it.mood == Emotion.BINGUNG }?.count)
    }

    @Test
    fun `a teacher without a class gets a failure with the error code`() = runBlocking {
        server.enqueue(jsonResponse(failureJson("CLASS_NOT_FOUND"), 404))

        val result = repository.dashboard() as ApiResult.Failure

        assertEquals(404, result.status)
        assertTrue(result.errorCode == "CLASS_NOT_FOUND")
    }
}
