package com.nthb.kibooz.data.repository

import com.nthb.kibooz.data.failureJson
import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.WaliProfileApi
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout

class WaliProfileRepositoryTest {

    @get:Rule
    val timeout: Timeout = Timeout(30, TimeUnit.SECONDS)

    private lateinit var server: MockWebServer
    private lateinit var repository: WaliProfileRepository

    private val summary = """{"id":"s1","fullName":"Amelia","nisn":"0012345678","nisnVerified":true,"nisnNeedsUpdate":false,
        "avatarUrl":null,"classId":"c1","className":"Bunga"}"""

    private val profile = """{"success":true,"data":{"id":"s1","fullName":"Amelia","nisn":"0012345678","nisnVerified":false,
        "nisnNeedsUpdate":false,"avatarUrl":null,"classId":"c1","className":"Bunga","gradeLevel":"TK A","schoolName":"TK Pertiwi",
        "academicYear":"2026/2027","currentPoints":120,"seasonPoints":40,"classRank":3,
        "guardian":{"fullName":"Citra","email":"citra@example.org","phoneNumber":null,"whatsappNumber":"+62 813 0000 1111","address":null}}}"""

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        val client = testClient(server)
        repository = WaliProfileRepository(client.create(WaliProfileApi::class.java), client.moshi)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `lists the children with the verification flags and the archive time`() = runBlocking {
        val archived = summary.replace("\"className\":\"Bunga\"", "\"className\":\"Bunga\",\"classArchivedAt\":\"2026-11-01T00:00:00Z\"")
        server.enqueue(jsonResponse("""{"success":true,"data":{"children":[$summary,$archived]}}"""))

        val result = repository.children() as ApiResult.Success

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/v1/wali/children", request.path)
        assertEquals("Bearer jwt", request.getHeader("Authorization"))
        assertEquals(2, result.data.size)
        assertTrue(result.data[0].nisnVerified)
        assertNull(result.data[0].classArchivedAt)
        assertEquals("2026-11-01T00:00:00Z", result.data[1].classArchivedAt)
    }

    @Test
    fun `a child without the flags reads them as false`() = runBlocking {
        server.enqueue(jsonResponse("""{"success":true,"data":{"children":[{"id":"s1","fullName":"A","nisn":"1","avatarUrl":null,"classId":"c","className":"B"}]}}"""))

        val child = (repository.children() as ApiResult.Success).data.single()

        assertFalse(child.nisnVerified)
        assertFalse(child.nisnNeedsUpdate)
    }

    @Test
    fun `reads one child with the contact details of the parent`() = runBlocking {
        server.enqueue(jsonResponse(profile))

        val result = repository.child("s1") as ApiResult.Success

        assertEquals("/api/v1/wali/child/s1", server.takeRequest().path)
        assertEquals("Amelia", result.data.fullName)
        assertEquals("TK Pertiwi", result.data.schoolName)
        assertEquals(3, result.data.classRank)
        assertEquals("Citra", result.data.guardian.fullName)
        assertEquals("citra@example.org", result.data.guardian.email)
        assertNull(result.data.guardian.phoneNumber)
        assertEquals("+62 813 0000 1111", result.data.guardian.whatsappNumber)
    }

    @Test
    fun `an unknown child is the not found code`() = runBlocking {
        server.enqueue(jsonResponse(failureJson("STUDENT_NOT_FOUND"), 404))

        val result = repository.child("gone") as ApiResult.Failure

        assertEquals("STUDENT_NOT_FOUND", result.errorCode)
        assertEquals(404, result.status)
    }

