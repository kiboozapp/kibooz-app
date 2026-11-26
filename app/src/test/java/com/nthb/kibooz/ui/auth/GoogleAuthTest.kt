package com.nthb.kibooz.ui.auth

import com.nthb.kibooz.R
import com.nthb.kibooz.data.InMemorySessionStore
import com.nthb.kibooz.data.authJson
import com.nthb.kibooz.data.failureJson
import com.nthb.kibooz.data.remote.ApiClient
import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.AuthApi
import com.nthb.kibooz.data.repository.AuthRepository
import com.nthb.kibooz.data.repository.GoogleSignInOutcome
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
class GoogleAuthTest {

    @get:Rule
    val timeout: Timeout = Timeout(30, TimeUnit.SECONDS)

    private lateinit var server: MockWebServer
    private lateinit var store: InMemorySessionStore
    private lateinit var repository: AuthRepository
    private lateinit var authViewModel: AuthViewModel
    private lateinit var registerViewModel: RegisterViewModel

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
        authViewModel = AuthViewModel(repository, busyRetryMillis = 0)
        registerViewModel = RegisterViewModel(repository)
    }

    @After
    fun tearDown() {
        server.shutdown()
        Dispatchers.resetMain()
    }

    private fun json(body: String, code: Int = 200) = MockResponse().setResponseCode(code).setBody(body)

    @Test
    fun `googleLogin persists session and device token for existing user`() = runBlocking {
        store.writeDeviceToken("ani@example.com", "prev-device")
        server.enqueue(
            json(
                """
                {"success":true,"data":{
                  "needsRegistration":false,
                  "token":"g-access","refreshToken":"g-refresh","deviceToken":"new-device",
                  "user":{"id":"u1","email":"ani@example.com","fullName":"Ani","role":"WALI","avatarUrl":null}
                }}
                """.trimIndent()
            )
        )

        val res = repository.googleLogin("google-id-token", Role.WALI, emailHint = " Ani@Example.com ")

        val outcome = (res as ApiResult.Success).data as GoogleSignInOutcome.SignedIn
        assertEquals("ani@example.com", outcome.user.email)
        assertEquals("g-access", store.session?.accessToken)
        assertEquals("new-device", store.readDeviceToken("ani@example.com"))

        val recorded = server.takeRequest()
        assertEquals("/api/v1/auth/google", recorded.path)
        assertTrue(recorded.body.readUtf8().contains("\"deviceToken\":\"prev-device\""))
    }

    @Test
    fun `AuthViewModel routes unregistered Google account to onboarding without saving session`() = runBlocking {
        server.enqueue(
            json(
                """
                {"success":true,"data":{
                  "needsRegistration":true,
                  "googleProfile":{"email":"baru@gmail.com","fullName":"Ibu Baru","avatarUrl":"https://example.com/a.jpg"}
                }}
                """.trimIndent()
            )
        )

        authViewModel.loginWithGoogle("token-xyz", Role.GURU, "baru@gmail.com")
        val state = authViewModel.state.first { !it.loading }

        assertNull(state.signedIn)
        assertNull(state.error)
        assertNull(store.session)
        assertEquals("baru@gmail.com", state.googleOnboarding?.profile?.email)
        assertEquals("Ibu Baru", state.googleOnboarding?.profile?.fullName)
        assertEquals(Role.GURU, state.googleOnboarding?.role)

        authViewModel.googleOnboardingHandled()
        assertNull(authViewModel.state.value.googleOnboarding)
    }

    @Test
    fun `AuthViewModel maps AUTH_ROLE_MISMATCH to clear Indonesian message`() = runBlocking {
        server.enqueue(json(failureJson("AUTH_ROLE_MISMATCH"), 409))

        authViewModel.loginWithGoogle("token-xyz", Role.WALI)
        val state = authViewModel.state.first { !it.loading }

        assertEquals(R.string.error_role_mismatch, state.error)
        assertNull(state.signedIn)
    }

    @Test
    fun `RegisterRules skips password validation during Google onboarding and submits to google endpoint`() = runBlocking {
        val form = RegisterForm(
            role = Role.WALI,
            fullName = "Ibu Baru",
            email = "baru@gmail.com",
            password = "",
            classCode = "ABC12345",
            childName = "Budi Kecil",
            nisn = "1234567890",
            googleIdToken = "id-token-123"
        )

        val validationErrors = RegisterRules.validate(form)
        assertFalse(validationErrors.containsKey(RegisterField.PASSWORD))
        assertTrue(validationErrors.isEmpty())

        server.enqueue(json(authJson("g-reg-access", "g-reg-refresh"), 201))

        registerViewModel.register(form)
        val state = registerViewModel.state.first { !it.loading }

        assertNotNull(state.signedIn)
        assertEquals("g-reg-access", store.session?.accessToken)
        val req = server.takeRequest()
        assertEquals("/api/v1/auth/google/register", req.path)
        assertTrue(req.body.readUtf8().contains("\"idToken\":\"id-token-123\""))
    }
}
