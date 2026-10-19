package com.nthb.kibooz.data

import com.nthb.kibooz.data.remote.ApiClient
import com.nthb.kibooz.data.remote.ApiEnvelope
import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.AuthApi
import com.nthb.kibooz.data.remote.TokenAuthenticator
import com.nthb.kibooz.data.remote.safeApiCall
import com.nthb.kibooz.data.repository.AuthRepository
import com.nthb.kibooz.data.session.Role
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.rules.Timeout
import org.junit.Test
import retrofit2.Response
import retrofit2.http.GET

/** Menjalankan ApiClient, TokenAuthenticator dan AuthRepository bersama terhadap MockWebServer. */
class TokenAuthenticatorTest {

    private data class Ping(val name: String)

    private interface PingService {
        @GET("ping")
        suspend fun ping(): Response<ApiEnvelope<Ping>>
    }

    @get:Rule
    val timeout: Timeout = Timeout(30, TimeUnit.SECONDS)

    private lateinit var server: MockWebServer
    private lateinit var store: InMemorySessionStore
    private lateinit var repository: AuthRepository
    private lateinit var ping: PingService

    private val refreshCalls = AtomicInteger()
    private val pingCalls = AtomicInteger()
    private var refreshResponse = { MockResponse().setBody(authJson("new-access", "new-refresh")) }

    /** /ping hanya menerima "new-access", sisanya 401. */
    private var pingAcceptsNewToken = true

    @Before
    fun setUp() {
        store = InMemorySessionStore(sampleSession)
        server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                    "/api/v1/auth/refresh-token" -> {
                        refreshCalls.incrementAndGet()
                        refreshResponse()
                    }
                    "/api/v1/auth/login" ->
                        MockResponse().setResponseCode(401).setBody(failureJson("AUTH_INVALID_CREDENTIALS"))
                    "/api/v1/ping" -> {
                        pingCalls.incrementAndGet()
                        if (pingAcceptsNewToken && request.getHeader("Authorization") == "Bearer new-access") {
                            MockResponse().setBody("""{"success":true,"data":{"name":"kibooz"}}""")
                        } else {
                            MockResponse().setResponseCode(401).setBody(failureJson("AUTH_TOKEN_INVALID"))
                        }
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
            start()
        }

        lateinit var repo: AuthRepository
        val client = ApiClient(
            tokenProvider = { repo.accessToken() },
            authenticator = TokenAuthenticator { repo.refresh(it) },
            baseUrl = server.url("/api/v1/").toString(),
            logLevel = HttpLoggingInterceptor.Level.NONE
        )
        repo = AuthRepository(client.create(AuthApi::class.java), store, client.moshi)
        repository = repo
        ping = client.create(PingService::class.java)
        moshi = client.moshi
    }

    private lateinit var moshi: com.squareup.moshi.Moshi

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `a 401 refreshes the token once and retries the request`() = runBlocking {
        val result = safeApiCall(moshi) { ping.ping() }

        assertEquals("kibooz", (result as ApiResult.Success).data.name)
        assertEquals(1, refreshCalls.get())
        assertEquals(2, pingCalls.get())
        assertEquals("new-refresh", store.session?.refreshToken)
    }

    @Test
    fun `a request that is still refused after the refresh is not retried again`() = runBlocking {
        pingAcceptsNewToken = false

        val result = safeApiCall(moshi) { ping.ping() } as ApiResult.Failure

        assertEquals(401, result.status)
        assertEquals(1, refreshCalls.get())
        assertEquals(2, pingCalls.get())
    }

    @Test
    fun `when the session has ended the 401 is returned and the session is cleared`() = runBlocking {
        refreshResponse = { MockResponse().setResponseCode(401).setBody(failureJson("AUTH_REFRESH_INVALID")) }

        val result = safeApiCall(moshi) { ping.ping() } as ApiResult.Failure

        assertEquals("AUTH_TOKEN_INVALID", result.errorCode)
        assertEquals(1, pingCalls.get())
        assertNull(store.session)
        assertNull(repository.session.value)
    }

    @Test
    fun `a failing refresh that is not a 401 leaves the session alone`() = runBlocking {
        refreshResponse = { MockResponse().setResponseCode(503) }

        val result = safeApiCall(moshi) { ping.ping() } as ApiResult.Failure

        assertEquals(401, result.status)
        assertEquals("old-refresh", store.session?.refreshToken)
    }

    @Test
    fun `a wrong password on login never triggers a refresh`() = runBlocking {
        store.session = null

        val result = repository.login("ani@example.com", "wrong", Role.WALI) as ApiResult.Failure

        assertEquals("AUTH_INVALID_CREDENTIALS", result.errorCode)
        assertEquals(0, refreshCalls.get())
    }

    @Test
    fun `a 401 without a stored session is not refreshed`() = runBlocking {
        store.session = null

        val result = safeApiCall(moshi) { ping.ping() } as ApiResult.Failure

        assertEquals(401, result.status)
        assertEquals(0, refreshCalls.get())
    }

    @Test
    fun `several requests that fail together share one refresh`() = runBlocking {
        refreshResponse = {
            MockResponse().setBody(authJson("new-access", "new-refresh")).setBodyDelay(300, TimeUnit.MILLISECONDS)
        }

        val results = (1..5).map { async(Dispatchers.IO) { safeApiCall(moshi) { ping.ping() } } }.awaitAll()

        assertTrue(results.all { it is ApiResult.Success })
        assertEquals(1, refreshCalls.get())
    }
}
