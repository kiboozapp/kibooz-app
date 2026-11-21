package com.nthb.kibooz.data.repository

import com.nthb.kibooz.data.failureJson
import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.LeaderboardApi
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

class LeaderboardRepositoryTest {

    @get:Rule
    val timeout: Timeout = Timeout(30, TimeUnit.SECONDS)

    private lateinit var server: MockWebServer
    private lateinit var repository: LeaderboardRepository

    private val full = """{"success":true,"data":{
        "podium":[{"rank":1,"studentName":"Novita","points":90,"avatarUrl":null},
                  {"rank":2,"studentName":"Melarini","points":80,"avatarUrl":"https://cdn.example/a.jpg"},
                  {"rank":3,"studentName":"Amelia","points":70,"avatarUrl":null}],
        "rankings":[{"rank":4,"studentName":"Desi","points":60,"avatarUrl":null}]}}"""

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        val client = testClient(server)
        repository = LeaderboardRepository(client.create(LeaderboardApi::class.java), client.moshi)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `reads the podium and the rest with the token and no class by default`() = runBlocking {
        server.enqueue(jsonResponse(full))

        val result = repository.leaderboard() as ApiResult.Success

        val request = server.takeRequest()
        assertEquals("/api/v1/leaderboard", request.path)
        assertEquals("Bearer jwt", request.getHeader("Authorization"))
        assertEquals(listOf("Novita", "Melarini", "Amelia"), result.data.podium.map { it.studentName })
        assertEquals(4, result.data.rankings.single().rank)
        assertEquals("https://cdn.example/a.jpg", result.data.podium[1].avatarUrl)
        assertNull(result.data.podium[0].avatarUrl)
    }

    @Test
    fun `a chosen class is sent as classId`() = runBlocking {
        server.enqueue(jsonResponse(full))

        repository.leaderboard("c2")

        assertEquals("/api/v1/leaderboard?classId=c2", server.takeRequest().path)
    }

    @Test
    fun `a class with no children has an empty podium and list`() = runBlocking {
        server.enqueue(jsonResponse("""{"success":true,"data":{"podium":[],"rankings":[]}}"""))

        val result = repository.leaderboard() as ApiResult.Success

        assertEquals(0, result.data.podium.size + result.data.rankings.size)
    }

    @Test
    fun `a class that is not visible gets the error code`() = runBlocking {
        server.enqueue(jsonResponse(failureJson("CLASS_NOT_FOUND"), 404))

        assertEquals("CLASS_NOT_FOUND", (repository.leaderboard("x") as ApiResult.Failure).errorCode)
    }
}
