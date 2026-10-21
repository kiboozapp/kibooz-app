package com.nthb.kibooz.data.repository

import com.nthb.kibooz.data.failureJson
import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.ClassApi
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

class ClassRepositoryTest {

    @get:Rule
    val timeout: Timeout = Timeout(30, TimeUnit.SECONDS)

    private lateinit var server: MockWebServer
    private lateinit var repository: ClassRepository

    private fun student(id: String, mood: String = "null") = """
        {"id":"$id","fullName":"Siswa $id","nisn":"00$id","avatarUrl":null,"currentPoints":10,"seasonPoints":5,
         "classRank":1,"todayMood":$mood}
    """.trimIndent()

    private fun page(page: Int, limit: Int, total: Int, vararg students: String) =
        """{"success":true,"data":{"page":$page,"limit":$limit,"total":$total,"students":[${students.joinToString(",")}]}}"""

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        val client = testClient(server)
        repository = ClassRepository(client.create(ClassApi::class.java), client.moshi)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `lists the students of a class with the page and limit in the query`() = runBlocking {
        server.enqueue(jsonResponse(page(2, 10, 12, student("1", "\"SENANG\""), student("2"))))

        val result = repository.students("class-1", page = 2, limit = 10) as ApiResult.Success

        assertEquals("/api/v1/classes/class-1/students?page=2&limit=10", server.takeRequest().path)
        assertEquals(12, result.data.total)
        assertEquals(Emotion.SENANG, result.data.students[0].todayMood)
        assertNull(result.data.students[1].todayMood)
        assertNull(result.data.students[0].avatarUrl)
    }

    @Test
    fun `allStudents reads every page until the total is reached`() = runBlocking {
        server.enqueue(jsonResponse(page(1, 2, 3, student("1"), student("2"))))
        server.enqueue(jsonResponse(page(2, 2, 3, student("3"))))

        val result = repository.allStudents("class-1", pageSize = 2) as ApiResult.Success

        assertEquals(listOf("1", "2", "3"), result.data.map { it.id })
        assertEquals(2, server.requestCount)
        assertEquals("/api/v1/classes/class-1/students?page=1&limit=2", server.takeRequest().path)
        assertEquals("/api/v1/classes/class-1/students?page=2&limit=2", server.takeRequest().path)
    }

    @Test
    fun `allStudents needs one request for a class that fits one page`() = runBlocking {
        server.enqueue(jsonResponse(page(1, 50, 2, student("1"), student("2"))))

        val result = repository.allStudents("class-1") as ApiResult.Success

        assertEquals(2, result.data.size)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `allStudents stops on an empty page even if the total is larger`() = runBlocking {
        server.enqueue(jsonResponse(page(1, 2, 5)))

        val result = repository.allStudents("class-1", pageSize = 2) as ApiResult.Success

        assertTrue(result.data.isEmpty())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `allStudents returns the failure of a later page and no partial list`() = runBlocking {
        server.enqueue(jsonResponse(page(1, 2, 3, student("1"), student("2"))))
        server.enqueue(jsonResponse(failureJson("RATE_LIMITED"), 429).setHeader("Retry-After", "4"))

        val result = repository.allStudents("class-1", pageSize = 2) as ApiResult.Failure

        assertEquals("RATE_LIMITED", result.errorCode)
        assertEquals(4L, result.retryAfterSeconds)
    }

    @Test
    fun `a teacher who does not teach the class gets AUTH_FORBIDDEN`() = runBlocking {
        server.enqueue(jsonResponse(failureJson("AUTH_FORBIDDEN"), 403))

        val result = repository.students("other-class") as ApiResult.Failure

        assertEquals(403, result.status)
        assertEquals("AUTH_FORBIDDEN", result.errorCode)
    }

    @Test
    fun `lists the children of the parent`() = runBlocking {
        server.enqueue(
            jsonResponse(
                """{"success":true,"data":{"children":[
                    {"id":"s1","fullName":"Ani","nisn":"123","avatarUrl":"https://x/a.png","classId":"c1","className":"Bunga"},
                    {"id":"s2","fullName":"Budi","nisn":"456","avatarUrl":null,"classId":"c2","className":"Mawar"}]}}"""
            )
        )

        val result = repository.children() as ApiResult.Success

        assertEquals("/api/v1/wali/children", server.takeRequest().path)
        assertEquals(listOf("Ani", "Budi"), result.data.map { it.fullName })
        assertEquals("c2", result.data[1].classId)
        assertNull(result.data[1].avatarUrl)
    }

    @Test
    fun `a parent without children gets an empty list`() = runBlocking {
        server.enqueue(jsonResponse("""{"success":true,"data":{"children":[]}}"""))

        assertTrue((repository.children() as ApiResult.Success).data.isEmpty())
    }

    @Test
    fun `offline is a network error`() = runBlocking {
        server.shutdown()

        assertTrue(repository.children() is ApiResult.NetworkError)
        assertTrue(repository.allStudents("class-1") is ApiResult.NetworkError)
    }
}
