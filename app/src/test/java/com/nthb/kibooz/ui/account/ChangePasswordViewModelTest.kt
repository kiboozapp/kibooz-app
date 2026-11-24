package com.nthb.kibooz.ui.account

import com.nthb.kibooz.R
import com.nthb.kibooz.data.InMemorySessionStore
import com.nthb.kibooz.data.failureJson
import com.nthb.kibooz.data.remote.ApiClient
import com.nthb.kibooz.data.remote.AuthApi
import com.nthb.kibooz.data.repository.AuthRepository
import com.nthb.kibooz.data.sampleSession
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
class ChangePasswordViewModelTest {

    @get:Rule
    val timeout: Timeout = Timeout(30, TimeUnit.SECONDS)

    private lateinit var server: MockWebServer
    private lateinit var store: InMemorySessionStore
    private lateinit var viewModel: ChangePasswordViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        server = MockWebServer().apply { start() }
        store = InMemorySessionStore(sampleSession)
        val client = ApiClient(
            tokenProvider = { "jwt" },
            baseUrl = server.url("/api/v1/").toString(),
            logLevel = HttpLoggingInterceptor.Level.NONE
        )
        val repository = AuthRepository(client.create(AuthApi::class.java), store, client.moshi)
        viewModel = ChangePasswordViewModel(repository, busyRetryMillis = 0)
    }

    @After
    fun tearDown() {
        server.shutdown()
        Dispatchers.resetMain()
    }

    private fun json(body: String, code: Int = 200) = MockResponse().setResponseCode(code).setBody(body)

    private fun ok() = json("""{"success":true,"message":"Password changed"}""")

    private fun settled(): ChangePasswordState = runBlocking { viewModel.state.first { !it.loading } }

    @Test
    fun `empty fields are refused before any request`() {
        viewModel.change("", "", "")

        val errors = viewModel.state.value.fieldErrors
        assertEquals(R.string.form_password_kosong, errors[PasswordField.CURRENT])
        assertEquals(R.string.form_password_kosong, errors[PasswordField.NEW])
        assertFalse(viewModel.state.value.loading)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a new password shorter than 8 characters is refused`() {
        viewModel.change("lama12345", "pendek", "pendek")

        assertEquals(R.string.form_password_terlalu_pendek, viewModel.state.value.fieldErrors[PasswordField.NEW])
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a new password equal to the current one is refused`() {
        viewModel.change("sama12345", "sama12345", "sama12345")

        assertEquals(R.string.form_password_sama, viewModel.state.value.fieldErrors[PasswordField.NEW])
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a confirmation that differs from the new password is refused`() {
        viewModel.change("lama12345", "baru12345", "baru54321")

        val errors = viewModel.state.value.fieldErrors
        assertEquals(R.string.form_password_konfirmasi_beda, errors[PasswordField.CONFIRM])
        assertNull(errors[PasswordField.NEW])
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `every problem is reported at once`() {
        viewModel.change("", "pendek", "lain")

        assertEquals(setOf(PasswordField.CURRENT, PasswordField.NEW, PasswordField.CONFIRM), viewModel.state.value.fieldErrors.keys)
    }

    @Test
    fun `success marks the password as changed and ends the local session`() {
        server.enqueue(ok())

        viewModel.change("lama12345", "baru12345", "baru12345")

        val state = settled()
        assertTrue(state.changed)
        assertTrue(state.fieldErrors.isEmpty())
        assertNull(state.error)
        assertNull(store.session)
    }

    @Test
    fun `a wrong current password is shown on that field and the session stays`() {
        server.enqueue(json(failureJson("AUTH_PASSWORD_INCORRECT"), 403))

        viewModel.change("salah12345", "baru12345", "baru12345")

        val state = settled()
        assertFalse(state.changed)
        assertEquals(R.string.error_password_incorrect, state.fieldErrors[PasswordField.CURRENT])
        assertNull(state.error)
        assertNotNull(store.session)
    }

    @Test
    fun `a new password the server rejects is shown on that field`() {
        server.enqueue(
            json(
                """{"success":false,"message":"x","errorCode":"VALIDATION_ERROR","details":{"newPassword":"too common"}}""",
                400
            )
        )

        viewModel.change("lama12345", "password123", "password123")

        val state = settled()
        assertFalse(state.changed)
        assertEquals(R.string.error_password_baru_ditolak, state.fieldErrors[PasswordField.NEW])
        assertNotNull(store.session)
    }

    @Test
    fun `a busy server is waited for once and the request is sent again`() {
        server.enqueue(json(failureJson("RATE_LIMITED"), 429).setHeader("Retry-After", "1"))
        server.enqueue(ok())

        viewModel.change("lama12345", "baru12345", "baru12345")

        assertTrue(settled().changed)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `a limit that is not the busy kind is shown as an error`() {
        server.enqueue(json(failureJson("RATE_LIMITED"), 429).setHeader("Retry-After", "30"))

        viewModel.change("lama12345", "baru12345", "baru12345")

        val state = settled()
        assertEquals(R.string.error_rate_limited, state.error)
        assertEquals(1, server.requestCount)
        assertNotNull(store.session)
    }

    @Test
    fun `no connection shows the network message and keeps the session`() {
        server.shutdown()

        viewModel.change("lama12345", "baru12345", "baru12345")

        val state = settled()
        assertEquals(R.string.error_network, state.error)
        assertNotNull(store.session)
    }

    @Test
    fun `the error is cleared once it was shown`() {
        server.shutdown()
        viewModel.change("lama12345", "baru12345", "baru12345")

        viewModel.errorShown()

        assertNull(viewModel.state.value.error)
    }
}
