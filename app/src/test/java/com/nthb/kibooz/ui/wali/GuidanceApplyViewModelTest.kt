package com.nthb.kibooz.ui.wali

import com.nthb.kibooz.R
import com.nthb.kibooz.data.remote.ApiEnvelope
import com.nthb.kibooz.data.remote.EmptyData
import com.nthb.kibooz.data.remote.GuidanceApplyRequest
import com.nthb.kibooz.data.remote.WaliApi
import com.nthb.kibooz.data.remote.WaliDashboard
import com.nthb.kibooz.data.repository.WaliRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.Headers.Companion.headersOf
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
class GuidanceApplyViewModelTest {

    private class Sent(val key: String, val request: GuidanceApplyRequest)

    private class FakeWaliApi : WaliApi {
        val sent = mutableListOf<Sent>()
        val replies = ArrayDeque<() -> Response<ApiEnvelope<EmptyData>>>()
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun dashboard(studentId: String?): Response<ApiEnvelope<WaliDashboard>> = error("not used")

        override suspend fun applyGuidance(idempotencyKey: String, body: GuidanceApplyRequest): Response<ApiEnvelope<EmptyData>> {
            sent += Sent(idempotencyKey, body)
            gate?.await()
            replies.removeFirstOrNull()?.let { return it() }
            return Response.success(ApiEnvelope(true, "Guidance applied", null))
        }
    }

    private val api = FakeWaliApi()
    private val pauses = mutableListOf<Long>()
    private lateinit var viewModel: GuidanceApplyViewModel

    private fun failure(code: Int, errorCode: String): () -> Response<ApiEnvelope<EmptyData>> = {
        Response.error(
            code,
            """{"success":false,"message":"x","errorCode":"$errorCode"}""".toResponseBody("application/json".toMediaType())
        )
    }

    private val offline: () -> Response<ApiEnvelope<EmptyData>> = { throw IOException("offline") }

    private fun rateLimited(retryAfter: String): () -> Response<ApiEnvelope<EmptyData>> = {
        val raw = okhttp3.Response.Builder()
            .request(okhttp3.Request.Builder().url("http://localhost/").build())
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .code(429)
            .message("Too Many Requests")
            .headers(headersOf("Retry-After", retryAfter))
            .build()
        Response.error(
            """{"success":false,"message":"x","errorCode":"RATE_LIMITED"}""".toResponseBody("application/json".toMediaType()),
            raw
        )
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        viewModel = GuidanceApplyViewModel(WaliRepository(api, moshi), pause = { pauses += it })
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a successful report marks the guidance as applied`() {
        viewModel.apply("g1", "s1", "Sudah dipeluk")

        val state = viewModel.state.value
        assertTrue(state.applied)
        assertFalse(state.sending)
        assertNull(state.error)
        assertEquals("g1", api.sent.single().request.guidanceId)
        assertEquals("Sudah dipeluk", api.sent.single().request.parentNotes)
    }

    @Test
    fun `once applied a second tap sends nothing`() {
        viewModel.apply("g1", "s1", "")
        viewModel.apply("g1", "s1", "")

        assertEquals(1, api.sent.size)
    }

    @Test
    fun `a tap while sending is ignored`() {
        api.gate = CompletableDeferred()

        viewModel.apply("g1", "s1", "")
        assertTrue(viewModel.state.value.sending)
        viewModel.apply("g1", "s1", "")

        assertEquals(1, api.sent.size)
        api.gate!!.complete(Unit)
    }

    @Test
    fun `without a guidance or a child nothing is sent`() {
        viewModel.apply(null, "s1", "")
        assertEquals(R.string.panduan_tidak_ada, viewModel.state.value.error)

        viewModel.apply("g1", " ", "")
        assertEquals(R.string.panduan_tidak_ada, viewModel.state.value.error)
        assertEquals(0, api.sent.size)
    }

    @Test
    fun `notes over 500 characters are refused before any request`() {
        viewModel.apply("g1", "s1", "x".repeat(501))

        assertEquals(R.string.form_catatan_terlalu_panjang, viewModel.state.value.error)
        assertEquals(0, api.sent.size)
    }

    @Test
    fun `a network failure can be retried with the same key and the first notes`() {
        api.replies += offline

        viewModel.apply("g1", "s1", "Catatan pertama")
        assertEquals(R.string.error_network, viewModel.state.value.error)
        assertTrue(viewModel.state.value.canRetry)

        viewModel.apply("g1", "s1", "diabaikan")

        assertEquals(2, api.sent.size)
        assertEquals(api.sent[0].key, api.sent[1].key)
        assertEquals("Catatan pertama", api.sent[1].request.parentNotes)
        assertTrue(viewModel.state.value.applied)
    }

    @Test
    fun `a server error can be retried but a missing guidance cannot`() {
        api.replies += failure(500, "INTERNAL_ERROR")
        viewModel.apply("g1", "s1", "")
        assertTrue(viewModel.state.value.canRetry)

        api.replies += failure(404, "GUIDANCE_NOT_FOUND")
        viewModel.apply("g1", "s1", "")
        assertFalse(viewModel.state.value.canRetry)
        assertEquals(R.string.error_not_found, viewModel.state.value.error)

        // The failed key is dropped, so the next report is a new action
        api.replies += { Response.success(ApiEnvelope(true, null, null)) }
        viewModel.apply("g1", "s1", "")
        assertEquals(3, api.sent.size)
        assertTrue(api.sent[2].key != api.sent[1].key)
    }

    @Test
    fun `a rate limit waits for Retry-After and resends with the same key`() {
        api.replies += rateLimited("2")

        viewModel.apply("g1", "s1", "")

        assertEquals(2, api.sent.size)
        assertEquals(api.sent[0].key, api.sent[1].key)
        assertEquals(listOf(1_000L, 1_000L), pauses)
        assertTrue(viewModel.state.value.applied)
    }

    @Test
    fun `the error is cleared once it was shown`() {
        api.replies += failure(404, "GUIDANCE_NOT_FOUND")
        viewModel.apply("g1", "s1", "")

        viewModel.errorShown()

        assertNull(viewModel.state.value.error)
    }
}
