package com.nthb.kibooz.data.repository

import com.nthb.kibooz.data.InMemorySessionStore
import com.nthb.kibooz.data.authJson
import com.nthb.kibooz.data.failureJson
import com.nthb.kibooz.data.remote.ApiClient
import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.AuthApi
import com.nthb.kibooz.data.sampleSession
import com.nthb.kibooz.data.session.Role
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.rules.Timeout
import org.junit.Test

class AuthRepositoryTest {

    @get:Rule
    val timeout: Timeout = Timeout(30, TimeUnit.SECONDS)

    private lateinit var server: MockWebServer
    private lateinit var store: InMemorySessionStore
    private lateinit var repository: AuthRepository

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        store = InMemorySessionStore()
        repository = newRepository()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun newRepository(): AuthRepository {
        val client = ApiClient(
            tokenProvider = { null },
            baseUrl = server.url("/api/v1/").toString(),
            logLevel = HttpLoggingInterceptor.Level.NONE
        )
        return AuthRepository(client.create(AuthApi::class.java), store, client.moshi)
    }

    private fun json(body: String, code: Int = 200) = MockResponse().setResponseCode(code).setBody(body)

    @Test
    fun `login stores the session and the device token`() = runBlocking {
        server.enqueue(json(authJson("access-1", "refresh-1", deviceToken = "device-1")))

        val result = repository.login(" Ani@Example.com ", "secret", Role.WALI)

        assertEquals("ani@example.com", (result as ApiResult.Success).data.email)
        assertEquals("access-1", store.session?.accessToken)
        assertEquals("refresh-1", store.session?.refreshToken)
        assertEquals("device-1", store.readDeviceToken("ani@example.com"))
        assertEquals("access-1", repository.session.value?.accessToken)
        assertEquals("access-1", repository.accessToken())
    }

    @Test
    fun `login sends the device token saved for that email`() = runBlocking {
        store.writeDeviceToken("ani@example.com", "device-1")
        server.enqueue(json(authJson("a", "r")))

        repository.login("ani@example.com", "secret", Role.WALI)

        val body = server.takeRequest().body.readUtf8()
        assertTrue(body, body.contains("\"deviceToken\":\"device-1\""))
        assertTrue(body, body.contains("\"role\":\"WALI\""))
    }

    @Test
    fun `login sends no device token for an email that has none`() = runBlocking {
        server.enqueue(json(authJson("a", "r")))

        repository.login("ani@example.com", "secret", Role.WALI)

        assertTrue(!server.takeRequest().body.readUtf8().contains("deviceToken"))
    }

    @Test
    fun `a wrong password returns the error code and stores nothing`() = runBlocking {
        server.enqueue(json(failureJson("AUTH_INVALID_CREDENTIALS"), code = 401))

        val result = repository.login("ani@example.com", "wrong", Role.WALI) as ApiResult.Failure

        assertEquals(401, result.status)
        assertEquals("AUTH_INVALID_CREDENTIALS", result.errorCode)
        assertNull(store.session)
        assertNull(repository.session.value)
    }

    @Test
    fun `login offline reports a network error`() = runBlocking {
        server.shutdown()

        val result = repository.login("ani@example.com", "secret", Role.WALI)

        assertTrue(result is ApiResult.NetworkError)
        assertNull(store.session)
    }

    @Test
    fun `restore loads the stored session once`() = runBlocking {
        store.session = sampleSession

        assertEquals(sampleSession, repository.restore())
        store.session = null

        assertEquals(sampleSession, repository.restore())
    }

    @Test
    fun `the token is read lazily from storage before restore was called`() {
        store.session = sampleSession

        assertEquals("old-access", repository.accessToken())
    }

    @Test
    fun `refresh saves the rotated pair and returns the new access token`() = runBlocking {
        store.session = sampleSession
        server.enqueue(json(authJson("new-access", "new-refresh")))

        val token = repository.refresh("old-access")

        assertEquals("new-access", token)
        assertEquals("new-refresh", store.session?.refreshToken)
        assertEquals("new-access", repository.session.value?.accessToken)
        assertTrue(server.takeRequest().body.readUtf8().contains("\"refreshToken\":\"old-refresh\""))
    }

    @Test
    fun `refresh keeps the new tokens in memory even when saving them fails`() = runBlocking {
        store.session = sampleSession
        store.failWrites = true
        server.enqueue(json(authJson("new-access", "new-refresh")))

        try {
            repository.refresh("old-access")
            fail("expected the failed write to surface")
        } catch (e: IOException) {
            assertEquals("disk full", e.message)
        }

        assertEquals("new-access", repository.accessToken())
        assertEquals("new-refresh", repository.session.value?.refreshToken)
    }

    @Test
    fun `a refresh token the server rejects ends the session`() = runBlocking {
        store.session = sampleSession
        server.enqueue(json(failureJson("AUTH_REFRESH_INVALID"), code = 401))

        val token = repository.refresh("old-access")

        assertNull(token)
        assertNull(store.session)
        assertNull(repository.session.value)
    }

    @Test
    fun `a rate limit keeps the session so the refresh can be retried`() = runBlocking {
        store.session = sampleSession
        server.enqueue(json(failureJson("RATE_LIMITED"), code = 429).setHeader("Retry-After", "5"))

        assertNull(repository.refresh("old-access"))

        assertNotNull(store.session)
        assertEquals(sampleSession.refreshToken, store.session?.refreshToken)
    }

    @Test
    fun `a server error keeps the session`() = runBlocking {
        store.session = sampleSession
        server.enqueue(MockResponse().setResponseCode(502).setBody("<html>Bad gateway</html>"))

        assertNull(repository.refresh("old-access"))

        assertNotNull(store.session)
    }

    @Test
    fun `being offline keeps the session`() = runBlocking {
        store.session = sampleSession
        server.shutdown()

        assertNull(repository.refresh("old-access"))

        assertNotNull(store.session)
    }

    @Test
    fun `a token that was already replaced returns the newest one without calling the server`() = runBlocking {
        store.session = sampleSession.copy(accessToken = "newer-access")

        val token = repository.refresh("old-access")

        assertEquals("newer-access", token)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `refresh without a session does nothing`() = runBlocking {
        assertNull(repository.refresh("whatever"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `concurrent refreshes call the server once`() = runBlocking {
        store.session = sampleSession
        server.enqueue(json(authJson("new-access", "new-refresh")).setBodyDelay(300, TimeUnit.MILLISECONDS))

        val tokens = (1..6).map { async(Dispatchers.IO) { repository.refresh("old-access") } }.awaitAll()

        assertEquals(List(6) { "new-access" }, tokens)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `logout revokes the refresh token and clears the session but keeps the device token`() = runBlocking {
        store.session = sampleSession
        store.writeDeviceToken("ani@example.com", "device-1")
        server.enqueue(json("""{"success":true,"message":"Logged out"}"""))

        repository.logout()

        val request = server.takeRequest()
        assertEquals("/api/v1/auth/logout", request.path)
        assertTrue(request.body.readUtf8().contains("\"refreshToken\":\"old-refresh\""))
        assertNull(store.session)
        assertNull(repository.session.value)
        assertEquals("device-1", store.readDeviceToken("ani@example.com"))
    }

    @Test
    fun `logout clears the session even when the server cannot be reached`() = runBlocking {
        store.session = sampleSession
        server.shutdown()

        repository.logout()

        assertNull(store.session)
        assertNull(repository.session.value)
    }

    @Test
    fun `logout without a session makes no request`() = runBlocking {
        repository.logout()

        assertEquals(0, server.requestCount)
    }
}
