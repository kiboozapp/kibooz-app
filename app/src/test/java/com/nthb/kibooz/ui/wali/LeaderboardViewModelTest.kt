package com.nthb.kibooz.ui.wali

import com.nthb.kibooz.R
import com.nthb.kibooz.data.remote.ApiEnvelope
import com.nthb.kibooz.data.remote.LeaderboardApi
import com.nthb.kibooz.data.remote.LeaderboardData
import com.nthb.kibooz.data.remote.RankEntry
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
        var reply: () -> Response<ApiEnvelope<LeaderboardData>> = { error("not set") }

        override suspend fun leaderboard(classId: String?): Response<ApiEnvelope<LeaderboardData>> {
            asked += classId
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
    fun `the old ranking stays when a reload goes offline`() {
        val viewModel = viewModel()
        api.reply = { throw IOException("offline") }

        viewModel.load()

        assertEquals(data, viewModel.state.value.data)
        assertEquals(R.string.error_network, viewModel.state.value.error)

        viewModel.errorShown()
        assertNull(viewModel.state.value.error)
    }
}
