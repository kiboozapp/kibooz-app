package com.nthb.kibooz.ui.guru

import com.nthb.kibooz.R
import com.nthb.kibooz.data.remote.ApiEnvelope
import com.nthb.kibooz.data.remote.ChildList
import com.nthb.kibooz.data.remote.ClassApi
import com.nthb.kibooz.data.remote.ClassList
import com.nthb.kibooz.data.remote.ClassOverview
import com.nthb.kibooz.data.remote.ClassStudent
import com.nthb.kibooz.data.remote.ClassStudentList
import com.nthb.kibooz.data.remote.DailyMoodDistribution
import com.nthb.kibooz.data.remote.GuruApi
import com.nthb.kibooz.data.remote.GuruDashboard
import com.nthb.kibooz.data.remote.MonthlyCount
import com.nthb.kibooz.data.remote.MoodAnalytics
import com.nthb.kibooz.data.remote.TeacherInfo
import com.nthb.kibooz.data.remote.WeeklyPoint
import com.nthb.kibooz.data.repository.ClassRepository
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class GuruViewModelsTest {

    private val dashboard = GuruDashboard(
        teacher = TeacherInfo("Siti Rahayu", "1987", null),
        classOverview = ClassOverview("c1", "Class A (Bunga)", "ABC123", true, 20, 18, Emotion.SENANG),
        dailyMoodDistribution = DailyMoodDistribution(10, 2, 1, 5)
    )

    private val analytics = MoodAnalytics(
        donutSummary = emptyList(),
        weeklyTrend = listOf(WeeklyPoint("Monday", 60), WeeklyPoint("Tuesday", 80)),
        monthlyDistribution = listOf(MonthlyCount(Emotion.SENANG, 12), MonthlyCount(Emotion.SEDIH, 3))
    )

    private class FakeGuruApi : GuruApi {
        var dashboardReply: () -> Response<ApiEnvelope<GuruDashboard>> = { error("not set") }
        var analyticsReply: () -> Response<ApiEnvelope<MoodAnalytics>> = { error("not set") }
        var dashboardCalls = 0
        val ranges = mutableListOf<String>()

        override suspend fun dashboard(classId: String?): Response<ApiEnvelope<GuruDashboard>> {
            dashboardCalls++
            return dashboardReply()
        }

        override suspend fun moodAnalytics(classId: String?, range: String): Response<ApiEnvelope<MoodAnalytics>> {
            ranges += range
            return analyticsReply()
        }
    }

    private class FakeClassApi(var students: List<ClassStudent> = emptyList(), var fail: Boolean = false) : ClassApi {
        val classIds = mutableListOf<String>()

        override suspend fun listStudents(classId: String, page: Int, limit: Int): Response<ApiEnvelope<ClassStudentList>> {
            classIds += classId
            if (fail) throw IOException("offline")
            return Response.success(ApiEnvelope(true, null, ClassStudentList(page, limit, students.size, students)))
        }

        override suspend fun listClasses(page: Int, limit: Int): Response<ApiEnvelope<ClassList>> = error("not used")

        override suspend fun listChildren(): Response<ApiEnvelope<ChildList>> = error("not used")
    }

    private val guruApi = FakeGuruApi()
    private val classApi = FakeClassApi()
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val guruRepository = GuruRepository(guruApi, moshi)
    private val classRepository = ClassRepository(classApi, moshi)

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        guruApi.dashboardReply = { Response.success(ApiEnvelope(true, null, dashboard)) }
        guruApi.analyticsReply = { Response.success(ApiEnvelope(true, null, analytics)) }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun <T> failure(code: Int, errorCode: String): Response<ApiEnvelope<T>> = Response.error(
        code,
        """{"success":false,"message":"x","errorCode":"$errorCode"}""".toResponseBody("application/json".toMediaType())
    )

    private fun student(id: String, mood: Emotion?) = ClassStudent(id, "Siswa $id", "00$id", null, 0, 0, 1, mood)

    @Test
    fun `the dashboard loads on creation and shows the server numbers`() {
        val viewModel = GuruDashboardViewModel(guruRepository)

        val state = viewModel.state.value
        assertFalse(state.loading)
        assertEquals(dashboard, state.dashboard)
        assertNull(state.error)
    }

    @Test
    fun `a failed dashboard shows the mapped error and the retry works`() {
        guruApi.dashboardReply = { failure(404, "CLASS_NOT_FOUND") }
        val viewModel = GuruDashboardViewModel(guruRepository)

        assertFalse(viewModel.state.value.loading)
        assertNull(viewModel.state.value.dashboard)
        assertNotNull(viewModel.state.value.error)

        guruApi.dashboardReply = { Response.success(ApiEnvelope(true, null, dashboard)) }
        viewModel.load()

        assertEquals(dashboard, viewModel.state.value.dashboard)
        assertNull(viewModel.state.value.error)
        assertEquals(2, guruApi.dashboardCalls)
    }

    @Test
    fun `old numbers stay on screen when a reload fails`() {
        val viewModel = GuruDashboardViewModel(guruRepository)
        guruApi.dashboardReply = { throw IOException("offline") }

        viewModel.load()

        assertEquals(dashboard, viewModel.state.value.dashboard)
        assertEquals(R.string.error_network, viewModel.state.value.error)
    }

    @Test
    fun `the error is cleared once it was shown`() {
        guruApi.dashboardReply = { throw IOException("offline") }
        val viewModel = GuruDashboardViewModel(guruRepository)

        viewModel.errorShown()

        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `mood stats combine the dashboard, the monthly analytics and the roster`() {
        classApi.students = listOf(student("1", Emotion.SENANG), student("2", null))

        val viewModel = MoodStatsViewModel(guruRepository, classRepository)

        val state = viewModel.state.value
        assertFalse(state.loading)
        assertEquals("Class A (Bunga)", state.className)
        assertEquals(DailyMoodDistribution(10, 2, 1, 5), state.today)
        assertEquals(2, state.weekly.size)
        assertEquals(12, state.monthly.first { it.mood == Emotion.SENANG }.count)
        assertEquals(2, state.students.size)
        assertEquals(listOf("monthly"), guruApi.ranges)
        assertEquals(listOf("c1"), classApi.classIds)
        assertNull(state.error)
    }

    @Test
    fun `one failing part does not hide the others`() {
        classApi.fail = true

        val viewModel = MoodStatsViewModel(guruRepository, classRepository)

        val state = viewModel.state.value
        assertEquals(DailyMoodDistribution(10, 2, 1, 5), state.today)
        assertEquals(2, state.weekly.size)
        assertEquals(emptyList<ClassStudent>(), state.students)
        assertEquals(R.string.error_network, state.error)
    }

    @Test
    fun `without a dashboard the roster is not requested`() {
        guruApi.dashboardReply = { failure(404, "CLASS_NOT_FOUND") }

        val viewModel = MoodStatsViewModel(guruRepository, classRepository)

        assertEquals(emptyList<String>(), classApi.classIds)
        assertNull(viewModel.state.value.today)
        assertNotNull(viewModel.state.value.error)
    }
}
