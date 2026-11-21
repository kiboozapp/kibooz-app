package com.nthb.kibooz.ui.camera

import com.nthb.kibooz.R
import com.nthb.kibooz.data.remote.ApiEnvelope
import com.nthb.kibooz.data.remote.ChildList
import com.nthb.kibooz.data.remote.ChildSummary
import com.nthb.kibooz.data.remote.ClassApi
import com.nthb.kibooz.data.remote.ClassList
import com.nthb.kibooz.data.remote.ClassStudentList
import com.nthb.kibooz.data.remote.ScanClaimData
import com.nthb.kibooz.data.remote.ScanClaimRequest
import com.nthb.kibooz.data.remote.TrashApi
import com.nthb.kibooz.data.remote.TrashStats
import com.nthb.kibooz.data.repository.ClassRepository
import com.nthb.kibooz.data.repository.TrashRepository
import com.nthb.kibooz.ml.TrashCategory
import com.nthb.kibooz.ml.TrashResult
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class TrashScanViewModelTest {

    private class Claim(val key: String, val request: ScanClaimRequest)

    private class FakeTrashApi : TrashApi {
        val claims = mutableListOf<Claim>()
        val replies = ArrayDeque<() -> Response<ApiEnvelope<ScanClaimData>>>()

        /** Jika terisi, klaim menunggu sampai ini selesai, untuk menguji keadaan saat klaim berjalan. */
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun scanClaim(idempotencyKey: String, body: ScanClaimRequest): Response<ApiEnvelope<ScanClaimData>> {
            claims += Claim(idempotencyKey, body)
            gate?.await()
            replies.removeFirstOrNull()?.let { return it() }
            return Response.success(ApiEnvelope(true, null, ScanClaimData(10, 120, 40, 3, 4)))
        }

        override suspend fun stats(studentId: String?): Response<ApiEnvelope<TrashStats>> = error("tidak dipakai")
    }

    private class FakeClassApi(var children: List<ChildSummary> = emptyList()) : ClassApi {
        var childRequests = 0

        override suspend fun listChildren(): Response<ApiEnvelope<ChildList>> {
            childRequests++
            return Response.success(ApiEnvelope(true, null, ChildList(children)))
        }

        override suspend fun listClasses(page: Int, limit: Int): Response<ApiEnvelope<ClassList>> = error("tidak dipakai")

        override suspend fun listStudents(classId: String, page: Int, limit: Int): Response<ApiEnvelope<ClassStudentList>> =
            error("tidak dipakai")
    }

    private val trashApi = FakeTrashApi()
    private val pauses = mutableListOf<Long>()
    private val ticks = mutableListOf<Int?>()
    private val classApi = FakeClassApi()
    private lateinit var viewModel: TrashScanViewModel

    private fun child(id: String, name: String) = ChildSummary(id, name, "00$id", null, "c1", "Bunga")

    private fun failure(code: Int, errorCode: String): () -> Response<ApiEnvelope<ScanClaimData>> = {
        Response.error(
            code,
            """{"success":false,"message":"x","errorCode":"$errorCode"}""".toResponseBody("application/json".toMediaType())
        )
    }

    private val offline: () -> Response<ApiEnvelope<ScanClaimData>> = { throw IOException("offline") }

    /** 429 dengan atau tanpa `Retry-After`, dan dengan atau tanpa body JSON (misalnya dari proxy). */
    private fun rateLimited(retryAfter: String? = null, json: Boolean = true): () -> Response<ApiEnvelope<ScanClaimData>> = {
        val raw = okhttp3.Response.Builder()
            .request(okhttp3.Request.Builder().url("http://localhost/").build())
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .code(429)
            .message("Too Many Requests")
            .headers(if (retryAfter == null) headersOf() else headersOf("Retry-After", retryAfter))
            .build()
        val body = if (json) """{"success":false,"message":"x","errorCode":"RATE_LIMITED"}""" else "<html>Too many</html>"
        Response.error(body.toResponseBody("application/json".toMediaType()), raw)
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        viewModel = TrashScanViewModel(
            ClassRepository(classApi, moshi),
            TrashRepository(trashApi, moshi),
            pause = { millis ->
                pauses += millis
                ticks += viewModel.state.value.retryInSeconds
            }
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun readyToClaim(result: TrashResult = TrashResult(TrashCategory.ORGANIK, 0.9f)) {
        viewModel.start("child-1")
        viewModel.onDetected(result)
    }

    @Test
    fun `a child from the launcher is used without asking for the children`() {
        viewModel.start("child-1")

        assertEquals("child-1", viewModel.state.value.childId)
        assertFalse(viewModel.state.value.loadingChildren)
        assertEquals(0, classApi.childRequests)
    }

    @Test
    fun `a parent with one child gets it selected`() {
        classApi.children = listOf(child("s1", "Ani"))

        viewModel.start(null)

        assertEquals("s1", viewModel.state.value.childId)
        assertTrue(viewModel.state.value.childChoices.isEmpty())
    }

    @Test
    fun `a parent with several children has to choose`() {
        classApi.children = listOf(child("s1", "Ani"), child("s2", "Budi"))

        viewModel.start("")
        assertNull(viewModel.state.value.childId)
        assertEquals(listOf("Ani", "Budi"), viewModel.state.value.childChoices.map { it.fullName })

        viewModel.selectChild("s2")

        assertEquals("s2", viewModel.state.value.childId)
        assertTrue(viewModel.state.value.childChoices.isEmpty())
    }

    @Test
    fun `a parent without children is told so`() {
        viewModel.start(null)

        assertEquals(R.string.error_no_child, viewModel.state.value.error)
        assertNull(viewModel.state.value.childId)
    }

    @Test
    fun `claiming before anything is detected is refused without a request`() {
        viewModel.start("child-1")

        viewModel.claim()

        assertEquals(R.string.sampah_belum_terdeteksi, viewModel.state.value.error)
        assertTrue(trashApi.claims.isEmpty())
    }

    @Test
    fun `claiming without a chosen child is refused without a request`() {
        classApi.children = listOf(child("s1", "Ani"), child("s2", "Budi"))
        viewModel.start(null)
        viewModel.onDetected(TrashResult(TrashCategory.ORGANIK, 0.9f))

        viewModel.claim()

        assertEquals(R.string.sampah_anak_belum_dipilih, viewModel.state.value.error)
        assertTrue(trashApi.claims.isEmpty())
    }

    @Test
    fun `a confident detection is claimed with its category and score and shows the points from the server`() {
        readyToClaim(TrashResult(TrashCategory.ANORGANIK, 0.91f))

        viewModel.claim()

        val claim = trashApi.claims.single()
        assertEquals("child-1", claim.request.studentId)
        assertEquals(TrashCategory.ANORGANIK, claim.request.trashType)
        assertEquals(0.91f, claim.request.confidenceScore)
        val state = viewModel.state.value
        assertEquals(10, state.claimed?.pointsAdded)
        assertEquals(4, state.claimed?.remainingDailyScans)
        assertFalse(state.claiming)
        assertNull(state.error)
    }

    @Test
    fun `75 percent exactly is accepted without asking`() {
        readyToClaim(TrashResult(TrashCategory.ORGANIK, 0.75f))

        viewModel.claim()

        assertFalse(viewModel.state.value.askCategory)
        assertEquals(1, trashApi.claims.size)
    }

    @Test
    fun `below 75 percent asks for the category and sends nothing yet`() {
        readyToClaim(TrashResult(TrashCategory.B3, 0.6f))

        viewModel.claim()

        assertTrue(viewModel.state.value.askCategory)
        assertTrue(trashApi.claims.isEmpty())
    }

    @Test
    fun `the manual choice is sent with the score the model had`() {
        readyToClaim(TrashResult(TrashCategory.B3, 0.6f))
        viewModel.claim()

        viewModel.claimManual(TrashCategory.ORGANIK)

        val claim = trashApi.claims.single()
        assertEquals(TrashCategory.ORGANIK, claim.request.trashType)
        assertEquals(0.6f, claim.request.confidenceScore)
        assertFalse(viewModel.state.value.askCategory)
        assertNotNull(viewModel.state.value.claimed)
    }

    @Test
    fun `an unsure item can be marked as B3 by hand`() {
        readyToClaim(TrashResult(TrashCategory.ANORGANIK, 0.46f))
        viewModel.claim()
        assertTrue(viewModel.state.value.askCategory)

        viewModel.claimManual(TrashCategory.B3)

        assertEquals(TrashCategory.B3, trashApi.claims.single().request.trashType)
    }

    @Test
    fun `the manual choice offers all three categories`() {
        assertEquals(TrashCategory.entries.toSet(), MANUAL_TRASH_CHOICES.toSet())
        assertEquals(TrashCategory.B3, MANUAL_TRASH_CHOICES.last())
    }

    @Test
    fun `cancelling the manual choice sends nothing and closes the dialog`() {
        readyToClaim(TrashResult(TrashCategory.B3, 0.6f))
        viewModel.claim()

        viewModel.dismissCategoryChoice()

        assertFalse(viewModel.state.value.askCategory)
        assertTrue(trashApi.claims.isEmpty())
    }

    @Test
    fun `a claim that failed offline is retried with the same key and the same item`() {
        trashApi.replies += offline
        readyToClaim(TrashResult(TrashCategory.ORGANIK, 0.9f))

        viewModel.claim()
        assertEquals(R.string.error_network, viewModel.state.value.error)
        assertTrue(viewModel.state.value.canRetry)

        // Kamera sudah melihat benda lain, tetapi percobaan ulang tetap klaim yang gagal tadi
        viewModel.onDetected(TrashResult(TrashCategory.ANORGANIK, 0.95f))
        viewModel.claim()

        assertEquals(2, trashApi.claims.size)
        assertEquals(trashApi.claims[0].key, trashApi.claims[1].key)
        assertEquals(TrashCategory.ORGANIK, trashApi.claims[1].request.trashType)
        assertNotNull(viewModel.state.value.claimed)
    }

    @Test
    fun `a server error and a busy request can be retried`() {
        trashApi.replies += failure(503, "INTERNAL_ERROR")
        readyToClaim()

        viewModel.claim()

        assertTrue(viewModel.state.value.canRetry)

        trashApi.replies += failure(409, "IDEMPOTENCY_IN_PROGRESS")
        viewModel.claim()

        assertTrue(viewModel.state.value.canRetry)
        assertEquals(R.string.error_request_in_progress, viewModel.state.value.error)
    }

    @Test
    fun `the daily limit is final and the next claim gets a new key`() {
        trashApi.replies += failure(429, "TRASH_DAILY_LIMIT_REACHED")
        readyToClaim()

        viewModel.claim()

        assertEquals(R.string.error_trash_daily_limit, viewModel.state.value.error)
        assertFalse(viewModel.state.value.canRetry)

        viewModel.claim()

        assertNotEquals(trashApi.claims[0].key, trashApi.claims[1].key)
    }

    @Test
    fun `a validation error is not retried with the same request`() {
        trashApi.replies += failure(400, "VALIDATION_ERROR")
        readyToClaim()

        viewModel.claim()

        assertEquals(R.string.error_validation, viewModel.state.value.error)
        assertFalse(viewModel.state.value.canRetry)
    }

    @Test
    fun `a child who is not the parents is reported as not found`() {
        trashApi.replies += failure(404, "STUDENT_NOT_FOUND")
        readyToClaim()

        viewModel.claim()

        assertEquals(R.string.error_not_found, viewModel.state.value.error)
        assertFalse(viewModel.state.value.canRetry)
    }

    @Test
    fun `a new detection is ignored while a claim is running`() {
        val gate = CompletableDeferred<Unit>()
        trashApi.gate = gate
        readyToClaim(TrashResult(TrashCategory.ORGANIK, 0.9f))

        viewModel.claim()
        assertTrue(viewModel.state.value.claiming)
        viewModel.onDetected(TrashResult(TrashCategory.B3, 0.95f))

        assertEquals(TrashCategory.ORGANIK, viewModel.state.value.detected?.category)

        // Klaim kedua saat yang pertama berjalan juga diabaikan
        viewModel.claim()
        gate.complete(Unit)

        assertEquals(1, trashApi.claims.size)
        assertFalse(viewModel.state.value.claiming)
    }

    @Test
    fun `a claim worth zero points is still shown as claimed`() {
        trashApi.replies += { Response.success(ApiEnvelope(true, null, ScanClaimData(0, 120, 40, 3, 3))) }
        readyToClaim(TrashResult(TrashCategory.B3, 0.9f))

        viewModel.claim()

        assertEquals(0, viewModel.state.value.claimed?.pointsAdded)
        assertEquals(3, viewModel.state.value.claimed?.remainingDailyScans)
    }

    @Test
    fun `scanning again clears the card and the old detection`() {
        readyToClaim()
        viewModel.claim()

        viewModel.scanAgain()

        val state = viewModel.state.value
        assertNull(state.claimed)
        assertNull(state.detected)
        assertEquals("child-1", state.childId)
    }

    @Test
    fun `changing the child drops a claim that was waiting for a retry`() {
        trashApi.replies += offline
        readyToClaim()
        viewModel.claim()

        viewModel.selectChild("child-2")
        viewModel.onDetected(TrashResult(TrashCategory.ORGANIK, 0.9f))
        viewModel.claim()

        assertEquals("child-2", trashApi.claims[1].request.studentId)
        assertNotEquals(trashApi.claims[0].key, trashApi.claims[1].key)
    }

    @Test
    fun `errorShown clears the error but keeps the retry`() {
        trashApi.replies += offline
        readyToClaim()
        viewModel.claim()

        viewModel.errorShown()

        assertNull(viewModel.state.value.error)
        assertTrue(viewModel.state.value.canRetry)
    }

    @Test
    fun `a rate limit waits for Retry-After and resends the same claim with the same key`() {
        trashApi.replies += rateLimited("2")
        readyToClaim(TrashResult(TrashCategory.ORGANIK, 0.9f))

        viewModel.claim()

        assertEquals(2, trashApi.claims.size)
        assertEquals(trashApi.claims[0].key, trashApi.claims[1].key)
        assertEquals(listOf(1000L, 1000L), pauses)
        assertEquals(listOf<Int?>(2, 1), ticks)
        assertNotNull(viewModel.state.value.claimed)
        assertNull(viewModel.state.value.retryInSeconds)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `a proxy 429 without a body waits 30 then 60 seconds`() {
        trashApi.replies += rateLimited(json = false)
        trashApi.replies += rateLimited(json = false)
        readyToClaim()

        viewModel.claim()

        assertEquals(3, trashApi.claims.size)
        assertEquals(90, pauses.size)
        assertNotNull(viewModel.state.value.claimed)
    }

    @Test
    fun `the daily limit is never waited for`() {
        trashApi.replies += failure(429, "TRASH_DAILY_LIMIT_REACHED")
        readyToClaim()

        viewModel.claim()

        assertTrue(pauses.isEmpty())
        assertEquals(1, trashApi.claims.size)
        assertEquals(R.string.error_trash_daily_limit, viewModel.state.value.error)
    }

    @Test
    fun `after three automatic retries the claim stays retryable by hand`() {
        repeat(4) { trashApi.replies += rateLimited("1") }
        readyToClaim()

        viewModel.claim()

        assertEquals(4, trashApi.claims.size)
        assertEquals(R.string.error_rate_limited, viewModel.state.value.error)
        assertTrue(viewModel.state.value.canRetry)
        assertNull(viewModel.state.value.retryInSeconds)
        assertFalse(viewModel.state.value.claiming)
    }
}
