package com.nthb.kibooz.data.repository

import com.nthb.kibooz.data.failureJson
import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.ClassApi
import com.nthb.kibooz.data.remote.CreateClassRequest
import com.nthb.kibooz.data.remote.GuruClassApi
import com.squareup.moshi.Moshi
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout

class ClassManagementRepositoryTest {

    @get:Rule
    val timeout: Timeout = Timeout(30, TimeUnit.SECONDS)

    private lateinit var server: MockWebServer
    private lateinit var repository: ClassManagementRepository

    private fun classJson(id: String = "c1", name: String = "Bunga", joinCode: String = "ABC23456", open: Boolean = true, archivedAt: String? = null) = """
        {"id":"$id","name":"$name","gradeLevel":"Class A","schoolName":"SD Kibooz","academicYear":"2026/2027",
         "joinCode":"$joinCode","joinOpen":$open,"totalStudents":22,"createdAt":"2026-07-01T00:00:00Z"
         ${archivedAt?.let { ""","archivedAt":"$it"""" } ?: ""}}
    """.trimIndent()

    private fun classEnvelope(json: String = classJson()) = """{"success":true,"data":$json}"""

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        val client = testClient(server)
        val classes = ClassRepository(client.create(ClassApi::class.java), client.moshi)
        repository = ClassManagementRepository(classes, client.create(GuruClassApi::class.java), client.moshi)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun failure(status: Int, code: String) = jsonResponse(failureJson(code), status)

    /** Isi JSON request sebagai map, supaya tes bisa memastikan field yang kosong tidak dikirim. */
    @Suppress("UNCHECKED_CAST")
    private fun bodyOf(request: RecordedRequest): Map<String, Any?> =
        Moshi.Builder().build().adapter(Map::class.java).fromJson(request.body.readUtf8()) as Map<String, Any?>

    @Test
    fun `the list reads archivedAt and leaves it null for an active class`() = runBlocking {
        server.enqueue(
            jsonResponse(
                """{"success":true,"data":{"page":1,"limit":50,"total":2,"classes":[
                    ${classJson("c1")},${classJson("c2", archivedAt = "2026-09-01T00:00:00Z")}]}}"""
            )
        )

        val result = repository.list() as ApiResult.Success

        assertEquals("/api/v1/guru/classes?page=1&limit=50", server.takeRequest().path)
        assertNull(result.data[0].archivedAt)
        assertFalse(result.data[0].archived)
        assertEquals("2026-09-01T00:00:00Z", result.data[1].archivedAt)
        assertTrue(result.data[1].archived)
    }

    // POST /guru/classes

    @Test
    fun `create posts the name only when the other fields are empty and sends the idempotency key`() = runBlocking {
        server.enqueue(jsonResponse(classEnvelope(), 201))
        val pending = PendingClass(CreateClassRequest("Bunga"))

        val result = repository.create(pending) as ApiResult.Success

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/guru/classes", request.path)
        assertEquals(pending.idempotencyKey, request.getHeader("Idempotency-Key"))
        val body = bodyOf(request)
        assertEquals("Bunga", body["name"])
        assertFalse(body.containsKey("gradeLevel"))
        assertFalse(body.containsKey("academicYear"))
        assertFalse(body.containsKey("schoolName"))
        assertEquals("ABC23456", result.data.joinCode)
        assertEquals(22, result.data.totalStudents)
    }

    @Test
    fun `create sends the optional fields that were given`() = runBlocking {
        server.enqueue(jsonResponse(classEnvelope(), 201))

        repository.create(PendingClass(CreateClassRequest("Bunga", gradeLevel = "Class B", academicYear = "2027/2028")))

        val body = bodyOf(server.takeRequest())
        assertEquals("Class B", body["gradeLevel"])
        assertEquals("2027/2028", body["academicYear"])
        assertFalse(body.containsKey("schoolName"))
    }