    @Test
    fun `adding a child sends the key, the upper case code and the trimmed child`() = runBlocking {
        server.enqueue(jsonResponse("""{"success":true,"data":$summary}""", 201))
        val add = PendingAddChild(" k7m2qx4r ", " 0012345678 ", "  Amelia Zahra ")

        val result = repository.addChild(add) as ApiResult.Success

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/wali/children", request.path)
        assertEquals(add.idempotencyKey, request.getHeader("Idempotency-Key"))
        assertEquals(
            """{"classCode":"K7M2QX4R","student":{"nisn":"0012345678","fullName":"Amelia Zahra"}}""",
            request.body.readUtf8()
        )
        assertEquals("s1", result.data.id)
    }

    @Test
    fun `retrying the same add sends the same key and a new add a new one`() = runBlocking {
        server.enqueue(jsonResponse(failureJson("INTERNAL_ERROR"), 500))
        server.enqueue(jsonResponse("""{"success":true,"data":$summary}""", 201))
        val add = PendingAddChild("K7M2QX4R", "0012345678", "Amelia")

        repository.addChild(add)
        repository.addChild(add)

        val first = server.takeRequest().getHeader("Idempotency-Key")
        assertEquals(first, server.takeRequest().getHeader("Idempotency-Key"))
        assertFalse(first == PendingAddChild("K7M2QX4R", "0012345678", "Amelia").idempotencyKey)
    }

    @Test
    fun `each add error code of the contract reaches the caller`() = runBlocking {
        val cases = listOf(
            "CLASS_CODE_NOT_FOUND" to 404,
            "NISN_ALREADY_REGISTERED" to 409,
            "CLASS_NO_ACTIVE_TEACHER" to 409,
            "CLASS_JOINING_CLOSED" to 409,
            "CLASS_FULL" to 409,
            "CHILD_LIMIT_REACHED" to 409,
            "IDEMPOTENCY_IN_PROGRESS" to 409,
            "IDEMPOTENCY_KEY_REUSED" to 422,
            "RATE_LIMITED" to 429
        )
        cases.forEach { (code, status) -> server.enqueue(jsonResponse(failureJson(code), status)) }

        cases.forEach { (code, status) ->
            val result = repository.addChild(PendingAddChild("K7M2QX4R", "0012345678", "Amelia")) as ApiResult.Failure

            assertEquals(code, result.errorCode)
            assertEquals(status, result.status)
        }
    }

    @Test
    fun `a validation error keeps the field details`() = runBlocking {
        server.enqueue(
            jsonResponse(
                """{"success":false,"message":"x","errorCode":"VALIDATION_ERROR","details":{"student.nisn":"bad","classCode":"bad"}}""",
                400
            )
        )

        val result = repository.addChild(PendingAddChild("K7M2QX4R", "1", "Amelia")) as ApiResult.Failure

        assertEquals("VALIDATION_ERROR", result.errorCode)
        assertEquals(setOf("student.nisn", "classCode"), result.details?.keys)
    }

    @Test
    fun `renaming a child sends only the name`() = runBlocking {
        server.enqueue(jsonResponse("""{"success":true,"data":$summary}"""))

        val result = repository.updateChild("s1", "  Amelia Baru ", null) as ApiResult.Success

        val request = server.takeRequest()
        assertEquals("PATCH", request.method)
        assertEquals("/api/v1/wali/children/s1", request.path)
        assertEquals("""{"fullName":"Amelia Baru"}""", request.body.readUtf8())
        assertEquals("s1", result.data.id)
    }

    @Test
    fun `changing the NISN sends only the NISN and both fields can go together`() = runBlocking {
        server.enqueue(jsonResponse("""{"success":true,"data":$summary}"""))
        server.enqueue(jsonResponse("""{"success":true,"data":$summary}"""))

        repository.updateChild("s1", null, " 0099887766 ")
        repository.updateChild("s1", "Amelia", "0099887766")

        assertEquals("""{"nisn":"0099887766"}""", server.takeRequest().body.readUtf8())
        assertEquals("""{"fullName":"Amelia","nisn":"0099887766"}""", server.takeRequest().body.readUtf8())
    }

