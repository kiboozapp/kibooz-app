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

    private fun dashboardJson(dominantMood: String = "\"SENANG\"") = """
        {"success":true,"data":{
          "teacher":{"fullName":"Siti Rahayu, S.Pd.","nip":"198507152010032005","avatarUrl":null},
          "classOverview":{"classId":"c1","className":"Class A (Mawar)","joinCode":"KBZ48R2M","joinOpen":true,
            "totalStudents":24,"presentStudents":22,"dominantMood":$dominantMood},
          "dailyMoodDistribution":{"senang":14,"sedih":3,"marah":1,"bingung":4}}}
    """.trimIndent()

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
    fun `reads the dashboard of the default class`() = runBlocking {
        server.enqueue(jsonResponse(dashboardJson()))

        val result = repository.dashboard() as ApiResult.Success

        assertEquals("/api/v1/guru/dashboard", server.takeRequest().path)
        assertEquals("Siti Rahayu, S.Pd.", result.data.teacher.fullName)
        assertNull(result.data.teacher.avatarUrl)
        assertEquals(24, result.data.classOverview.totalStudents)
        assertEquals(22, result.data.classOverview.presentStudents)
        assertEquals(Emotion.SENANG, result.data.classOverview.dominantMood)
        assertEquals(14, result.data.dailyMoodDistribution.senang)
        assertEquals(4, result.data.dailyMoodDistribution.bingung)
    }

    @Test
    fun `sends the class id when the teacher picks one`() = runBlocking {
        server.enqueue(jsonResponse(dashboardJson()))

        repository.dashboard("c1")

        assertEquals("/api/v1/guru/dashboard?classId=c1", server.takeRequest().path)
    }

    @Test
    fun `the dominant mood is null while no mood was recorded today`() = runBlocking {
        server.enqueue(jsonResponse(dashboardJson(dominantMood = "null")))

        val result = repository.dashboard() as ApiResult.Success

        assertNull(result.data.classOverview.dominantMood)
    }

    @Test
    fun `a parent asking for the teacher dashboard is forbidden`() = runBlocking {
        server.enqueue(jsonResponse(failureJson("AUTH_FORBIDDEN"), 403))

        val result = repository.dashboard() as ApiResult.Failure

        assertEquals(403, result.status)
        assertEquals("AUTH_FORBIDDEN", result.errorCode)
    }

    @Test
    fun `a reply without the class overview is a malformed response`() = runBlocking {
        server.enqueue(jsonResponse("""{"success":true,"data":{"teacher":{"fullName":"Siti","nip":null,"avatarUrl":null}}}"""))

        val result = repository.dashboard() as ApiResult.Failure

        assertEquals("MALFORMED_RESPONSE", result.errorCode)
    }

    @Test
    fun `offline is a network error`() = runBlocking {
        server.shutdown()

        assertTrue(repository.dashboard() is ApiResult.NetworkError)
    }
}
