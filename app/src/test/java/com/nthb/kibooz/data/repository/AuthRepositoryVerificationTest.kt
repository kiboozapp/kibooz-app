package com.nthb.kibooz.data.repository

import com.nthb.kibooz.data.InMemorySessionStore
import com.nthb.kibooz.data.authJson
import com.nthb.kibooz.data.failureJson
import com.nthb.kibooz.data.registerPendingJson
import com.nthb.kibooz.data.remote.ApiClient
import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.AuthApi
import com.nthb.kibooz.data.remote.ERROR_MALFORMED_RESPONSE
import com.nthb.kibooz.data.remote.RegisterRequest
import com.nthb.kibooz.data.resendJson
import com.nthb.kibooz.data.session.Role
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.logging.HttpLoggingInterceptor
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

/** Verifikasi email akun baru: register dengan dan tanpa verifikasi, kirim kode, kirim ulang kode, dan login 403. */
class AuthRepositoryVerificationTest {

    @get:Rule
    val timeout: Timeout = Timeout(30, TimeUnit.SECONDS)

    private lateinit var server: MockWebServer
    private lateinit var store: InMemorySessionStore
    private lateinit var repository: AuthRepository

    private val request = RegisterRequest(
        email = " Ani@Example.com ",
        password = "rahasia123",
        fullName = "Ani",
        role = Role.WALI,
        classCode = "ABC123"
    )

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        store = InMemorySessionStore()
        val client = ApiClient(
            tokenProvider = { null },
            baseUrl = server.url("/api/v1/").toString(),
            logLevel = HttpLoggingInterceptor.Level.NONE
        )
        repository = AuthRepository(client.create(AuthApi::class.java), store, client.moshi)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun json(body: String, code: Int = 200) = MockResponse().setResponseCode(code).setBody(body)

    @Test
    fun `register with the switch off signs in and stores the session as before`() = runBlocking {
        server.enqueue(json(authJson("access-1", "refresh-1", deviceToken = "device-1"), 201))

        val result = repository.register(request) as ApiResult.Success

        val outcome = result.data as RegisterOutcome.SignedIn
        assertEquals("ani@example.com", outcome.user.email)
        assertEquals("access-1", store.session?.accessToken)
        assertEquals("refresh-1", store.session?.refreshToken)
        assertEquals("device-1", store.readDeviceToken("ani@example.com"))
        assertEquals("access-1", repository.session.value?.accessToken)
    }

    @Test
    fun `register that needs a code stores no session and returns what the code screen needs`() = runBlocking {
        server.enqueue(json(registerPendingJson(sent = true, resendAfter = 60, deviceToken = "device-9"), 201))

        val result = repository.register(request) as ApiResult.Success

        val outcome = result.data as RegisterOutcome.VerificationRequired
        assertEquals("Ani@Example.com", outcome.email)
        assertEquals("device-9", outcome.deviceToken)
        assertTrue(outcome.verification.sent)
        assertEquals(60, outcome.verification.resendAfterSeconds)
        assertEquals(900, outcome.verification.expiresInSeconds)
        assertNull(store.session)
        assertNull(repository.session.value)
        assertNull(repository.accessToken())
        assertNull(store.readDeviceToken("ani@example.com"))
    }

    @Test
    fun `register that needs a code reports a mail that did not go out`() = runBlocking {
        server.enqueue(json(registerPendingJson(sent = false, resendAfter = 30), 201))

        val outcome = (repository.register(request) as ApiResult.Success).data as RegisterOutcome.VerificationRequired

        assertFalse(outcome.verification.sent)
        assertEquals(30, outcome.verification.resendAfterSeconds)
        assertNull(store.session)
    }

    @Test
    fun `register with empty tokens and no verification note is a malformed answer and stores nothing`() = runBlocking {
        server.enqueue(json(authJson("", ""), 201))

        val result = repository.register(request) as ApiResult.Failure

        assertEquals(ERROR_MALFORMED_RESPONSE, result.errorCode)
        assertNull(store.session)
        assertNull(repository.session.value)
    }

    @Test
    fun `verify email sends the password and the code as text and signs in like a login`() = runBlocking {
        server.enqueue(json(authJson("access-2", "refresh-2", deviceToken = "device-2")))

        val result = repository.verifyEmail(" Ani@Example.com ", "rahasia123", "012345") as ApiResult.Success

        val sent = server.takeRequest()
        val body = sent.body.readUtf8()
        assertEquals("POST", sent.method)
        assertEquals("/api/v1/auth/verify-email", sent.path)
        assertTrue(body, body.contains("\"email\":\"Ani@Example.com\""))
        assertTrue(body, body.contains("\"password\":\"rahasia123\""))
        assertTrue("the leading zero must survive", body.contains("\"code\":\"012345\""))
        assertFalse("no device token is known yet", body.contains("deviceToken"))
        assertEquals("ani@example.com", result.data.email)
        assertEquals("access-2", store.session?.accessToken)
        assertEquals("refresh-2", store.session?.refreshToken)
        assertEquals("device-2", store.readDeviceToken("ani@example.com"))
        assertEquals("access-2", repository.session.value?.accessToken)
    }