    @Test
    fun `changing a child answers the codes of the contract`() = runBlocking {
        server.enqueue(jsonResponse(failureJson("NISN_ALREADY_REGISTERED"), 409))
        server.enqueue(jsonResponse(failureJson("STUDENT_NOT_FOUND"), 404))
        server.enqueue(jsonResponse(failureJson("VALIDATION_ERROR"), 400))

        assertEquals("NISN_ALREADY_REGISTERED", (repository.updateChild("s1", null, "0099887766") as ApiResult.Failure).errorCode)
        assertEquals("STUDENT_NOT_FOUND", (repository.updateChild("s1", "Amelia", null) as ApiResult.Failure).errorCode)
        assertEquals("VALIDATION_ERROR", (repository.updateChild("s1", "A", null) as ApiResult.Failure).errorCode)
    }

    @Test
    fun `deleting a child is a DELETE on its path and has no data`() = runBlocking {
        server.enqueue(jsonResponse("""{"success":true,"message":"Child deleted"}"""))

        val result = repository.deleteChild("s1")

        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/api/v1/wali/children/s1", request.path)
        assertTrue(result is ApiResult.Success)
    }

    @Test
    fun `deleting a child that is already gone is the not found code`() = runBlocking {
        server.enqueue(jsonResponse(failureJson("STUDENT_NOT_FOUND"), 404))

        assertEquals("STUDENT_NOT_FOUND", (repository.deleteChild("s1") as ApiResult.Failure).errorCode)
    }

    @Test
    fun `changing the profile sends only the changed fields and an empty text clears one`() = runBlocking {
        server.enqueue(jsonResponse("""{"success":true,"data":{"fullName":"Citra Baru","email":"citra@example.org","phoneNumber":null,"whatsappNumber":null,"address":null}}"""))

        val result = repository.updateProfile(fullName = " Citra Baru ", whatsappNumber = "   ", address = null) as ApiResult.Success

        val request = server.takeRequest()
        assertEquals("PATCH", request.method)
        assertEquals("/api/v1/wali/profile", request.path)
        assertEquals("""{"fullName":"Citra Baru","whatsappNumber":""}""", request.body.readUtf8())
        assertEquals("Citra Baru", result.data.fullName)
        assertNull(result.data.whatsappNumber)
    }

    @Test
    fun `the profile can send the phone and the address`() = runBlocking {
        server.enqueue(jsonResponse("""{"success":true,"data":{"fullName":"Citra","email":"c@example.org","phoneNumber":"0812 3456","whatsappNumber":null,"address":"Jl. Contoh 1"}}"""))

        repository.updateProfile(phoneNumber = "0812 3456", address = " Jl. Contoh 1 ")

        assertEquals("""{"phoneNumber":"0812 3456","address":"Jl. Contoh 1"}""", server.takeRequest().body.readUtf8())
    }

    @Test
    fun `a profile validation error names the field`() = runBlocking {
        server.enqueue(
            jsonResponse(
                """{"success":false,"message":"x","errorCode":"VALIDATION_ERROR","details":{"phoneNumber":"must be a valid phone number"}}""",
                400
            )
        )

        val result = repository.updateProfile(phoneNumber = "12") as ApiResult.Failure

        assertEquals(setOf("phoneNumber"), result.details?.keys)
    }

    @Test
    fun `a parent without a profile row is the profile not found code`() = runBlocking {
        server.enqueue(jsonResponse(failureJson("PROFILE_NOT_FOUND"), 404))

        assertEquals("PROFILE_NOT_FOUND", (repository.updateProfile(fullName = "Citra") as ApiResult.Failure).errorCode)
    }

    @Test
    fun `no answer is a network error`() = runBlocking {
        server.enqueue(jsonResponse("{}").setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        val result = repository.children()

        assertTrue(result is ApiResult.NetworkError)
        assertTrue((result as ApiResult.NetworkError).cause is IOException)
    }
}
