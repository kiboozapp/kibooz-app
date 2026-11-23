package com.nthb.kibooz.data.repository

import com.nthb.kibooz.data.InMemorySessionStore
import com.nthb.kibooz.data.failureJson
import com.nthb.kibooz.data.remote.ApiClient
import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.AuthApi
import com.nthb.kibooz.data.sampleSession
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout

class AuthRepositoryChangePasswordTest {

    @get:Rule
    val timeout: Timeout = Timeout(30, TimeUnit.SECONDS)

    private lateinit var server: MockWebServer
    private lateinit var store: InMemorySessionStore
    private lateinit var repository: AuthRepository

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        store = InMemorySessionStore(sampleSession)
        val client = ApiClient(
            tokenProvider = { "jwt" },
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
    fun `sends both passwords with PUT and the token`() = runBlocking {
        server.enqueue(json("""{"success":true,"message":"Password changed"}"""))

        repository.changePassword("lama123!", "baru4567!")

        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/api/v1/users/me/password", request.path)
        assertEquals("Bearer jwt", request.getHeader("Authorization"))
        assertEquals("""{"currentPassword":"lama123!","newPassword":"baru4567!"}""", request.body.readUtf8())
    }

    @Test
    fun `success ends the local session because the server ended every session`() = runBlocking {
        repository.restore()
        server.enqueue(json("""{"success":true,"message":"Password changed"}"""))

        val result = repository.changePassword("lama123!", "baru4567!")

        assertTrue(result is ApiResult.Success)
        assertNull(store.session)
        assertNull(repository.session.value)
        // Tidak memanggil logout karena refresh token-nya sudah tidak berlaku
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a wrong current password keeps the session`() = runBlocking {
        repository.restore()
        server.enqueue(json(failureJson("AUTH_PASSWORD_INCORRECT"), 403))

        val result = repository.changePassword("salah", "baru4567!") as ApiResult.Failure

        assertEquals(403, result.status)
        assertEquals("AUTH_PASSWORD_INCORRECT", result.errorCode)
        assertNotNull(store.session)
        assertNotNull(repository.session.value)
    }

    @Test
    fun `a rejected new password reports the field and keeps the session`() = runBlocking {
        repository.restore()
        server.enqueue(
            json(
                """{"success":false,"message":"Invalid","errorCode":"VALIDATION_ERROR","details":{"newPassword":"too common"}}""",
                400
            )
        )

        val result = repository.changePassword("lama123!", "password") as ApiResult.Failure

        assertEquals("VALIDATION_ERROR", result.errorCode)
        assertEquals("too common", result.details?.get("newPassword"))
        assertNotNull(store.session)
    }

    @Test
    fun `offline is a network error and keeps the session`() = runBlocking {
        repository.restore()
        server.shutdown()

        val result = repository.changePassword("lama123!", "baru4567!")

        assertTrue(result is ApiResult.NetworkError)
        assertNotNull(store.session)
    }
}
