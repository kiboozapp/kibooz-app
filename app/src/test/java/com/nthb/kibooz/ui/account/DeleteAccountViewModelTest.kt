package com.nthb.kibooz.ui.account

import com.nthb.kibooz.R
import com.nthb.kibooz.data.failureJson
import com.nthb.kibooz.data.remote.AccountApi
import com.nthb.kibooz.data.remote.OutageKind
import com.nthb.kibooz.data.repository.AccountRepository
import com.nthb.kibooz.data.repository.jsonResponse
import com.nthb.kibooz.data.repository.testClient
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.mockwebserver.MockResponse
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

@OptIn(ExperimentalCoroutinesApi::class)
class DeleteAccountViewModelTest {

    @get:Rule
    val timeout: Timeout = Timeout(30, TimeUnit.SECONDS)

    private lateinit var server: MockWebServer
    private lateinit var repository: AccountRepository
    private var signOuts = 0
    private val pauses = mutableListOf<Long>()

    private val deleted = """{"success":true,"message":"Account deleted"}"""

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        server = MockWebServer().apply { start() }
        val client = testClient(server)
        repository = AccountRepository(client.create(AccountApi::class.java), client.moshi)
    }

    @After
    fun tearDown() {
        server.shutdown()
        Dispatchers.resetMain()
    }

    private fun teacher() = DeleteAccountViewModel(repository, isTeacher = true, signOut = { signOuts++ }, pause = { pauses += it })

    private fun parent() = DeleteAccountViewModel(repository, isTeacher = false, signOut = { signOuts++ }, pause = { pauses += it })

    private fun failure(code: String, status: Int, details: String? = null) = jsonResponse(
        """{"success":false,"message":"nope","errorCode":"$code"${details?.let { ",\"details\":$it" } ?: ""}}""",
        status
    )

    private fun lastRequestBody(): String {
        var last: String? = null
        while (true) {
            last = (server.takeRequest(200, TimeUnit.MILLISECONDS) ?: break).body.readUtf8()
        }
        return last.orEmpty()
    }

    /** Mengisi dan mengonfirmasi, lalu menunggu sampai permintaan selesai dan mengembalikan keadaan akhirnya. */
    private fun DeleteAccountViewModel.deleteWith(password: String, successor: String = ""): DeleteAccountState {
        requestDelete(password, successor)
        confirm()
        return runBlocking { state.first { !it.loading } }
    }

    @Test
    fun `an empty password is refused before any dialog or request`() {
        val viewModel = parent()

        viewModel.requestDelete("", "")

        val state = viewModel.state.value
        assertEquals(R.string.form_password_kosong, state.passwordError)
        assertFalse(state.confirming)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a password over 72 bytes is refused before any request`() {
        val viewModel = parent()

        viewModel.requestDelete("é".repeat(37), "")

        assertEquals(R.string.form_password_terlalu_panjang, viewModel.state.value.passwordError)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a valid form asks for confirmation and sends nothing yet`() {
        val viewModel = parent()

        viewModel.requestDelete("secret-123", "")

        assertTrue(viewModel.state.value.confirming)
        assertFalse(viewModel.state.value.loading)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `cancelling the dialog sends nothing and the form can be confirmed again`() {
        val viewModel = parent()
        viewModel.requestDelete("secret-123", "")

        viewModel.cancelConfirm()

        assertFalse(viewModel.state.value.confirming)
        assertEquals(0, server.requestCount)

        server.enqueue(jsonResponse(deleted))
        viewModel.requestDelete("secret-123", "")
        viewModel.confirm()
        runBlocking { viewModel.state.first { it.finishedMessage != null } }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `confirm does nothing before the form was checked`() {
        val viewModel = parent()

        viewModel.confirm()

        assertFalse(viewModel.state.value.loading)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `success signs out locally and reports the account as deleted`() {
        server.enqueue(jsonResponse(deleted))
        val viewModel = parent()

        val state = viewModel.deleteWith("secret-123")

        assertEquals(R.string.hapus_akun_berhasil, state.finishedMessage)
        assertNull(state.error)
        assertEquals(1, signOuts)
        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("""{"password":"secret-123"}""", request.body.readUtf8())
    }

    @Test
    fun `a teacher sends the successor email with the password`() {
        server.enqueue(jsonResponse(deleted))
        val viewModel = teacher()

        val state = viewModel.deleteWith("secret-123", " budi@example.com ")

        assertEquals(R.string.hapus_akun_berhasil, state.finishedMessage)
        assertEquals(
            """{"password":"secret-123","successorEmail":"budi@example.com"}""",
            server.takeRequest().body.readUtf8()
        )
    }

    @Test
    fun `a parent never sends a successor email even if the field has text`() {
        server.enqueue(jsonResponse(deleted))
        val viewModel = parent()

        viewModel.deleteWith("secret-123", "budi@example.com")

        assertEquals("""{"password":"secret-123"}""", server.takeRequest().body.readUtf8())
    }

    @Test
    fun `a malformed successor email is refused before any request`() {
        val viewModel = teacher()

        viewModel.requestDelete("secret-123", "bukan-email")

        assertEquals(R.string.form_email_tidak_valid, viewModel.state.value.successorError)
        assertFalse(viewModel.state.value.confirming)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `AUTH_PASSWORD_INCORRECT marks the password field and keeps the session`() {
        server.enqueue(failure("AUTH_PASSWORD_INCORRECT", 403))
        val viewModel = parent()

        val state = viewModel.deleteWith("wrong-pass")

        assertEquals(R.string.error_password_incorrect, state.passwordError)
        assertNull(state.finishedMessage)
        assertNull(state.error)
        assertEquals(0, signOuts)
    }

    @Test
    fun `SUCCESSOR_REQUIRED marks the successor field as required and the next try needs it`() {
        server.enqueue(failure("SUCCESSOR_REQUIRED", 409))
        val viewModel = teacher()

        val state = viewModel.deleteWith("secret-123")

        assertTrue(state.successorRequired)
        assertEquals(R.string.hapus_akun_pengganti_wajib, state.successorError)
        assertEquals(0, signOuts)

        // Tanpa email, formulir ditolak di layar dan tidak ada permintaan kedua
        viewModel.requestDelete("secret-123", "  ")
        assertEquals(R.string.hapus_akun_pengganti_wajib, viewModel.state.value.successorError)
        assertFalse(viewModel.state.value.confirming)
        assertEquals(1, server.requestCount)

        // Dengan email, permintaan kedua membawanya
        server.enqueue(jsonResponse(deleted))
        val done = viewModel.deleteWith("secret-123", "budi@example.com")
        assertEquals(R.string.hapus_akun_berhasil, done.finishedMessage)
        server.takeRequest()
        assertEquals(
            """{"password":"secret-123","successorEmail":"budi@example.com"}""",
            server.takeRequest().body.readUtf8()
        )
        assertEquals(1, signOuts)
    }

    @Test
    fun `TEACHER_NOT_FOUND marks the successor field`() {
        server.enqueue(failure("TEACHER_NOT_FOUND", 404))
        val viewModel = teacher()

        val state = viewModel.deleteWith("secret-123", "nobody@example.com")

        assertEquals(R.string.hapus_akun_pengganti_tidak_ditemukan, state.successorError)
        assertFalse(state.successorRequired)
        assertNull(state.finishedMessage)
        assertEquals(0, signOuts)
    }

    @Test
    fun `PROFILE_NOT_FOUND clears the local session because the account is already gone`() {
        server.enqueue(failure("PROFILE_NOT_FOUND", 404))
        val viewModel = parent()

        val state = viewModel.deleteWith("secret-123")

        assertEquals(R.string.hapus_akun_sudah_tidak_ada, state.finishedMessage)
        assertEquals(1, signOuts)
    }

    @Test
    fun `a validation error on the successor email marks that field`() {
        server.enqueue(failure("VALIDATION_ERROR", 400, """{"successorEmail":"must be another teacher"}"""))
        val viewModel = teacher()

        val state = viewModel.deleteWith("secret-123", "me@example.com")

        assertEquals(R.string.hapus_akun_pengganti_tidak_valid, state.successorError)
        assertNull(state.error)
    }

    @Test
    fun `any other validation error is a general error`() {
        server.enqueue(failure("VALIDATION_ERROR", 400, """{"password":"is required"}"""))
        val viewModel = parent()

        val state = viewModel.deleteWith("secret-123")

        assertEquals(R.string.error_validation, state.error)
        assertNull(state.passwordError)
        assertNull(state.outage)
    }

    @Test
    fun `a 429 with Retry-After waits and sends the same request again`() {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "1").setBody(failureJson("RATE_LIMITED")))
        server.enqueue(jsonResponse(deleted))
        val viewModel = parent()

        val state = viewModel.deleteWith("secret-123")

        assertEquals(R.string.hapus_akun_berhasil, state.finishedMessage)
        assertEquals(listOf(1_000L), pauses)
        assertEquals(2, server.requestCount)
        assertEquals(1, signOuts)
    }

    @Test
    fun `a 429 that never ends gives up with a rate limit message`() {
        repeat(4) {
            server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "1").setBody(failureJson("RATE_LIMITED")))
        }
        val viewModel = parent()

        val state = viewModel.deleteWith("secret-123")

        assertEquals(R.string.error_rate_limited, state.error)
        assertNull(state.finishedMessage)
        assertNull(state.retryInSeconds)
        assertEquals(4, server.requestCount)
        assertEquals(0, signOuts)
    }

    @Test
    fun `offline shows the outage card and a retry sends the same form again`() {
        // OkHttp repeats a request once after a dropped connection, so two answers are consumed
        repeat(2) { server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)) }
        val viewModel = teacher()

        val offline = viewModel.deleteWith("secret-123", "budi@example.com")

        assertEquals(OutageKind.OFFLINE, offline.outage)
        assertEquals(R.string.error_network, offline.error)
        assertNull(offline.finishedMessage)
        assertEquals(0, signOuts)

        server.enqueue(jsonResponse(deleted))
        viewModel.confirm()
        val done = runBlocking { viewModel.state.first { it.finishedMessage != null } }

        assertEquals(R.string.hapus_akun_berhasil, done.finishedMessage)
        assertNull(done.outage)
        // Percobaan pertama diputus sebelum dibaca, jadi hanya isi permintaan terakhir yang dibandingkan
        assertEquals("""{"password":"secret-123","successorEmail":"budi@example.com"}""", lastRequestBody())
    }

    @Test
    fun `a bare 503 from a proxy is an outage and not a field error`() {
        server.enqueue(MockResponse().setResponseCode(503))
        val viewModel = parent()

        val state = viewModel.deleteWith("secret-123")

        assertEquals(OutageKind.UNAVAILABLE, state.outage)
        assertNull(state.passwordError)
    }

    @Test
    fun `a server error shows its message and no outage card`() {
        server.enqueue(failure("INTERNAL_ERROR", 500))
        val viewModel = parent()

        val state = viewModel.deleteWith("secret-123")

        assertEquals(R.string.error_server, state.error)
        assertNull(state.outage)
    }

    @Test
    fun `a second submit while the request runs sends nothing`() {
        // Balasan tertunda supaya permintaan pertama masih berjalan saat tombol ditekan lagi
        server.enqueue(jsonResponse(deleted).setBodyDelay(300, TimeUnit.MILLISECONDS))
        val viewModel = parent()

        viewModel.requestDelete("secret-123", "")
        viewModel.confirm()
        assertTrue(viewModel.state.value.loading)

        viewModel.requestDelete("secret-123", "")
        viewModel.confirm()
        assertFalse(viewModel.state.value.confirming)

        runBlocking { viewModel.state.first { it.finishedMessage != null } }
        assertEquals(1, server.requestCount)
        assertEquals(1, signOuts)
    }

    @Test
    fun `nothing can be sent again after the account was deleted`() {
        server.enqueue(jsonResponse(deleted))
        val viewModel = parent()
        viewModel.deleteWith("secret-123")

        viewModel.requestDelete("secret-123", "")
        viewModel.confirm()

        assertEquals(1, server.requestCount)
        assertEquals(1, signOuts)
    }

    @Test
    fun `errorShown clears the general error`() {
        server.enqueue(failure("INTERNAL_ERROR", 500))
        val viewModel = parent()
        viewModel.deleteWith("secret-123")
        assertEquals(R.string.error_server, viewModel.state.value.error)

        viewModel.errorShown()

        assertNull(viewModel.state.value.error)
    }
}
