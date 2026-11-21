package com.nthb.kibooz.ui.wali

import com.nthb.kibooz.R
import com.nthb.kibooz.data.remote.ApiEnvelope
import com.nthb.kibooz.data.remote.ScanClaimData
import com.nthb.kibooz.data.remote.ScanClaimRequest
import com.nthb.kibooz.data.remote.TrashApi
import com.nthb.kibooz.data.remote.TrashStats
import com.nthb.kibooz.data.remote.TrashStatsStudent
import com.nthb.kibooz.data.repository.TrashRepository
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
class TrashStatsViewModelTest {

    private class FakeTrashApi : TrashApi {
        val asked = mutableListOf<String?>()
        var reply: () -> Response<ApiEnvelope<TrashStats>> = { error("not set") }

        override suspend fun scanClaim(idempotencyKey: String, body: ScanClaimRequest): Response<ApiEnvelope<ScanClaimData>> =
            error("not used")

        override suspend fun stats(studentId: String?): Response<ApiEnvelope<TrashStats>> {
            asked += studentId
            return reply()
        }
    }

    private val api = FakeTrashApi()
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val stats = TrashStats(TrashStatsStudent("s1", "Amelia"), 120, 40, 3, 10, emptyList(), 2, 5, 3)

    private fun viewModel(studentId: String? = null) = TrashStatsViewModel(TrashRepository(api, moshi), studentId)

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        api.reply = { Response.success(ApiEnvelope(true, null, stats)) }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `it loads the child it was given on creation`() {
        val state = viewModel("s1").state.value

        assertFalse(state.loading)
        assertEquals(stats, state.stats)
        assertEquals(listOf<String?>("s1"), api.asked)
    }

    @Test
    fun `a parent without children gets a flag and no error`() {
        api.reply = {
            Response.error(
                404,
                """{"success":false,"message":"x","errorCode":"NO_CHILD_REGISTERED"}""".toResponseBody("application/json".toMediaType())
            )
        }

        val state = viewModel().state.value

        assertTrue(state.noChild)
        assertNull(state.error)
        assertNull(state.stats)
    }

    @Test
    fun `old numbers stay when a reload goes offline`() {
        val viewModel = viewModel()
        api.reply = { throw IOException("offline") }

        viewModel.load()

        assertEquals(stats, viewModel.state.value.stats)
        assertEquals(R.string.error_network, viewModel.state.value.error)

        viewModel.errorShown()
        assertNull(viewModel.state.value.error)
    }
}
