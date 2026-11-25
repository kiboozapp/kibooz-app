package com.nthb.kibooz.ui.auth

import com.nthb.kibooz.R
import com.nthb.kibooz.data.InMemorySessionStore
import com.nthb.kibooz.data.authJson
import com.nthb.kibooz.data.failureJson
import com.nthb.kibooz.data.remote.ApiClient
import com.nthb.kibooz.data.remote.AuthApi
import com.nthb.kibooz.data.remote.OutageKind
import com.nthb.kibooz.data.repository.AuthRepository
import com.nthb.kibooz.data.resendJson
import com.nthb.kibooz.data.session.Role
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
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
class VerifyEmailViewModelTest {

    @get:Rule
    val timeout: Timeout = Timeout(30, TimeUnit.SECONDS)

    private lateinit var server: MockWebServer
    private lateinit var store: InMemorySessionStore
    private lateinit var repository: AuthRepository

    /** Jam palsu untuk hitung mundur, dalam milidetik. */
    private var now = 5_000_000L
    private val email = "ani@example.com"
    private val password = "rahasia123"

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
        repository = AuthRepository(client.create(AuthApi::class.java), store, client.moshi)
    }

    @After
    fun tearDown() {
        server.shutdown()
        Dispatchers.resetMain()
    }

    private fun json(body: String, code: Int = 200) = MockResponse().setResponseCode(code).setBody(body)

    private fun viewModel(
        source: VerifySource = VerifySource.LOGIN,
        resendAfterSeconds: Int = 0,
        deviceToken: String? = null
    ) = VerifyEmailViewModel(
        repository,
        email,
        source,
        resendAfterSeconds,
        deviceToken,
        cooldown = ResendCooldown { now },
        busyRetryMillis = 0
    )

    private fun VerifyEmailViewModel.settled(): VerifyEmailState = runBlocking { state.first { !it.busy } }

    /**
     * Memutus setiap permintaan sampai tanda online dinyalakan, lalu menjawab dengan [answer]. Jumlah antrean tidak dipakai,
     * karena berapa kali OkHttp mencoba lagi satu permintaan yang diputus tidak bisa dipastikan.
     */
    private fun serveWhenOnline(answer: MockResponse): AtomicBoolean {
        val online = AtomicBoolean(false)
        server.dispatcher = object : Dispatcher() {
            // MockWebServer asks peek() before it reads a request, and the disconnect at start only works from there
            override fun peek(): MockResponse =
                if (online.get()) MockResponse() else MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)

            override fun dispatch(request: RecordedRequest): MockResponse =
                if (online.get()) answer else MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)
        }
        return online
    }

    /** Isi permintaan terakhir yang sampai ke server. */
    private fun lastRequestBody(): String {
        var body = ""
        while (true) body = (server.takeRequest(200, TimeUnit.MILLISECONDS) ?: break).body.readUtf8()
        return body
    }

    // Kode verifikasi

    @Test
    fun `a code that is not 6 digits is refused before any request`() {
        val vm = viewModel()

        vm.verify("123", password)
        assertEquals(R.string.verifikasi_kode_enam_digit, vm.state.value.codeError)

        vm.verify("12345a", password)
        assertEquals(R.string.verifikasi_kode_enam_digit, vm.state.value.codeError)

        assertEquals(0, server.requestCount)
        assertFalse(vm.state.value.loading)
    }

    @Test
    fun `an empty password is refused on its own field before any request`() {
        val vm = viewModel()

        vm.verify("123456", "")

        assertEquals(R.string.form_password_kosong, vm.state.value.passwordError)
        assertNull(vm.state.value.codeError)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a bad code and an empty password are both shown at once`() {
        val vm = viewModel()

        vm.verify("12", "")

        assertEquals(R.string.verifikasi_kode_enam_digit, vm.state.value.codeError)
        assertEquals(R.string.form_password_kosong, vm.state.value.passwordError)
    }

    @Test
    fun `typing again clears the error of that field only`() {
        val vm = viewModel()
        vm.verify("12", "")

        vm.codeEdited()
        assertNull(vm.state.value.codeError)
        assertNotNull(vm.state.value.passwordError)

        vm.passwordEdited()
        assertNull(vm.state.value.passwordError)
    }

    @Test
    fun `a right code with the password signs in, keeps the session and sends the code as text`() {
        server.enqueue(json(authJson("access-1", "refresh-1", deviceToken = "device-1")))
        val vm = viewModel()

        vm.verify(" 012345 ", password)
        val state = vm.settled()

        val body = server.takeRequest().body.readUtf8()
        assertTrue(body, body.contains("\"code\":\"012345\""))
        assertTrue(body, body.contains("\"email\":\"ani@example.com\""))
        assertTrue(body, body.contains("\"password\":\"rahasia123\""))
        assertEquals(Role.WALI, state.signedIn?.role)
        assertNull(state.codeError)
        assertEquals("access-1", store.session?.accessToken)
        assertEquals("device-1", runBlocking { store.readDeviceToken(email) })
        assertFalse(state.toString().contains(password))
    }

    @Test
    fun `the device token of the registration is sent with the code`() {
        server.enqueue(json(authJson("access-1", "refresh-1")))
        val vm = viewModel(VerifySource.REGISTERED, 60, deviceToken = "device-9")

        vm.verify("123456", password)
        vm.settled()

        assertTrue(server.takeRequest().body.readUtf8().contains("\"deviceToken\":\"device-9\""))
    }

    @Test
    fun `a wrong, expired or used up code shows one message on the code field and signs nobody in`() {
        server.enqueue(json(failureJson("VERIFICATION_CODE_INVALID"), 400))
        val vm = viewModel()

        vm.verify("999999", password)
        val state = vm.settled()

        assertEquals(R.string.verifikasi_error_kode_salah, state.codeError)
        assertNull(state.passwordError)
        assertNull(state.error)
        assertNull(state.signedIn)
        assertNull(state.outage)
        assertNull(store.session)
    }

    @Test
    fun `a wrong password shows its own message on the password field and not on the code`() {
        server.enqueue(json(failureJson("AUTH_INVALID_CREDENTIALS"), 401))
        val vm = viewModel()

        vm.verify("123456", "salah-salah")
        val state = vm.settled()

        assertEquals(R.string.error_password_incorrect, state.passwordError)
        assertNull(state.codeError)
        assertNull(state.error)
        assertNull(state.signedIn)
        assertNull(store.session)
    }

    @Test
    fun `an email that is verified already sends the person to login`() {
        server.enqueue(json(failureJson("EMAIL_ALREADY_VERIFIED"), 409))
        val vm = viewModel()

        vm.verify("123456", password)
        val state = vm.settled()

        assertTrue(state.alreadyVerified)
        assertNull(state.error)
        assertNull(state.signedIn)
    }

    @Test
    fun `a second tap while the code is checked sends nothing more`() {
        server.enqueue(json(authJson("access-1", "refresh-1")))
        val vm = viewModel()

        vm.verify("123456", password)
        vm.verify("123456", password)
        vm.settled()

        assertEquals(1, server.requestCount)
    }

    @Test
    fun `verify is ignored once signed in`() {
        server.enqueue(json(authJson("access-1", "refresh-1")))
        val vm = viewModel()
        vm.verify("123456", password)
        vm.settled()

        vm.verify("123456", password)

        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a busy server answer with Retry-After 1 is retried once`() {
        server.enqueue(json(failureJson("RATE_LIMITED"), 429).setHeader("Retry-After", "1"))
        server.enqueue(json(authJson("access-1", "refresh-1")))
        val vm = viewModel()

        vm.verify("123456", password)

        assertNotNull(vm.settled().signedIn)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `a real rate limit shows the message and is not retried`() {
        server.enqueue(json(failureJson("RATE_LIMITED"), 429).setHeader("Retry-After", "30"))
        val vm = viewModel()

        vm.verify("123456", password)
        val state = vm.settled()

        assertEquals(R.string.error_rate_limited, state.error)
        assertNull(state.signedIn)
        assertEquals(1, server.requestCount)
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
        val vm = viewModel()

        vm.verify("123456", password)
        val state = vm.settled()

        assertEquals(R.string.error_forbidden, state.error)
        assertNull(state.signedIn)
        assertNull(store.session)
    }

    @Test
    fun `a missing connection raises the outage card and its retry sends the same code and password again`() {
        val online = serveWhenOnline(json(authJson("access-1", "refresh-1")))
        val vm = viewModel()

        vm.verify("123456", password)
        assertEquals(OutageKind.OFFLINE, vm.settled().outage)

        online.set(true)
        vm.retryAfterOutage()
        val state = vm.settled()

        assertNull(state.outage)
        assertNotNull(state.signedIn)
        val body = lastRequestBody()
        assertTrue(body, body.contains("123456") && body.contains(password))
    }

    @Test
    fun `a gateway error without a JSON body is an outage and an answer from the API is not`() {
        server.enqueue(json("", 502))
        server.enqueue(json(failureJson("INTERNAL_ERROR"), 500))
        val vm = viewModel()

        vm.verify("123456", password)
        assertEquals(OutageKind.UNAVAILABLE, vm.settled().outage)

        vm.verify("123456", password)
        val state = vm.settled()
        assertNull(state.outage)
        assertEquals(R.string.error_server, state.error)
    }

    // Hitung mundur

    @Test
    fun `after registering the resend button waits for the time the server gave`() {
        server.enqueue(json(resendJson()))
        val vm = viewModel(VerifySource.REGISTERED, resendAfterSeconds = 60)
        assertEquals(60, vm.state.value.resendSecondsLeft)
        assertEquals(R.string.verifikasi_info_daftar, vm.state.value.notice)

        vm.resend(password)
        assertEquals("nothing is sent during the countdown", 0, server.requestCount)

        now += 1_000
        vm.tick()
        assertEquals(59, vm.state.value.resendSecondsLeft)

        now += 59_000
        vm.tick()
        assertEquals(0, vm.state.value.resendSecondsLeft)

        vm.resend(password)
        vm.settled()
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `the countdown follows the deadline when the screen was away`() {
        val vm = viewModel(VerifySource.REGISTERED, resendAfterSeconds = 60)

        now += 45_000
        vm.tick()

        assertEquals(15, vm.state.value.resendSecondsLeft)
    }

    @Test
    fun `a mail that did not go out lets the person resend at once`() {
        server.enqueue(json(resendJson()))
        val vm = viewModel(VerifySource.REGISTERED_MAIL_NOT_SENT, resendAfterSeconds = 60)

        assertEquals(0, vm.state.value.resendSecondsLeft)
        assertEquals(R.string.verifikasi_info_belum_terkirim, vm.state.value.notice)

        vm.resend(password)
        vm.settled()

        assertEquals(1, server.requestCount)
    }

    @Test
    fun `coming from a refused login lets the person resend at once with its own notice`() {
        val vm = viewModel(VerifySource.LOGIN)

        assertEquals(0, vm.state.value.resendSecondsLeft)
        assertEquals(R.string.verifikasi_info_login, vm.state.value.notice)
    }

    // Kirim ulang

    @Test
    fun `resend sends the password, starts the countdown from the answer and keeps the password out of the state`() {
        server.enqueue(json(resendJson(sent = true, resendAfter = 45)))
        val vm = viewModel(deviceToken = "device-9")

        vm.resend(password)
        val state = vm.settled()

        val body = server.takeRequest().body.readUtf8()
        assertTrue(body, body.contains("\"email\":\"ani@example.com\""))
        assertTrue(body, body.contains("\"password\":\"rahasia123\""))
        assertTrue(body, body.contains("\"deviceToken\":\"device-9\""))
        assertEquals(45, state.resendSecondsLeft)
        assertEquals(R.string.verifikasi_info_terkirim_ulang, state.notice)
        assertFalse(state.toString().contains(password))
    }

    @Test
    fun `a resend whose mail did not go out gives no countdown`() {
        server.enqueue(json(resendJson(sent = false, resendAfter = 60)))
        val vm = viewModel()

        vm.resend(password)
        val state = vm.settled()

        assertEquals(0, state.resendSecondsLeft)
        assertEquals(R.string.verifikasi_info_belum_terkirim, state.notice)
    }

    @Test
    fun `an empty password is refused on the password field before any resend`() {
        val vm = viewModel()

        vm.resend("")

        assertEquals(R.string.form_password_kosong, vm.state.value.passwordError)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a wrong password on resend shows the message on the password field`() {
        server.enqueue(json(failureJson("AUTH_INVALID_CREDENTIALS"), 401))
        val vm = viewModel()

        vm.resend("salah-salah")
        val state = vm.settled()

        assertEquals(R.string.error_password_incorrect, state.passwordError)
        assertNull(state.error)
        assertEquals(0, state.resendSecondsLeft)
    }

    @Test
    fun `too soon starts the countdown from Retry-After`() {
        server.enqueue(json(failureJson("VERIFICATION_RESEND_TOO_SOON"), 429).setHeader("Retry-After", "37"))
        val vm = viewModel()

        vm.resend(password)
        val state = vm.settled()

        assertEquals(37, state.resendSecondsLeft)
        assertEquals(R.string.verifikasi_error_terlalu_cepat, state.error)
        assertEquals("a refused request is not retried", 1, server.requestCount)
    }

    @Test
    fun `too soon without Retry-After counts down a full minute`() {
        server.enqueue(json(failureJson("VERIFICATION_RESEND_TOO_SOON"), 429))
        val vm = viewModel()

        vm.resend(password)

        assertEquals(60, vm.settled().resendSecondsLeft)
    }

    @Test
    fun `an hourly limit with a long Retry-After counts down that long`() {
        server.enqueue(json(failureJson("VERIFICATION_RESEND_TOO_SOON"), 429).setHeader("Retry-After", "1800"))
        val vm = viewModel()

        vm.resend(password)

        assertEquals(1_800, vm.settled().resendSecondsLeft)
    }

    @Test
    fun `an email that is verified already is told to the screen so it can go to login`() {
        server.enqueue(json(failureJson("EMAIL_ALREADY_VERIFIED"), 409))
        val vm = viewModel()

        vm.resend(password)
        val state = vm.settled()

        assertTrue(state.alreadyVerified)
        assertNull(state.error)
    }

    @Test
    fun `a mail service failure shows its message, is not an outage and leaves resend open`() {
        server.enqueue(json(failureJson("VERIFICATION_RESEND_TOO_SOON"), 429).setHeader("Retry-After", "30"))
        server.enqueue(json(failureJson("EMAIL_SEND_FAILED"), 503))
        val vm = viewModel()
        vm.resend(password)
        vm.settled()
        now += 30_000
        vm.tick()

        vm.resend(password)
        val state = vm.settled()

        assertEquals(R.string.verifikasi_error_kirim_gagal, state.error)
        assertNull(state.outage)
        assertEquals(0, state.resendSecondsLeft)
    }

    @Test
    fun `a missing connection during resend raises the card and its retry resends with the same password`() {
        val online = serveWhenOnline(json(resendJson()))
        val vm = viewModel()

        vm.resend(password)
        assertEquals(OutageKind.OFFLINE, vm.settled().outage)

        online.set(true)
        vm.retryAfterOutage()
        val state = vm.settled()

        assertNull(state.outage)
        assertEquals(60, state.resendSecondsLeft)
        assertTrue(lastRequestBody().contains(password))
    }

    @Test
    fun `a second tap while a code is being sent sends nothing more`() {
        server.enqueue(json(resendJson()))
        val vm = viewModel()

        vm.resend(password)
        vm.resend(password)
        vm.settled()

        assertEquals(1, server.requestCount)
    }

    @Test
    fun `verify is blocked while a code is being sent`() {
        server.enqueue(json(resendJson()))
        val vm = viewModel()

        vm.resend(password)
        vm.verify("123456", password)
        vm.settled()

        assertEquals(1, server.requestCount)
    }

    @Test
    fun `resend is blocked while the code is being checked`() {
        server.enqueue(json(authJson("access-1", "refresh-1")))
        val vm = viewModel()

        vm.verify("123456", password)
        vm.resend(password)
        vm.settled()

        assertEquals(1, server.requestCount)
    }
}
