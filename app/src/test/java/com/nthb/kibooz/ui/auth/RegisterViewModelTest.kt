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
class RegisterViewModelTest {

    @get:Rule
    val timeout: Timeout = Timeout(30, TimeUnit.SECONDS)

    private lateinit var server: MockWebServer
    private lateinit var store: InMemorySessionStore
    private lateinit var viewModel: RegisterViewModel

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
        viewModel = RegisterViewModel(AuthRepository(client.create(AuthApi::class.java), store, client.moshi))
    }

    @After
    fun tearDown() {
        server.shutdown()
        Dispatchers.resetMain()
    }

    private fun json(body: String, code: Int = 200) = MockResponse().setResponseCode(code).setBody(body)

    private fun settled(): RegisterUiState = runBlocking { viewModel.state.first { !it.loading } }

    private val guru = RegisterForm(
        role = Role.GURU,
        fullName = "Siti Rahayu",
        email = "siti@example.com",
        password = "rahasia123",
        className = "Bunga"
    )

    private val wali = RegisterForm(
        role = Role.WALI,
        fullName = "Sarah Kartika",
        email = "sarah@example.com",
        password = "rahasia123",
        classCode = "ABC123",
        childName = "Amelia Siti Zahra",
        nisn = "0012345678"
    )

    @Test
    fun `an empty form is refused for every required field before any request`() {
        viewModel.register(RegisterForm(role = Role.WALI))

        val errors = viewModel.state.value.fieldErrors
        assertEquals(
            setOf(
                RegisterField.FULL_NAME, RegisterField.EMAIL, RegisterField.PASSWORD,
                RegisterField.CLASS_CODE, RegisterField.CHILD_NAME, RegisterField.NISN
            ),
            errors.keys
        )
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a guru needs a class name but not a class code or a child`() {
        assertEquals(setOf(RegisterField.CLASS_NAME), RegisterRules.validate(guru.copy(className = " ")).keys)
        assertTrue(RegisterRules.validate(guru).isEmpty())
    }

    @Test
    fun `a class code is 4 to 12 letters or digits`() {
        assertEquals(R.string.form_kode_kelas_tidak_valid, RegisterRules.validate(wali.copy(classCode = "AB1"))[RegisterField.CLASS_CODE])
        assertEquals(R.string.form_kode_kelas_tidak_valid, RegisterRules.validate(wali.copy(classCode = "AB-123"))[RegisterField.CLASS_CODE])
        assertTrue(RegisterRules.validate(wali.copy(classCode = "abcd")).isEmpty())
    }

    @Test
    fun `a guru registration sends the class and leaves out the empty optional fields`() {
        server.enqueue(json(authJson("access-1", "refresh-1", deviceToken = "device-1"), 201))

        viewModel.register(guru.copy(phone = "  ", nip = "1987", gradeLevel = "Class A"))
        val state = settled()

        val request = server.takeRequest()
        val body = request.body.readUtf8()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/auth/register", request.path)
        assertTrue(body, body.contains("\"role\":\"GURU\""))
        assertTrue(body, body.contains("\"class\":{\"name\":\"Bunga\",\"gradeLevel\":\"Class A\"}"))
        assertTrue(body, body.contains("\"nip\":\"1987\""))
        assertFalse(body, body.contains("phoneNumber"))
        assertFalse(body, body.contains("classCode"))
        assertFalse(body, body.contains("student"))
        assertNotNull(state.signedIn)
    }

    @Test
    fun `a wali registration sends the class code and the child and signs in`() {
        server.enqueue(json(authJson("access-1", "refresh-1", deviceToken = "device-1"), 201))

        viewModel.register(wali.copy(email = " Sarah@Example.com "))
        val state = settled()

        val body = server.takeRequest().body.readUtf8()
        assertTrue("trimmed like login, the server normalizes the case", body.contains("\"email\":\"Sarah@Example.com\""))
        assertTrue(body, body.contains("\"classCode\":\"ABC123\""))
        assertTrue(body, body.contains("\"student\":{\"nisn\":\"0012345678\",\"fullName\":\"Amelia Siti Zahra\"}"))
        assertFalse(body, body.contains("\"class\""))
        assertNotNull(state.signedIn)
        assertEquals("access-1", store.session?.accessToken)
        assertEquals("device-1", runBlocking { store.readDeviceToken("sarah@example.com") })
    }

    @Test
    fun `a taken email is shown on the email field and nothing is stored`() {
        server.enqueue(json(failureJson("EMAIL_ALREADY_REGISTERED"), 409))

        viewModel.register(guru)
        val state = settled()

        assertEquals(R.string.error_email_taken, state.fieldErrors[RegisterField.EMAIL])
        assertNull(state.error)
        assertNull(state.signedIn)
        assertNull(store.session)
    }

    @Test
    fun `a wrong class code is shown on the class code field`() {
        server.enqueue(json(failureJson("CLASS_CODE_NOT_FOUND"), 404))

        viewModel.register(wali)

        assertEquals(R.string.error_class_code_not_found, settled().fieldErrors[RegisterField.CLASS_CODE])
    }

    @Test
    fun `a taken NISN is shown on the NISN field`() {
        server.enqueue(json(failureJson("NISN_ALREADY_REGISTERED"), 409))

        viewModel.register(wali)

        assertEquals(R.string.error_nisn_taken, settled().fieldErrors[RegisterField.NISN])
    }

    @Test
    fun `an error that belongs to no field becomes a general message`() {
        server.enqueue(json(failureJson("INTERNAL_ERROR"), 500))

        viewModel.register(guru)
        val state = settled()

        assertEquals(R.string.error_server, state.error)
        assertTrue(state.fieldErrors.isEmpty())

        viewModel.errorShown()
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `a second tap while the first is running sends nothing more`() {
        server.enqueue(json(authJson("a", "r"), 201))

        viewModel.register(guru)
        viewModel.register(guru)
        settled()

        assertEquals(1, server.requestCount)
    }
}
