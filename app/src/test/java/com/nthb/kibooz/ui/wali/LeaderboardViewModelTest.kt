package com.nthb.kibooz.ui.wali

import com.nthb.kibooz.R
import com.nthb.kibooz.data.remote.ApiEnvelope
import com.nthb.kibooz.data.remote.ChildList
import com.nthb.kibooz.data.remote.ClassApi
import com.nthb.kibooz.data.remote.ClassList
import com.nthb.kibooz.data.remote.ClassStudentList
import com.nthb.kibooz.data.remote.ClassSummary
import com.nthb.kibooz.data.remote.LeaderboardApi
import com.nthb.kibooz.data.remote.LeaderboardData
import com.nthb.kibooz.data.remote.LeaderboardPeriod
import com.nthb.kibooz.data.remote.RankEntry
import com.nthb.kibooz.data.repository.ClassRepository
import com.nthb.kibooz.data.repository.LeaderboardRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class LeaderboardViewModelTest {

    private class FakeApi : LeaderboardApi {
        val asked = mutableListOf<String?>()
        val askedPeriods = mutableListOf<String?>()
        var reply: () -> Response<ApiEnvelope<LeaderboardData>> = { error("not set") }

        override suspend fun leaderboard(classId: String?, period: String?): Response<ApiEnvelope<LeaderboardData>> {
            asked += classId
            askedPeriods += period
            return reply()
        }
    }

    private val api = FakeApi()
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val data = LeaderboardData(
        podium = listOf(RankEntry(1, "Novita", 90, null)),
        rankings = listOf(RankEntry(2, "Desi", 60, null))
    )

    private fun viewModel(classId: String? = null) = LeaderboardViewModel(LeaderboardRepository(api, moshi), classId)

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        api.reply = { Response.success(ApiEnvelope(true, null, data)) }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `it loads on creation for the class it was given`() {
        val state = viewModel("c1").state.value

        assertFalse(state.loading)
        assertEquals(data, state.data)
        assertFalse(state.empty)
        assertEquals(listOf<String?>("c1"), api.asked)
    }

    @Test
    fun `a class with nobody in it is reported as empty`() {
        api.reply = { Response.success(ApiEnvelope(true, null, LeaderboardData(emptyList(), emptyList()))) }

        assertTrue(viewModel().state.value.empty)
    }

    @Test
    fun `all ranks lists the podium first and then the rest`() {
        val state = viewModel().state.value

        assertEquals(listOf("Novita", "Desi"), state.allRanks.map { it.studentName })
    }

    @Test
    fun `all ranks is empty before any data arrives`() {
        api.reply = { throw IOException("offline") }

        assertTrue(viewModel().state.value.allRanks.isEmpty())
    }

    @Test
    fun `it starts on the semester ranking and sends no period`() {
        val state = viewModel().state.value

        assertEquals(LeaderboardPeriod.SEMESTER, state.period)
        assertEquals(listOf<String?>(null), api.askedPeriods)
    }

    @Test
    fun `choosing a period loads that period for the same class`() {
        val viewModel = viewModel("c1")

        viewModel.selectPeriod(LeaderboardPeriod.WEEK)

        assertEquals(LeaderboardPeriod.WEEK, viewModel.state.value.period)
        assertEquals(data, viewModel.state.value.data)
        assertEquals(listOf<String?>("c1", "c1"), api.asked)
        assertEquals(listOf<String?>(null, "week"), api.askedPeriods)
    }

    @Test
    fun `choosing the period that is already shown loads nothing`() {
        val viewModel = viewModel()

        viewModel.selectPeriod(LeaderboardPeriod.SEMESTER)

        assertEquals(1, api.asked.size)
    }

    @Test
    fun `a new period that fails does not keep the ranking of the old one`() {
        val viewModel = viewModel()
        api.reply = { throw IOException("offline") }

        viewModel.selectPeriod(LeaderboardPeriod.TODAY)

        assertNull(viewModel.state.value.data)
        assertEquals(LeaderboardPeriod.TODAY, viewModel.state.value.period)
        assertEquals(R.string.error_network, viewModel.state.value.error)
    }

    @Test
    fun `retrying after a failed period asks for that period again`() {
        val viewModel = viewModel()
        api.reply = { throw IOException("offline") }
        viewModel.selectPeriod(LeaderboardPeriod.MONTH)
        api.reply = { Response.success(ApiEnvelope(true, null, data)) }

        viewModel.load()

        assertEquals(data, viewModel.state.value.data)
        assertNull(viewModel.state.value.error)
        assertEquals(listOf<String?>(null, "month", "month"), api.askedPeriods)
    }

    @Test
    fun `the old ranking stays when a reload goes offline`() {
        val viewModel = viewModel()
        api.reply = { throw IOException("offline") }

        viewModel.load()

        assertEquals(data, viewModel.state.value.data)
        assertEquals(R.string.error_network, viewModel.state.value.error)

        viewModel.errorShown()
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `a reload shows the newer ranking and keeps the class and period`() {
        val viewModel = viewModel("c1")
        viewModel.selectPeriod(LeaderboardPeriod.WEEK)
        val newer = LeaderboardData(
            podium = listOf(RankEntry(1, "Desi", 75, null)),
            rankings = listOf(RankEntry(2, "Novita", 70, null))
        )
        api.reply = { Response.success(ApiEnvelope(true, null, newer)) }

        viewModel.load()

        assertEquals(newer, viewModel.state.value.data)
        assertEquals(listOf<String?>("c1", "c1", "c1"), api.asked)
        assertEquals(listOf<String?>(null, "week", "week"), api.askedPeriods)
    }

    @Test
    fun `guru leaderboard loads classes and selectClass reloads ranking while keeping period`() {
        val c1 = ClassSummary("c1", "Matahari", "TK-A", "TK", "2025/2026", "ABC123", true, 16, "2026-01-01T00:00:00Z")
        val c2 = ClassSummary("c2", "Sakura", "TK-A", "TK", "2025/2026", "SAK456", true, 15, "2026-01-02T00:00:00Z")
        val fakeClassApi = object : ClassApi {
            override suspend fun listStudents(classId: String, page: Int, limit: Int): Response<ApiEnvelope<ClassStudentList>> =
                error("not used")

            override suspend fun listClasses(page: Int, limit: Int): Response<ApiEnvelope<ClassList>> =
                Response.success(ApiEnvelope(true, null, ClassList(page, limit, 2, listOf(c1, c2))))

            override suspend fun listChildren(): Response<ApiEnvelope<ChildList>> = error("not used")
        }
        val viewModel = LeaderboardViewModel(
            repository = LeaderboardRepository(api, moshi),
            initialClassId = "c1",
            classRepository = ClassRepository(fakeClassApi, moshi)
        )

        viewModel.selectPeriod(LeaderboardPeriod.MONTH)
        val sakuraData = LeaderboardData(
            podium = listOf(RankEntry(1, "Sakura 1", 70, null)),
            rankings = emptyList()
        )
        api.reply = { Response.success(ApiEnvelope(true, null, sakuraData)) }

        viewModel.selectClass("c2")

        assertEquals(listOf("c1", "c2"), viewModel.state.value.classes.map { it.id })
        assertEquals("c2", viewModel.state.value.selectedClassId)
        assertEquals(LeaderboardPeriod.MONTH, viewModel.state.value.period)
        assertEquals(sakuraData, viewModel.state.value.data)
        assertEquals(listOf<String?>("c1", "c1", "c2"), api.asked)
        assertEquals(listOf<String?>(null, "month", "month"), api.askedPeriods)
    }

    @Test
    fun `syncClass only reloads when a different class id arrives`() {
        val viewModel = viewModel("c1")

        viewModel.syncClass("c1")
        assertEquals(listOf<String?>("c1"), api.asked)

        viewModel.syncClass("c2")
        assertEquals(listOf<String?>("c1", "c2"), api.asked)
        assertEquals("c2", viewModel.state.value.selectedClassId)
    }
}
