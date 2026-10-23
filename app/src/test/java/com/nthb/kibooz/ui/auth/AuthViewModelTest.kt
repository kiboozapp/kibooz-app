package com.nthb.kibooz.ui.auth

import com.nthb.kibooz.R
import com.nthb.kibooz.data.InMemorySessionStore
import com.nthb.kibooz.data.authJson
import com.nthb.kibooz.data.failureJson
import com.nthb.kibooz.data.remote.ApiClient
import com.nthb.kibooz.data.remote.AuthApi
import com.nthb.kibooz.data.repository.AuthRepository
import com.nthb.kibooz.data.session.Role
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    @get:Rule
    val timeout: Timeout = Timeout(30, TimeUnit.SECONDS)

    private lateinit var server: MockWebServer
    private lateinit var store: InMemorySessionStore
    private lateinit var viewModel: AuthViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        server = MockWebServer().apply { start() }
        store = InMemorySessionStore()
        val client = ApiClient(
            tokenProvider = { null },
            baseUrl = server.url("/api/v1/").toString(),
            logLevel = HttpLoggingInterceptor.Level.NONE
        )
        val repository = AuthRepository(client.create(AuthApi::class.java), store, client.moshi)
        viewModel = AuthViewModel(repository, busyRetryMillis = 0)
    }

    @After
    fun tearDown() {
        server.shutdown()
        Dispatchers.resetMain()
    }

    private fun json(body: String, code: Int = 200) = MockResponse().setResponseCode(code).setBody(body)

    /** Menunggu sampai login selesai, lalu mengembalikan keadaan akhirnya. */
    private fun settled(): LoginUiState = runBlocking { viewModel.state.first { !it.loading } }

    @Test
    fun `a bad email or an empty password is refused before any request`() {
        viewModel.login("bukan-email", "", Role.WALI)

        val state = viewModel.state.value
        assertEquals(R.string.form_email_tidak_valid, state.emailError)
        assertEquals(R.string.form_password_kosong, state.passwordError)
        assertFalse(state.loading)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a short password is still sent because only the server knows if it is right`() {
        server.enqueue(json(failureJson("AUTH_INVALID_CREDENTIALS"), 401))

        viewModel.login("ani@example.com", "abc", Role.WALI)

        assertEquals(R.string.error_invalid_credentials, settled().error)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a successful login exposes the user and keeps the session`() {
        server.enqueue(json(authJson("access-1", "refresh-1")))

        viewModel.login("ani@example.com", "secret123", Role.WALI)

        val state = settled()
        assertEquals("ani@example.com", state.signedIn?.email)
        assertEquals(Role.WALI, state.signedIn?.role)
        assertNull(state.error)
        assertEquals("access-1", store.session?.accessToken)
    }

    @Test
    fun `wrong credentials show the mapped message and no user`() {
        server.enqueue(json(failureJson("AUTH_INVALID_CREDENTIALS"), 401))

        viewModel.login("ani@example.com", "secret123", Role.WALI)

        val state = settled()
        assertEquals(R.string.error_invalid_credentials, state.error)
        assertNull(state.signedIn)
        assertNull(store.session)
    }

    @Test
    fun `showing the error clears it`() {
        server.enqueue(json(failureJson("AUTH_INVALID_CREDENTIALS"), 401))
        viewModel.login("ani@example.com", "secret123", Role.WALI)
        assertNotNull(settled().error)

        viewModel.errorShown()

        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `a busy server answer with Retry-After 1 is retried once`() {
        server.enqueue(json(failureJson("RATE_LIMITED"), 429).setHeader("Retry-After", "1"))
        server.enqueue(json(authJson("access-1", "refresh-1")))

        viewModel.login("ani@example.com", "secret123", Role.WALI)

        assertNotNull(settled().signedIn)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `a real rate limit is not retried`() {
        server.enqueue(json(failureJson("RATE_LIMITED"), 429).setHeader("Retry-After", "30"))

        viewModel.login("ani@example.com", "secret123", Role.WALI)

        assertEquals(R.string.error_rate_limited, settled().error)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a missing connection shows the network message`() {
        // OkHttp mencoba lagi satu kali setelah koneksi putus, jadi dua balasan yang memutus
        repeat(2) { server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)) }

        viewModel.login("ani@example.com", "secret123", Role.WALI)

        assertEquals(R.string.error_network, settled().error)
    }

    @Test
    fun `an admin account is signed out again because the app has no portal for it`() {
        server.enqueue(
            json(
                """{"success":true,"data":{"token":"a","refreshToken":"r",
                "user":{"id":"u9","email":"admin@example.com","fullName":"Admin","role":"ADMIN","avatarUrl":null}}}"""
            )
        )
        server.enqueue(json("""{"success":true}"""))

        viewModel.login("admin@example.com", "secret123", Role.WALI)

        val state = settled()
        assertEquals(R.string.error_forbidden, state.error)
        assertNull(state.signedIn)
        assertNull(store.session)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `a second tap while a login runs is ignored`() {
        server.enqueue(json(authJson("access-1", "refresh-1")))

        viewModel.login("ani@example.com", "secret123", Role.WALI)
        viewModel.login("ani@example.com", "secret123", Role.WALI)

        settled()
        assertTrue(server.requestCount <= 1)
    }
}