    @Test
    fun `sending the same pending class again reuses the key and a new one gets a new key`() = runBlocking {
        server.enqueue(jsonResponse(classEnvelope(), 201))
        server.enqueue(jsonResponse(classEnvelope(), 201))
        server.enqueue(jsonResponse(classEnvelope(), 201))
        val pending = PendingClass(CreateClassRequest("Bunga"))

        repository.create(pending)
        repository.create(pending)
        repository.create(PendingClass(CreateClassRequest("Bunga")))

        val keys = List(3) { server.takeRequest().getHeader("Idempotency-Key") }
        assertEquals(keys[0], keys[1])
        assertNotEquals(keys[0], keys[2])
    }

    @Test
    fun `create answers CLASS_LIMIT_REACHED at 20 active classes`() = runBlocking {
        server.enqueue(failure(409, "CLASS_LIMIT_REACHED"))

        val result = repository.create(PendingClass(CreateClassRequest("Bunga"))) as ApiResult.Failure

        assertEquals(409, result.status)
        assertEquals("CLASS_LIMIT_REACHED", result.errorCode)
    }

    @Test
    fun `create returns the field details of a validation error`() = runBlocking {
        server.enqueue(
            jsonResponse(
                """{"success":false,"message":"x","errorCode":"VALIDATION_ERROR","details":{"name":"too long"}}""",
                400
            )
        )

        val result = repository.create(PendingClass(CreateClassRequest("x".repeat(51)))) as ApiResult.Failure

        assertEquals("VALIDATION_ERROR", result.errorCode)
        assertEquals("too long", result.details?.get("name"))
    }

    @Test
    fun `create returns the idempotency conflicts`() = runBlocking {
        server.enqueue(failure(409, "IDEMPOTENCY_IN_PROGRESS"))
        server.enqueue(failure(422, "IDEMPOTENCY_KEY_REUSED"))

        val first = repository.create(PendingClass(CreateClassRequest("Bunga"))) as ApiResult.Failure
        val second = repository.create(PendingClass(CreateClassRequest("Bunga"))) as ApiResult.Failure

        assertEquals("IDEMPOTENCY_IN_PROGRESS", first.errorCode)
        assertEquals(422, second.status)
        assertEquals("IDEMPOTENCY_KEY_REUSED", second.errorCode)
    }

    @Test
    fun `create by a parent account is forbidden and a rate limit carries Retry-After`() = runBlocking {
        server.enqueue(failure(403, "AUTH_FORBIDDEN"))
        server.enqueue(failure(429, "RATE_LIMITED").setHeader("Retry-After", "7"))

        val forbidden = repository.create(PendingClass(CreateClassRequest("Bunga"))) as ApiResult.Failure
        val limited = repository.create(PendingClass(CreateClassRequest("Bunga"))) as ApiResult.Failure

        assertEquals("AUTH_FORBIDDEN", forbidden.errorCode)
        assertEquals("RATE_LIMITED", limited.errorCode)
        assertEquals(7L, limited.retryAfterSeconds)
    }

    // POST /guru/classes/{classId}/join-code

    @Test
    fun `rotateJoinCode posts to the join code route and returns the new code`() = runBlocking {
        server.enqueue(jsonResponse("""{"success":true,"data":{"joinCode":"K7M2QX4R"}}"""))

        val result = repository.rotateJoinCode("c1") as ApiResult.Success

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/guru/classes/c1/join-code", request.path)
        assertEquals("K7M2QX4R", result.data)
    }

    @Test
    fun `rotateJoinCode on an archived class answers CLASS_ARCHIVED and on an unknown class CLASS_NOT_FOUND`() = runBlocking {
        server.enqueue(failure(409, "CLASS_ARCHIVED"))
        server.enqueue(failure(404, "CLASS_NOT_FOUND"))

        val archived = repository.rotateJoinCode("c1") as ApiResult.Failure
        val missing = repository.rotateJoinCode("c9") as ApiResult.Failure

        assertEquals("CLASS_ARCHIVED", archived.errorCode)
        assertEquals(404, missing.status)
        assertEquals("CLASS_NOT_FOUND", missing.errorCode)
    }

    // PUT /guru/classes/{classId}/joining

    @Test
    fun `setJoining puts joinOpen false and returns the value now in force`() = runBlocking {
        server.enqueue(jsonResponse("""{"success":true,"data":{"joinOpen":false}}"""))

        val result = repository.setJoining("c1", open = false) as ApiResult.Success

        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/api/v1/guru/classes/c1/joining", request.path)
        assertEquals(false, bodyOf(request)["joinOpen"])
        assertFalse(result.data)
    }

