package com.nthb.kibooz.ui.guru

import com.nthb.kibooz.R
import com.nthb.kibooz.data.remote.ApiEnvelope
import com.nthb.kibooz.data.remote.ChildList
import com.nthb.kibooz.data.remote.ClassApi
import com.nthb.kibooz.data.remote.ClassList
import com.nthb.kibooz.data.remote.ClassOverview
import com.nthb.kibooz.data.remote.ClassStudent
import com.nthb.kibooz.data.remote.ClassStudentList
import com.nthb.kibooz.data.remote.ClassSummary
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
        var dashboardReplyFor: ((String?) -> Response<ApiEnvelope<GuruDashboard>>)? = null
        var analyticsReply: () -> Response<ApiEnvelope<MoodAnalytics>> = { error("not set") }
        var analyticsReplyFor: ((String?) -> Response<ApiEnvelope<MoodAnalytics>>)? = null
        var dashboardCalls = 0
        val dashboardClassIds = mutableListOf<String?>()
        val analyticsClassIds = mutableListOf<String?>()
        val ranges = mutableListOf<String>()

        override suspend fun dashboard(classId: String?): Response<ApiEnvelope<GuruDashboard>> {
            dashboardCalls++
            dashboardClassIds += classId
            return dashboardReplyFor?.invoke(classId) ?: dashboardReply()
        }

        override suspend fun moodAnalytics(classId: String?, range: String): Response<ApiEnvelope<MoodAnalytics>> {
            analyticsClassIds += classId
            ranges += range
            return analyticsReplyFor?.invoke(classId) ?: analyticsReply()
        }
    }

    private class FakeClassApi(var students: List<ClassStudent> = emptyList(), var fail: Boolean = false) : ClassApi {
        val classIds = mutableListOf<String>()
        var classes: List<ClassSummary>? = null
        val studentsByClass = mutableMapOf<String, List<ClassStudent>>()

        override suspend fun listStudents(classId: String, page: Int, limit: Int): Response<ApiEnvelope<ClassStudentList>> {
            classIds += classId
            if (fail) throw IOException("offline")
            val items = studentsByClass[classId] ?: students
            return Response.success(ApiEnvelope(true, null, ClassStudentList(page, limit, items.size, items)))
        }

        override suspend fun listClasses(page: Int, limit: Int): Response<ApiEnvelope<ClassList>> {
            val items = classes ?: error("not used")
            return Response.success(ApiEnvelope(true, null, ClassList(page, limit, items.size, items)))
        }

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

    @Test
    fun `dashboard loads active classes and selectClass switches active metrics`() {
        val c1 = ClassSummary("c1", "Kelas Matahari", "TK-A", "TK", "2025/2026", "ABC123", true, 16, "2026-01-01T00:00:00Z")
        val c2 = ClassSummary("c2", "Kelas Sakura", "TK-A", "TK", "2025/2026", "SAK456", true, 15, "2026-01-02T00:00:00Z")
        val archived = ClassSummary("c3", "Arsip", "TK-A", "TK", "2024/2025", "OLD999", false, 8, "2025-01-01T00:00:00Z", "2025-12-31T00:00:00Z")
        classApi.classes = listOf(c1, c2, archived)

        val dash2 = GuruDashboard(
            teacher = dashboard.teacher,
            classOverview = ClassOverview("c2", "Kelas Sakura", "SAK456", true, 15, 3, Emotion.BINGUNG),
            dailyMoodDistribution = DailyMoodDistribution(2, 1, 0, 0)
        )
        guruApi.dashboardReplyFor = { requestedClassId ->
            Response.success(ApiEnvelope(true, null, if (requestedClassId == "c2") dash2 else dashboard))
        }

        val viewModel = GuruDashboardViewModel(guruRepository, classRepository)

        assertEquals(listOf("c1", "c2"), viewModel.state.value.classes.map { it.id })
        assertEquals("c1", viewModel.state.value.selectedClassId)
        assertEquals(dashboard, viewModel.state.value.dashboard)

        viewModel.selectClass("c2")

        assertEquals("c2", viewModel.state.value.selectedClassId)
        assertEquals(dash2, viewModel.state.value.dashboard)
        assertEquals(listOf<String?>(null, "c2"), guruApi.dashboardClassIds)
    }

    @Test
    fun `mood stats load class options and switching class refreshes all datasets`() {
        val c1 = ClassSummary("c1", "Kelas Matahari", "TK-A", "TK", "2025/2026", "ABC123", true, 2, "2026-01-01T00:00:00Z")
        val c2 = ClassSummary("c2", "Kelas Sakura", "TK-A", "TK", "2025/2026", "SAK456", true, 1, "2026-01-02T00:00:00Z")
        classApi.classes = listOf(c1, c2)
        classApi.studentsByClass["c1"] = listOf(student("1", Emotion.SENANG), student("2", null))
        classApi.studentsByClass["c2"] = listOf(student("3", Emotion.MARAH))

        val dash2 = GuruDashboard(
            teacher = dashboard.teacher,
            classOverview = ClassOverview("c2", "Kelas Sakura", "SAK456", true, 1, 1, Emotion.MARAH),
            dailyMoodDistribution = DailyMoodDistribution(0, 0, 0, 1)
        )
        val analytics2 = MoodAnalytics(
            donutSummary = emptyList(),
            weeklyTrend = listOf(WeeklyPoint("Monday", 20)),
            monthlyDistribution = listOf(MonthlyCount(Emotion.MARAH, 1))
        )
        guruApi.dashboardReplyFor = { requestedClassId ->
            Response.success(ApiEnvelope(true, null, if (requestedClassId == "c2") dash2 else dashboard))
        }
        guruApi.analyticsReplyFor = { requestedClassId ->
            Response.success(ApiEnvelope(true, null, if (requestedClassId == "c2") analytics2 else analytics))
        }

        val viewModel = MoodStatsViewModel(guruRepository, classRepository, loadClassOptions = true)

        assertEquals(listOf("c1", "c2"), viewModel.state.value.classes.map { it.id })
        assertEquals("Class A (Bunga)", viewModel.state.value.className)
        assertEquals(2, viewModel.state.value.students.size)

        viewModel.selectClass("c2")

        val state = viewModel.state.value
        assertEquals("c2", state.selectedClassId)
        assertEquals("Kelas Sakura", state.className)
        assertEquals(DailyMoodDistribution(0, 0, 0, 1), state.today)
        assertEquals(1, state.weekly.size)
        assertEquals(1, state.monthly.first { it.mood == Emotion.MARAH }.count)
        assertEquals(listOf("Siswa 3"), state.students.map { it.fullName })
        assertEquals(listOf<String?>(null, "c2"), guruApi.analyticsClassIds)
        assertEquals(listOf("c1", "c2"), classApi.classIds)
    }
}
