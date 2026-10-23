package com.nthb.kibooz.data.session

import com.nthb.kibooz.data.InMemorySessionStore
import com.nthb.kibooz.data.remote.ApiClient
import com.nthb.kibooz.data.remote.AuthApi
import com.nthb.kibooz.data.repository.AuthRepository
import com.nthb.kibooz.data.sampleSession
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout

class SessionManagerTest {

    @get:Rule
    val timeout: Timeout = Timeout(30, TimeUnit.SECONDS)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var server: MockWebServer
    private lateinit var store: InMemorySessionStore
    private lateinit var manager: SessionManager

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        store = InMemorySessionStore(sampleSession).apply { deviceTokens["ani@example.com"] = "device-1" }
        val client = ApiClient(
            tokenProvider = { null },
            baseUrl = server.url("/api/v1/").toString(),
            logLevel = HttpLoggingInterceptor.Level.NONE
        )
        manager = SessionManager(AuthRepository(client.create(AuthApi::class.java), store, client.moshi), scope)
    }

    @After
    fun tearDown() {
        scope.cancel()
        server.shutdown()
    }

    private fun awaitSessionCleared() = runBlocking {
        withTimeout(10_000) { while (store.session != null) delay(10) }
    }

    @Test
    fun `signing out revokes the refresh token and clears the session but keeps the device token`() {
        server.enqueue(MockResponse().setBody("""{"success":true}"""))

        manager.signOut()
        awaitSessionCleared()

        val request = server.takeRequest(5, TimeUnit.SECONDS)
        assertNotNull(request)
        assertEquals("/api/v1/auth/logout", request!!.path)
        assertEquals("""{"refreshToken":"old-refresh"}""", request.body.readUtf8())
        assertNull(store.session)
        assertEquals("device-1", store.deviceTokens["ani@example.com"])
    }

    @Test
    fun `the local session is cleared even when the server cannot be reached`() {
        repeat(2) { server.enqueue(MockResponse().setResponseCode(503)) }

        manager.signOut()
        awaitSessionCleared()

        assertNull(store.session)
    }
}
