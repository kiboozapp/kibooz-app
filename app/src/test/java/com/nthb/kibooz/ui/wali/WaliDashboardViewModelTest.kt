package com.nthb.kibooz.ui.wali

import com.nthb.kibooz.R
import com.nthb.kibooz.data.remote.ApiEnvelope
import com.nthb.kibooz.data.remote.ChildList
import com.nthb.kibooz.data.remote.ChildSummary
import com.nthb.kibooz.data.remote.ClassApi
import com.nthb.kibooz.data.remote.ClassList
import com.nthb.kibooz.data.remote.ClassStudentList
import com.nthb.kibooz.data.remote.DashboardStudent
import com.nthb.kibooz.data.remote.EmptyData
import com.nthb.kibooz.data.remote.GuidanceApplyRequest
import com.nthb.kibooz.data.remote.PointsSummary
import com.nthb.kibooz.data.remote.WaliApi
import com.nthb.kibooz.data.remote.WaliDashboard
import com.nthb.kibooz.data.repository.ClassRepository
import com.nthb.kibooz.data.repository.WaliRepository
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
class WaliDashboardViewModelTest {

    private fun dashboard(id: String, name: String) = WaliDashboard(
        student = DashboardStudent(id, name, "c1", "Class A • TK", null, "00$id"),
        todayMood = null,
        pointsSummary = PointsSummary(100, 20, 2, 5, 3),
        recommendedGuidance = null
    )

    private class FakeWaliApi : WaliApi {
        val asked = mutableListOf<String?>()
        var reply: (String?) -> Response<ApiEnvelope<WaliDashboard>> = { error("not set") }

        override suspend fun dashboard(studentId: String?): Response<ApiEnvelope<WaliDashboard>> {
            asked += studentId
            return reply(studentId)
        }

        override suspend fun applyGuidance(
            idempotencyKey: String,
            body: GuidanceApplyRequest
        ): Response<ApiEnvelope<EmptyData>> = error("not used")
    }

    private class FakeClassApi(var children: List<ChildSummary>, var fail: Boolean = false) : ClassApi {
        override suspend fun listChildren(): Response<ApiEnvelope<ChildList>> {
            if (fail) throw IOException("offline")
            return Response.success(ApiEnvelope(true, null, ChildList(children)))
        }

        override suspend fun listClasses(page: Int, limit: Int): Response<ApiEnvelope<ClassList>> = error("not used")

        override suspend fun listStudents(classId: String, page: Int, limit: Int): Response<ApiEnvelope<ClassStudentList>> =
            error("not used")
    }

    private val waliApi = FakeWaliApi()
    private val classApi = FakeClassApi(
        listOf(ChildSummary("s1", "Amelia", "001", null, "c1", "Bunga"), ChildSummary("s2", "Budi", "002", null, "c1", "Bunga"))
    )
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    private fun viewModel() = WaliDashboardViewModel(WaliRepository(waliApi, moshi), ClassRepository(classApi, moshi))

    private fun <T> failure(code: Int, errorCode: String): Response<ApiEnvelope<T>> = Response.error(
        code,
        """{"success":false,"message":"x","errorCode":"$errorCode"}""".toResponseBody("application/json".toMediaType())
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        waliApi.reply = { id -> Response.success(ApiEnvelope(true, null, dashboard(id ?: "s1", if (id == "s2") "Budi" else "Amelia"))) }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `it loads the oldest child and the list of children together`() {
        val state = viewModel().state.value

        assertFalse(state.loading)
        assertEquals("Amelia", state.dashboard?.student?.fullName)
        assertEquals(2, state.children.size)
        assertEquals(listOf<String?>(null), waliApi.asked)
        assertNull(state.error)
    }

    @Test
    fun `choosing another child loads that child and reload keeps the choice`() {
        val viewModel = viewModel()

        viewModel.selectChild("s2")
        assertEquals("Budi", viewModel.state.value.dashboard?.student?.fullName)

        viewModel.load()
        assertEquals(listOf<String?>(null, "s2", "s2"), waliApi.asked)
    }

    @Test
    fun `a chosen child that was deleted falls back to the default child`() {
        val viewModel = viewModel()
        viewModel.selectChild("s2")
        waliApi.reply = { id ->
            if (id == "s2") failure(404, "STUDENT_NOT_FOUND") else Response.success(ApiEnvelope(true, null, dashboard("s1", "Amelia")))
        }

        viewModel.load()

        val state = viewModel.state.value
        assertEquals(listOf<String?>(null, "s2", "s2", null), waliApi.asked)
        assertEquals("Amelia", state.dashboard?.student?.fullName)
        assertNull(state.error)
        assertFalse(state.noChild)
        assertFalse(state.loading)

        viewModel.load()
        assertEquals("s1", waliApi.asked.last())
    }

    @Test
    fun `a parent without children gets a flag, not an error toast`() {
        waliApi.reply = { failure(404, "NO_CHILD_REGISTERED") }

        val state = viewModel().state.value

        assertTrue(state.noChild)
        assertNull(state.dashboard)
        assertNull(state.error)
    }

    @Test
    fun `the old dashboard stays when a reload goes offline`() {
        val viewModel = viewModel()
        waliApi.reply = { throw IOException("offline") }

        viewModel.load()

        assertEquals("Amelia", viewModel.state.value.dashboard?.student?.fullName)
        assertEquals(R.string.error_network, viewModel.state.value.error)
    }

    @Test
    fun `a failing children list does not hide the dashboard`() {
        classApi.fail = true

        val state = viewModel().state.value

        assertEquals("Amelia", state.dashboard?.student?.fullName)
        assertTrue(state.children.isEmpty())
        assertNull(state.error)
    }
}
