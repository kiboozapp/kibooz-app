package com.nthb.kibooz.ui.camera

import com.nthb.kibooz.R
import com.nthb.kibooz.data.remote.ApiEnvelope
import com.nthb.kibooz.data.remote.LogMoodBatchData
import com.nthb.kibooz.data.remote.LogMoodBatchItem
import com.nthb.kibooz.data.remote.LogMoodBatchRequest
import com.nthb.kibooz.data.remote.MoodApi
import com.nthb.kibooz.data.repository.MoodRepository
import com.nthb.kibooz.model.ScannedStudentMood
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class ReviewMoodViewModelTest {

    private class Call(val key: String, val request: LogMoodBatchRequest)

    /** Menjawab satu per satu dari [replies], dan 201 untuk semua entri jika antriannya habis. */
    private class FakeMoodApi : MoodApi {
        val calls = mutableListOf<Call>()
        val replies = ArrayDeque<() -> Response<ApiEnvelope<LogMoodBatchData>>>()

        override suspend fun logMoodBatch(
            idempotencyKey: String,
            body: LogMoodBatchRequest
        ): Response<ApiEnvelope<LogMoodBatchData>> {
            calls += Call(idempotencyKey, body)
            replies.removeFirstOrNull()?.let { return it() }

            val logs = body.entries.mapIndexed { i, e -> LogMoodBatchItem("log-$i", e.studentId, "2026-10-24T08:00:00Z") }
            return Response.success(201, ApiEnvelope(true, "saved", LogMoodBatchData(logs)))
        }
    }

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private lateinit var api: FakeMoodApi
    private lateinit var viewModel: ReviewMoodViewModel
    private val classId = "class-1"

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        api = FakeMoodApi()
        viewModel = ReviewMoodViewModel(MoodRepository(api, moshi))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun rows(count: Int, confidence: Float = 0.9f) = List(count) { i ->
        ScannedStudentMood("student-$i", "Siswa $i", 0, "SENANG", confidence, isConfirmed = false)
    }

    private fun failure(code: Int, errorCode: String, details: String = "null") = {
        Response.error<ApiEnvelope<LogMoodBatchData>>(
            code,
            """{"success":false,"message":"x","errorCode":"$errorCode","details":$details}"""
                .toResponseBody("application/json".toMediaType())
        )
    }

    private val offline = { throw IOException("offline") }

    @Test
    fun `saves a class in one batch and reports the count`() {
        viewModel.save(classId, rows(3))

        assertEquals(1, api.calls.size)
        assertEquals(classId, api.calls[0].request.classId)
        assertEquals(listOf("student-0", "student-1", "student-2"), api.calls[0].request.entries.map { it.studentId })
        assertEquals(3, viewModel.state.value.savedCount)
        assertFalse(viewModel.state.value.saving)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `more than 30 students are split into batches of 30 with their own keys`() {
        viewModel.save(classId, rows(31))

        assertEquals(listOf(30, 1), api.calls.map { it.request.entries.size })
        assertNotEquals(api.calls[0].key, api.calls[1].key)
        assertEquals(31, viewModel.state.value.savedCount)
    }

    @Test
    fun `a missing class is refused before any request`() {
        viewModel.save(null, rows(2))

        assertEquals(R.string.review_kelas_belum_dipilih, viewModel.state.value.error)
        assertTrue(api.calls.isEmpty())
    }

    @Test
    fun `nothing to save is refused before any request`() {
        viewModel.save(classId, emptyList())

        assertEquals(R.string.review_kosong, viewModel.state.value.error)
        assertTrue(api.calls.isEmpty())
    }

    @Test
    fun `an unconfirmed result under 75 percent blocks the save`() {
        viewModel.save(classId, rows(2, confidence = 0.6f))

        assertEquals(R.string.review_perlu_konfirmasi, viewModel.state.value.error)
        assertTrue(api.calls.isEmpty())
    }

    @Test
    fun `a confirmed low result is saved as the camera result`() {
        val confirmed = rows(1, confidence = 0.6f).onEach { it.isConfirmed = true }

        viewModel.save(classId, confirmed)

        assertEquals(0.6f, api.calls.single().request.entries.single().confidenceScore)
    }

    @Test
    fun `retrying after being offline sends the same key`() {
        api.replies += offline
        val list = rows(2)

        viewModel.save(classId, list)
        assertEquals(R.string.error_network, viewModel.state.value.error)
        viewModel.save(classId, list)

        assertEquals(2, api.calls.size)
        assertEquals(api.calls[0].key, api.calls[1].key)
        assertEquals(2, viewModel.state.value.savedCount)
    }

    @Test
    fun `a result the teacher changed after a failure gets a new key`() {
        api.replies += offline
        val list = rows(2)

        viewModel.save(classId, list)
        list[0].currentMood = "MARAH"
        viewModel.save(classId, list)

        assertNotEquals(api.calls[0].key, api.calls[1].key)
        assertEquals("MANUAL_INPUT", api.calls[1].request.entries[0].source.name)
    }

    @Test
    fun `a retry skips the batches that were already saved`() {
        val list = rows(31)
        api.replies += { Response.success(201, ApiEnvelope(true, null, LogMoodBatchData(emptyList()))) }
        api.replies += offline

        viewModel.save(classId, list)
        assertNull(viewModel.state.value.savedCount)
        viewModel.save(classId, list)

        assertEquals(listOf(30, 1, 1), api.calls.map { it.request.entries.size })
        assertEquals("student-30", api.calls[2].request.entries.single().studentId)
        assertEquals(api.calls[1].key, api.calls[2].key)
        assertEquals(31, viewModel.state.value.savedCount)
    }

    @Test
    fun `a full day names the students who have no room left`() {
        api.replies += failure(
            409,
            "MOOD_LOG_LIMIT_REACHED",
            """{"entries[1].studentId":"has the largest number of mood logs for that day"}"""
        )

        viewModel.save(classId, rows(3))

        val state = viewModel.state.value
        assertEquals(R.string.error_mood_log_limit, state.error)
        assertEquals(listOf("student-1"), state.limitReachedFor)
        assertNull(state.savedCount)
    }

    @Test
    fun `a rate limit shows the rate limit message and keeps the list`() {
        api.replies += failure(429, "RATE_LIMITED")

        viewModel.save(classId, rows(2))

        assertEquals(R.string.error_rate_limited, viewModel.state.value.error)
        assertTrue(viewModel.state.value.limitReachedFor.isEmpty())
    }

    @Test
    fun `errorShown clears the error so a rotation does not show it again`() {
        api.replies += offline
        viewModel.save(classId, rows(1))

        viewModel.errorShown()

        assertNull(viewModel.state.value.error)
    }
}