    @Test
    fun `setJoining puts joinOpen true to open the class again`() = runBlocking {
        server.enqueue(jsonResponse("""{"success":true,"data":{"joinOpen":true}}"""))

        val result = repository.setJoining("c1", open = true) as ApiResult.Success

        assertEquals(true, bodyOf(server.takeRequest())["joinOpen"])
        assertTrue(result.data)
    }

    @Test
    fun `setJoining on an archived class answers CLASS_ARCHIVED and on an unknown class CLASS_NOT_FOUND`() = runBlocking {
        server.enqueue(failure(409, "CLASS_ARCHIVED"))
        server.enqueue(failure(404, "CLASS_NOT_FOUND"))

        assertEquals("CLASS_ARCHIVED", (repository.setJoining("c1", true) as ApiResult.Failure).errorCode)
        assertEquals("CLASS_NOT_FOUND", (repository.setJoining("c9", true) as ApiResult.Failure).errorCode)
    }

    // POST /guru/classes/{classId}/archive

    @Test
    fun `archive posts to the archive route and returns the class with archivedAt`() = runBlocking {
        server.enqueue(jsonResponse(classEnvelope(classJson(archivedAt = "2026-09-01T00:00:00Z"))))

        val result = repository.archive("c1") as ApiResult.Success

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/guru/classes/c1/archive", request.path)
        assertTrue(result.data.archived)
    }

    @Test
    fun `archive answers the owner, missing class and already archived errors`() = runBlocking {
        server.enqueue(failure(403, "CLASS_OWNER_REQUIRED"))
        server.enqueue(failure(404, "CLASS_NOT_FOUND"))
        server.enqueue(failure(409, "CLASS_ARCHIVED"))

        val notOwner = repository.archive("c1") as ApiResult.Failure
        val missing = repository.archive("c9") as ApiResult.Failure
        val twice = repository.archive("c2") as ApiResult.Failure

        assertEquals(403, notOwner.status)
        assertEquals("CLASS_OWNER_REQUIRED", notOwner.errorCode)
        assertEquals("CLASS_NOT_FOUND", missing.errorCode)
        assertEquals(409, twice.status)
        assertEquals("CLASS_ARCHIVED", twice.errorCode)
    }

    // POST /guru/classes/{classId}/restore

    @Test
    fun `restore posts to the restore route and returns the class with its new code`() = runBlocking {
        server.enqueue(jsonResponse(classEnvelope(classJson(joinCode = "NEWCODE9"))))

        val result = repository.restore("c1") as ApiResult.Success

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/guru/classes/c1/restore", request.path)
        assertEquals("NEWCODE9", result.data.joinCode)
        assertFalse(result.data.archived)
    }

    @Test
    fun `restore answers not archived, the class limit and a missing class`() = runBlocking {
        server.enqueue(failure(409, "CLASS_NOT_ARCHIVED"))
        server.enqueue(failure(409, "CLASS_LIMIT_REACHED"))
        server.enqueue(failure(404, "CLASS_NOT_FOUND"))

        assertEquals("CLASS_NOT_ARCHIVED", (repository.restore("c1") as ApiResult.Failure).errorCode)
        assertEquals("CLASS_LIMIT_REACHED", (repository.restore("c2") as ApiResult.Failure).errorCode)
        assertEquals("CLASS_NOT_FOUND", (repository.restore("c9") as ApiResult.Failure).errorCode)
    }

    @Test
    fun `offline is a network error for every call`() = runBlocking {
        server.shutdown()

        assertTrue(repository.list() is ApiResult.NetworkError)
        assertTrue(repository.create(PendingClass(CreateClassRequest("Bunga"))) is ApiResult.NetworkError)
        assertTrue(repository.rotateJoinCode("c1") is ApiResult.NetworkError)
        assertTrue(repository.setJoining("c1", true) is ApiResult.NetworkError)
        assertTrue(repository.archive("c1") is ApiResult.NetworkError)
        assertTrue(repository.restore("c1") is ApiResult.NetworkError)
    }
}
