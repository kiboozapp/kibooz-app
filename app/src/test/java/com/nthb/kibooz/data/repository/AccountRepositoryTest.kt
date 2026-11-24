package com.nthb.kibooz.data.repository

import com.nthb.kibooz.data.failureJson
import com.nthb.kibooz.data.remote.AccountApi
import com.nthb.kibooz.data.remote.ApiResult
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout

class AccountRepositoryTest {

    @get:Rule
    val timeout: Timeout = Timeout(30, TimeUnit.SECONDS)

    private lateinit var server: MockWebServer
    private lateinit var repository: AccountRepository

    private val deleted = """{"success":true,"message":"Account deleted"}"""

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        val client = testClient(server)
        repository = AccountRepository(client.create(AccountApi::class.java), client.moshi)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `deletes the account with a DELETE that carries the password in the body`() = runBlocking {
        server.enqueue(jsonResponse(deleted))

        val result = repository.deleteAccount("secret-123")

        assertTrue(result is ApiResult.Success)
        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/api/v1/users/me", request.path)
        assertEquals("Bearer jwt", request.getHeader("Authorization"))
        assertTrue(request.getHeader("Content-Type").orEmpty().startsWith("application/json"))
        assertEquals("""{"password":"secret-123"}""", request.body.readUtf8())
    }

    @Test
    fun `a successor email is trimmed and sent next to the password`() = runBlocking {
        server.enqueue(jsonResponse(deleted))

        repository.deleteAccount("secret-123", "  budi@example.com ")

        assertEquals("""{"password":"secret-123","successorEmail":"budi@example.com"}""", server.takeRequest().body.readUtf8())
    }

    @Test
    fun `a blank successor email is left out of the body`() = runBlocking {
        server.enqueue(jsonResponse(deleted))

        repository.deleteAccount("secret-123", "   ")

        assertFalse(server.takeRequest().body.readUtf8().contains("successorEmail"))
    }

    @Test
    fun `the password is sent exactly as typed`() = runBlocking {
        server.enqueue(jsonResponse(deleted))

        repository.deleteAccount(" pass word ")

        assertEquals("""{"password":" pass word "}""", server.takeRequest().body.readUtf8())
    }

    @Test
    fun `every error code reaches the caller with its status`() = runBlocking {
        val cases = listOf(
            403 to "AUTH_PASSWORD_INCORRECT",
            403 to "AUTH_FORBIDDEN",
            404 to "PROFILE_NOT_FOUND",
            404 to "TEACHER_NOT_FOUND",
            409 to "SUCCESSOR_REQUIRED",
            400 to "VALIDATION_ERROR",
            401 to "AUTH_TOKEN_INVALID"
        )
        cases.forEach { (status, code) -> server.enqueue(jsonResponse(failureJson(code), status)) }

        cases.forEach { (status, code) ->
            val failure = repository.deleteAccount("secret-123") as ApiResult.Failure
            assertEquals(status, failure.status)
            assertEquals(code, failure.errorCode)
        }
    }

    @Test
    fun `validation details are kept so the screen can mark the field`() = runBlocking {
        server.enqueue(
            jsonResponse(
                """{"success":false,"message":"nope","errorCode":"VALIDATION_ERROR","details":{"successorEmail":"must be another teacher"}}""",
                400
            )
        )

        val failure = repository.deleteAccount("secret-123", "ani@example.com") as ApiResult.Failure

        assertEquals("must be another teacher", failure.details?.get("successorEmail"))
    }

    @Test
    fun `a 429 keeps the Retry-After seconds`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(429).setHeader("Retry-After", "1").setBody(failureJson("RATE_LIMITED"))
        )

        val failure = repository.deleteAccount("secret-123") as ApiResult.Failure

        assertEquals("RATE_LIMITED", failure.errorCode)
        assertEquals(1L, failure.retryAfterSeconds)
    }

    @Test
    fun `a 429 from a proxy has no code and no wait`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429))

        val failure = repository.deleteAccount("secret-123") as ApiResult.Failure

        assertEquals(429, failure.status)
        assertNull(failure.errorCode)
        assertNull(failure.retryAfterSeconds)
    }

    @Test
    fun `offline reports a network error`() = runBlocking {
        server.shutdown()

        assertTrue(repository.deleteAccount("secret-123") is ApiResult.NetworkError)
    }
}
