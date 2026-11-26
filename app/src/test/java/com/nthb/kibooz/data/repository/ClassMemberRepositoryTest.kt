package com.nthb.kibooz.data.repository

import com.nthb.kibooz.data.failureJson
import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.ClassMemberApi
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout

class ClassMemberRepositoryTest {

    @get:Rule
    val timeout: Timeout = Timeout(30, TimeUnit.SECONDS)

    private lateinit var server: MockWebServer
    private lateinit var repository: ClassMemberRepository

    private fun teacherJson(id: String = "t1", isSelf: Boolean = false, isOwner: Boolean = false) = """
        {"id":"$id","userId":"u-$id","isSelf":$isSelf,"fullName":"Siti $id","nip":null,"avatarUrl":null,
         "isOwner":$isOwner,"joinedAt":"2026-07-01T00:00:00Z"}
    """.trimIndent()

    private val emptyOk = """{"success":true,"message":"ok"}"""

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        val client = testClient(server)
        repository = ClassMemberRepository(client.create(ClassMemberApi::class.java), client.moshi)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun failure(status: Int, code: String) = jsonResponse(failureJson(code), status)

    private fun codeOf(result: ApiResult<*>) = (result as ApiResult.Failure).errorCode

    @Test
    fun `teachers reads the list with the owner and the signed in row`() = runBlocking {
        server.enqueue(
            jsonResponse(
                """{"success":true,"data":{"teachers":[
                    ${teacherJson("t1", isSelf = true, isOwner = true)},${teacherJson("t2")}]}}"""
            )
        )

        val result = repository.teachers("c1") as ApiResult.Success

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/v1/guru/classes/c1/teachers", request.path)
        assertEquals(listOf("t1", "t2"), result.data.map { it.id })
        assertTrue(result.data[0].isSelf)
        assertTrue(result.data[0].isOwner)
        assertNull(result.data[1].nip)
    }

    @Test
    fun `teachers returns a class that is gone and a forbidden role`() = runBlocking {
        server.enqueue(failure(404, "CLASS_NOT_FOUND"))
        server.enqueue(failure(403, "AUTH_FORBIDDEN"))

        assertEquals("CLASS_NOT_FOUND", codeOf(repository.teachers("c9")))
        assertEquals("AUTH_FORBIDDEN", codeOf(repository.teachers("c1")))
    }

    @Test
    fun `addTeacher posts the email with the idempotency key of the pending teacher`() = runBlocking {
        server.enqueue(jsonResponse("""{"success":true,"data":${teacherJson("t2")}}""", 201))
        server.enqueue(jsonResponse("""{"success":true,"data":${teacherJson("t2")}}""", 201))
        val pending = PendingTeacher("guru@example.org")

        val result = repository.addTeacher("c1", pending) as ApiResult.Success
        repository.addTeacher("c1", pending)

        val first = server.takeRequest()
        assertEquals("POST", first.method)
        assertEquals("/api/v1/guru/classes/c1/teachers", first.path)
        assertEquals("""{"email":"guru@example.org"}""", first.body.readUtf8())
        assertEquals(pending.idempotencyKey, first.getHeader("Idempotency-Key"))
        assertEquals(pending.idempotencyKey, server.takeRequest().getHeader("Idempotency-Key"))
        assertEquals("t2", result.data.id)
        assertNotEquals(pending.idempotencyKey, PendingTeacher("guru@example.org").idempotencyKey)
    }

    @Test
    fun `addTeacher returns every documented error code`() = runBlocking {
        val cases = listOf(
            400 to "VALIDATION_ERROR",
            403 to "AUTH_FORBIDDEN",
            404 to "CLASS_NOT_FOUND",
            404 to "TEACHER_NOT_FOUND",
            409 to "TEACHER_ALREADY_IN_CLASS",
            409 to "CLASS_LIMIT_REACHED",
            409 to "IDEMPOTENCY_IN_PROGRESS",
            409 to "CLASS_ARCHIVED",
            422 to "IDEMPOTENCY_KEY_REUSED"
        )

        for ((status, code) in cases) {
            server.enqueue(failure(status, code))
            val result = repository.addTeacher("c1", PendingTeacher("guru@example.org")) as ApiResult.Failure
            assertEquals(status, result.status)
            assertEquals(code, result.errorCode)
        }
    }

