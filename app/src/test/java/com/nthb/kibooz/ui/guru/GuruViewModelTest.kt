package com.nthb.kibooz.ui.guru

import com.nthb.kibooz.R
import com.nthb.kibooz.data.remote.ApiEnvelope
import com.nthb.kibooz.data.remote.ClassOverview
import com.nthb.kibooz.data.remote.DailyMoodDistribution
import com.nthb.kibooz.data.remote.DashboardTeacher
import com.nthb.kibooz.data.remote.GuruApi
import com.nthb.kibooz.data.remote.GuruDashboard
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
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class GuruViewModelTest {

    private class FakeGuruApi : GuruApi {
        var dashboard: GuruDashboard = sample()
        var requests = 0

        /** Jika terisi, permintaan berikutnya gagal dengan ini sekali saja. */
        var nextFailure: (() -> Response<ApiEnvelope<GuruDashboard>>)? = null

        override suspend fun dashboard(classId: String?): Response<ApiEnvelope<GuruDashboard>> {
            requests++
            nextFailure?.let { failure -> nextFailure = null; return failure() }
            return Response.success(ApiEnvelope(true, null, dashboard))
        }
    }

    private val api = FakeGuruApi()
    private lateinit var viewModel: GuruViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        viewModel = GuruViewModel(GuruRepository(api, moshi))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loads the summary and the teacher name`() {
        viewModel.load()

        val state = viewModel.state.value
        assertFalse(state.loading)
        assertNull(state.error)
        assertEquals("Siti Rahayu, S.Pd.", state.teacherName)
        assertEquals(92, state.summary?.attendancePercent)
        assertEquals(Emotion.SENANG, state.summary?.dominantMood)
    }

    @Test
    fun `starts out loading before the first load`() {
        assertTrue(viewModel.state.value.loading)
        assertNull(viewModel.state.value.summary)
    }

    @Test
    fun `a forbidden reply shows the forbidden message and no summary`() {
        api.nextFailure = httpError(403, "AUTH_FORBIDDEN")

        viewModel.load()

        val state = viewModel.state.value
        assertFalse(state.loading)
        assertEquals(R.string.error_forbidden, state.error)
        assertNull(state.summary)
    }

    @Test
    fun `no connection shows the network message`() {
        api.nextFailure = { throw IOException("offline") }

        viewModel.load()

        assertEquals(R.string.error_network, viewModel.state.value.error)
    }

    @Test
    fun `a failed reload keeps the summary that is already on screen`() {
        viewModel.load()
        api.nextFailure = { throw IOException("offline") }

        viewModel.load()

        val state = viewModel.state.value
        assertFalse(state.loading)
        assertEquals(R.string.error_network, state.error)
        assertEquals(92, state.summary?.attendancePercent)
        assertEquals("Siti Rahayu, S.Pd.", state.teacherName)
    }

    @Test
    fun `loading again after a failure clears the error`() {
        api.nextFailure = httpError(500, "INTERNAL_ERROR")
        viewModel.load()
        assertEquals(R.string.error_server, viewModel.state.value.error)

        viewModel.load()

        assertNull(viewModel.state.value.error)
        assertEquals(92, viewModel.state.value.summary?.attendancePercent)
        assertEquals(2, api.requests)
    }

    @Test
    fun `a newer summary replaces the old one`() {
        viewModel.load()
        api.dashboard = sample(present = 12, dominant = Emotion.BINGUNG)

        viewModel.load()

        assertEquals(50, viewModel.state.value.summary?.attendancePercent)
        assertEquals(Emotion.BINGUNG, viewModel.state.value.summary?.dominantMood)
    }

    private fun httpError(code: Int, errorCode: String): () -> Response<ApiEnvelope<GuruDashboard>> = {
        Response.error(
            code,
            """{"success":false,"message":"x","errorCode":"$errorCode"}""".toResponseBody("application/json".toMediaType())
        )
    }

    private companion object {
        fun sample(present: Int = 22, dominant: Emotion? = Emotion.SENANG) = GuruDashboard(
            teacher = DashboardTeacher("Siti Rahayu, S.Pd.", "198507152010032005", null),
            classOverview = ClassOverview("c1", "Class A (Mawar)", "KBZ48R2M", true, 24, present, dominant),
            dailyMoodDistribution = DailyMoodDistribution(senang = 14, sedih = 3, marah = 1, bingung = 4)
        )
    }
}