    @Test
    fun `verify email sends the device token of the registration, or the saved one for that email`() = runBlocking {
        server.enqueue(json(authJson("a", "r")))
        repository.verifyEmail("ani@example.com", "rahasia123", "123456", deviceToken = "from-register")
        assertTrue(server.takeRequest().body.readUtf8().contains("\"deviceToken\":\"from-register\""))

        store.writeDeviceToken("ani@example.com", "saved-1")
        server.enqueue(json(authJson("a", "r")))
        repository.verifyEmail("ani@example.com", "rahasia123", "123456")
        assertTrue(server.takeRequest().body.readUtf8().contains("\"deviceToken\":\"saved-1\""))
    }

    @Test
    fun `verify email keeps the error code of every refusal and stores nothing`() = runBlocking {
        val cases = listOf(
            Triple(400, "VERIFICATION_CODE_INVALID", "wrong, expired or used up code"),
            Triple(401, "AUTH_INVALID_CREDENTIALS", "wrong password"),
            Triple(409, "EMAIL_ALREADY_VERIFIED", "already verified"),
            Triple(400, "VALIDATION_ERROR", "bad input")
        )

        cases.forEach { (status, code, label) ->
            server.enqueue(json(failureJson(code), status))

            val result = repository.verifyEmail("ani@example.com", "rahasia123", "999999") as ApiResult.Failure

            assertEquals(label, status, result.status)
            assertEquals(label, code, result.errorCode)
        }
        assertNull(store.session)
        assertNull(repository.session.value)
    }

    @Test
    fun `verify email whose answer has no tokens stores nothing`() = runBlocking {
        server.enqueue(json(authJson("", "")))

        val result = repository.verifyEmail("ani@example.com", "rahasia123", "123456") as ApiResult.Failure

        assertEquals(ERROR_MALFORMED_RESPONSE, result.errorCode)
        assertNull(store.session)
    }

    @Test
    fun `resend sends the email and the password and returns when the next code is allowed`() = runBlocking {
        server.enqueue(json(resendJson(sent = true, resendAfter = 45)))

        val result = repository.resendVerification(" ani@example.com ", "rahasia123") as ApiResult.Success

        val sent = server.takeRequest()
        val body = sent.body.readUtf8()
        assertEquals("/api/v1/auth/resend-verification", sent.path)
        assertTrue(body, body.contains("\"email\":\"ani@example.com\""))
        assertTrue(body, body.contains("\"password\":\"rahasia123\""))
        assertTrue(result.data.sent)
        assertEquals(45, result.data.resendAfterSeconds)
        assertNull(store.session)
    }

    @Test
    fun `resend sends the device token when there is one`() = runBlocking {
        server.enqueue(json(resendJson()))

        repository.resendVerification("ani@example.com", "rahasia123", deviceToken = "from-register")

        assertTrue(server.takeRequest().body.readUtf8().contains("\"deviceToken\":\"from-register\""))
    }

    @Test
    fun `resend too soon carries the Retry-After header`() = runBlocking {
        server.enqueue(json(failureJson("VERIFICATION_RESEND_TOO_SOON"), 429).setHeader("Retry-After", "42"))

        val result = repository.resendVerification("ani@example.com", "rahasia123") as ApiResult.Failure

        assertEquals(429, result.status)
        assertEquals("VERIFICATION_RESEND_TOO_SOON", result.errorCode)
        assertEquals(42L, result.retryAfterSeconds)
    }

    @Test
    fun `resend refusals keep their status and error code`() = runBlocking {
        val cases = listOf(
            Triple(409, "EMAIL_ALREADY_VERIFIED", "already verified"),
            Triple(503, "EMAIL_SEND_FAILED", "mail service"),
            Triple(401, "AUTH_INVALID_CREDENTIALS", "wrong password"),
            Triple(400, "VALIDATION_ERROR", "bad input")
        )

        cases.forEach { (status, code, label) ->
            server.enqueue(json(failureJson(code), status))

            val result = repository.resendVerification("ani@example.com", "rahasia123") as ApiResult.Failure

            assertEquals(label, status, result.status)
            assertEquals(label, code, result.errorCode)
        }
        assertNull(store.session)
    }

    @Test
    fun `login that waits for the code answers 403 and stores nothing`() = runBlocking {
        server.enqueue(json(failureJson("EMAIL_NOT_VERIFIED"), 403))

        val result = repository.login("ani@example.com", "rahasia123", Role.WALI) as ApiResult.Failure

        assertEquals(403, result.status)
        assertEquals("EMAIL_NOT_VERIFIED", result.errorCode)
        assertNull(store.session)
        assertNull(repository.session.value)
    }
}