    @Test
    fun `a rate limit carries Retry-After`() = runBlocking {
        server.enqueue(failure(429, "RATE_LIMITED").setHeader("Retry-After", "7"))

        val result = repository.addTeacher("c1", PendingTeacher("guru@example.org")) as ApiResult.Failure

        assertEquals("RATE_LIMITED", result.errorCode)
        assertEquals(7L, result.retryAfterSeconds)
    }

    @Test
    fun `removeTeacher deletes the teacher of the class`() = runBlocking {
        server.enqueue(jsonResponse(emptyOk))

        val result = repository.removeTeacher("c1", "t2")

        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/api/v1/guru/classes/c1/teachers/t2", request.path)
        assertTrue(result is ApiResult.Success)
    }

    @Test
    fun `removeTeacher returns every documented error code`() = runBlocking {
        val codes = listOf(
            "AUTH_FORBIDDEN", "CLASS_OWNER_REQUIRED", "CLASS_NOT_FOUND", "TEACHER_NOT_IN_CLASS", "LAST_TEACHER",
            "CLASS_ARCHIVED"
        )

        for (code in codes) {
            server.enqueue(failure(409, code))
            assertEquals(code, codeOf(repository.removeTeacher("c1", "t2")))
        }
    }

    @Test
    fun `transferOwnership puts the teacher id and returns the new owner`() = runBlocking {
        server.enqueue(jsonResponse("""{"success":true,"data":${teacherJson("t2", isOwner = true)}}"""))

        val result = repository.transferOwnership("c1", "t2") as ApiResult.Success

        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/api/v1/guru/classes/c1/owner", request.path)
        assertEquals("""{"teacherId":"t2"}""", request.body.readUtf8())
        assertTrue(result.data.isOwner)
    }

    @Test
    fun `transferOwnership returns every documented error code`() = runBlocking {
        val codes = listOf(
            "AUTH_FORBIDDEN", "CLASS_OWNER_REQUIRED", "CLASS_NOT_FOUND", "TEACHER_NOT_IN_CLASS", "CLASS_ARCHIVED"
        )

        for (code in codes) {
            server.enqueue(failure(409, code))
            assertEquals(code, codeOf(repository.transferOwnership("c1", "t2")))
        }
    }

    @Test
    fun `removeStudent deletes the child of the class`() = runBlocking {
        server.enqueue(jsonResponse(emptyOk))

        val result = repository.removeStudent("c1", "s1")

        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/api/v1/guru/classes/c1/students/s1", request.path)
        assertTrue(result is ApiResult.Success)
    }

    @Test
    fun `removeStudent returns every documented error code`() = runBlocking {
        val codes = listOf(
            "VALIDATION_ERROR", "AUTH_FORBIDDEN", "CLASS_NOT_FOUND", "STUDENT_NOT_FOUND", "CLASS_ARCHIVED", "RATE_LIMITED"
        )

        for (code in codes) {
            server.enqueue(failure(409, code))
            assertEquals(code, codeOf(repository.removeStudent("c1", "s1")))
        }
    }

    @Test
    fun `a proxy answer without a JSON body has no error code`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(502).setBody("<html>Bad Gateway</html>"))

        val result = repository.removeStudent("c1", "s1") as ApiResult.Failure

        assertEquals(502, result.status)
        assertNull(result.errorCode)
    }

    @Test
    fun `offline is a network error for every call`() = runBlocking {
        server.shutdown()

        assertTrue(repository.teachers("c1") is ApiResult.NetworkError)
        assertTrue(repository.addTeacher("c1", PendingTeacher("guru@example.org")) is ApiResult.NetworkError)
        assertTrue(repository.removeTeacher("c1", "t2") is ApiResult.NetworkError)
        assertTrue(repository.transferOwnership("c1", "t2") is ApiResult.NetworkError)
        assertTrue(repository.removeStudent("c1", "s1") is ApiResult.NetworkError)
    }
}
