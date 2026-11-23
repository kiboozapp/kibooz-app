package com.nthb.kibooz.ui.guru

import com.nthb.kibooz.R
import com.nthb.kibooz.data.remote.ApiEnvelope
import com.nthb.kibooz.data.remote.ClassOverview
import com.nthb.kibooz.data.remote.DailyMoodDistribution
import com.nthb.kibooz.data.remote.GuruApi
import com.nthb.kibooz.data.remote.GuruDashboard
import com.nthb.kibooz.data.remote.MoodAnalytics
import com.nthb.kibooz.data.remote.OutageKind
import com.nthb.kibooz.data.remote.TeacherInfo
import com.nthb.kibooz.data.repository.GuruRepository
import com.nthb.kibooz.ml.Emotion
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class GuruDashboardOutageTest {

    private val dashboard = GuruDashboard(
        teacher = TeacherInfo("Siti Rahayu", "1987", null),
        classOverview = ClassOverview("c1", "Class A (Bunga)", "ABC123", true, 20, 18, Emotion.SENANG),
        dailyMoodDistribution = DailyMoodDistribution(10, 2, 1, 5)
    )

    private class FakeGuruApi : GuruApi {
        var reply: () -> Response<ApiEnvelope<GuruDashboard>> = { error("not set") }

        override suspend fun dashboard(classId: String?): Response<ApiEnvelope<GuruDashboard>> = reply()

        override suspend fun moodAnalytics(classId: String?, range: String): Response<ApiEnvelope<MoodAnalytics>> =
            error("not used")
    }

    private val api = FakeGuruApi()
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val repository = GuruRepository(api, moshi)

    private fun ok(): Response<ApiEnvelope<GuruDashboard>> = Response.success(ApiEnvelope(true, null, dashboard))

    private fun offline(): Response<ApiEnvelope<GuruDashboard>> = throw IOException("offline")

    /** Balasan dari proxy: kode 502 dan body bukan JSON. */
    private fun proxyDown(): Response<ApiEnvelope<GuruDashboard>> =
        Response.error(502, "<html>Bad gateway</html>".toResponseBody("text/html".toMediaType()))

    private fun apiError(code: Int, errorCode: String): Response<ApiEnvelope<GuruDashboard>> = Response.error(
        code,
        """{"success":false,"message":"x","errorCode":"$errorCode"}""".toResponseBody("application/json".toMediaType())
    )

    private fun serverTimeout(retryAfter: Int): Response<ApiEnvelope<GuruDashboard>> = Response.error(
        """{"success":false,"message":"x","errorCode":"SERVER_TIMEOUT"}""".toResponseBody("application/json".toMediaType()),
        okhttp3.Response.Builder()
            .code(503)
            .message("Service Unavailable")
            .protocol(Protocol.HTTP_1_1)
            .request(Request.Builder().url("http://localhost/").build())
            .header("Retry-After", retryAfter.toString())
            .build()
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `no connection raises an offline outage that waits 30 seconds`() {
        api.reply = ::offline

        val viewModel = GuruDashboardViewModel(repository)

        val outage = viewModel.state.value.outage
        assertEquals(OutageKind.OFFLINE, outage?.kind)
        assertEquals(30L, outage?.waitSeconds)
        assertEquals(R.string.error_network, viewModel.state.value.error)
    }

    @Test
    fun `a 502 from the proxy raises an unavailable outage`() {
        api.reply = ::proxyDown

        val viewModel = GuruDashboardViewModel(repository)

        assertEquals(OutageKind.UNAVAILABLE, viewModel.state.value.outage?.kind)
        assertEquals(30L, viewModel.state.value.outage?.waitSeconds)
    }

    @Test
    fun `an outage keeps the numbers already on screen`() {
        api.reply = ::ok
        val viewModel = GuruDashboardViewModel(repository)
        api.reply = ::offline

        viewModel.load()

        assertEquals(dashboard, viewModel.state.value.dashboard)
        assertNotNull(viewModel.state.value.outage)
    }

    @Test
    fun `the wait grows with every automatic retry while the outage lasts`() {
        api.reply = ::offline
        val viewModel = GuruDashboardViewModel(repository)
        assertEquals(30L, viewModel.state.value.outage?.waitSeconds)

        viewModel.retryAfterOutage()
        assertEquals(60L, viewModel.state.value.outage?.waitSeconds)

        viewModel.retryAfterOutage()
        assertEquals(120L, viewModel.state.value.outage?.waitSeconds)
    }

    @Test
    fun `a manual load starts the wait over`() {
        api.reply = ::offline
        val viewModel = GuruDashboardViewModel(repository)
        viewModel.retryAfterOutage()
        viewModel.retryAfterOutage()
        assertEquals(120L, viewModel.state.value.outage?.waitSeconds)

        viewModel.load()

        assertEquals(30L, viewModel.state.value.outage?.waitSeconds)
    }

    @Test
    fun `a successful retry clears the outage and resets the wait`() {
        api.reply = ::offline
        val viewModel = GuruDashboardViewModel(repository)
        viewModel.retryAfterOutage()
        assertEquals(60L, viewModel.state.value.outage?.waitSeconds)

        api.reply = ::ok
        viewModel.retryAfterOutage()

        assertNull(viewModel.state.value.outage)
        assertNull(viewModel.state.value.error)
        assertEquals(dashboard, viewModel.state.value.dashboard)

        api.reply = ::offline
        viewModel.load()
        assertEquals(30L, viewModel.state.value.outage?.waitSeconds)
    }

    @Test
    fun `an error from the API is not an outage`() {
        api.reply = { apiError(404, "CLASS_NOT_FOUND") }

        val viewModel = GuruDashboardViewModel(repository)

        assertNull(viewModel.state.value.outage)
        assertNotNull(viewModel.state.value.error)
    }

    @Test
    fun `a 503 with Retry-After waits that long plus a short random pause`() {
        api.reply = { serverTimeout(retryAfter = 10) }

        val viewModel = GuruDashboardViewModel(repository)

        val outage = viewModel.state.value.outage
        assertEquals(OutageKind.UNAVAILABLE, outage?.kind)
        assertTrue("wait was ${outage?.waitSeconds}", outage?.waitSeconds in 10L..15L)
    }
}
