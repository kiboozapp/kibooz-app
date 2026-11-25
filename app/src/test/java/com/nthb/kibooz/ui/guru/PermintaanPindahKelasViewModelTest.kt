package com.nthb.kibooz.ui.guru

import com.nthb.kibooz.R
import com.nthb.kibooz.data.remote.ApiEnvelope
import com.nthb.kibooz.data.remote.ClassMoveRequest
import com.nthb.kibooz.data.remote.ClassMoveRequestList
import com.nthb.kibooz.data.remote.EmptyData
import com.nthb.kibooz.data.remote.MoveRequestApi
import com.nthb.kibooz.data.remote.MoveRequestList
import com.nthb.kibooz.data.remote.OutageKind
import com.nthb.kibooz.data.repository.MoveRequestRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
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
class PermintaanPindahKelasViewModelTest {

    private class FakeMoveRequestApi : MoveRequestApi {
        var listReply: () -> Response<ApiEnvelope<ClassMoveRequestList>> = {
            Response.success(ApiEnvelope(true, null, ClassMoveRequestList(emptyList())))
        }
        var acceptReply: (String, String) -> Response<ApiEnvelope<EmptyData>> = { _, _ ->
            Response.success(ApiEnvelope(true, "ok", EmptyData()))
        }
        var rejectReply: (String, String) -> Response<ApiEnvelope<EmptyData>> = { _, _ ->
            Response.success(ApiEnvelope(true, "ok", EmptyData()))
        }
        var listCalls = 0
        val acceptedIds = mutableListOf<String>()
        val rejectedIds = mutableListOf<String>()

        override suspend fun listClassMoveRequests(classId: String): Response<ApiEnvelope<ClassMoveRequestList>> {
            listCalls++
            return listReply()
        }

        override suspend fun acceptClassMove(classId: String, requestId: String): Response<ApiEnvelope<EmptyData>> {
            acceptedIds += requestId
            return acceptReply(classId, requestId)
        }

        override suspend fun rejectClassMove(classId: String, requestId: String): Response<ApiEnvelope<EmptyData>> {
            rejectedIds += requestId
            return rejectReply(classId, requestId)
        }

        override suspend fun listChildMoveRequests(studentId: String): Response<ApiEnvelope<MoveRequestList>> =
            error("unused")

        override suspend fun cancelChildMoveRequest(studentId: String, requestId: String): Response<ApiEnvelope<EmptyData>> =
            error("unused")
    }

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val req1 = ClassMoveRequest("r1", "s1", "Adit Pratama", "2026-11-25T01:00:00Z")
    private val req2 = ClassMoveRequest("r2", "s2", "Budi Santoso", "2026-11-25T02:00:00Z")
    private lateinit var api: FakeMoveRequestApi

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        api = FakeMoveRequestApi().apply {
            listReply = { Response.success(ApiEnvelope(true, null, ClassMoveRequestList(listOf(req1, req2)))) }
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vm(classId: String = "c1") =
        PermintaanPindahKelasViewModel(MoveRequestRepository(api, moshi), classId)

    private fun errResponse(status: Int, code: String): Response<ApiEnvelope<EmptyData>> {
        val body = """{"success":false,"errorCode":"$code","message":"err"}"""
            .toResponseBody("application/json".toMediaType())
        return Response.error(status, body)
    }

    @Test
    fun `blank classId reports class gone without loading`() {
        val model = vm("")
        assertFalse(model.state.value.loading)
        assertEquals(R.string.error_class_gone, model.state.value.loadError)
        assertEquals(0, api.listCalls)
    }

    @Test
    fun `accept removes request, records change, and keeps other items`() {
        val model = vm()
        assertEquals(2, model.state.value.requests.size)

        model.accept(req1, "Adit diterima")

        assertEquals(listOf("r1"), api.acceptedIds)
        assertEquals(listOf(req2), model.state.value.requests)
        assertEquals("Adit diterima", model.state.value.noticeText)
        assertTrue(model.state.value.changed)

        model.noticeShown()
        assertNull(model.state.value.noticeText)
    }

    @Test
    fun `reject removes request and sets notice`() {
        val model = vm()
        model.reject(req2, "Permintaan Budi ditolak")

        assertEquals(listOf("r2"), api.rejectedIds)
        assertEquals(listOf(req1), model.state.value.requests)
        assertEquals("Permintaan Budi ditolak", model.state.value.noticeText)
        assertTrue(model.state.value.changed)
    }

    @Test
    fun `missing request triggers reload so stale card disappears`() {
        val model = vm()
        api.acceptReply = { _, _ -> errResponse(404, "MOVE_REQUEST_NOT_FOUND") }
        api.listReply = { Response.success(ApiEnvelope(true, null, ClassMoveRequestList(listOf(req2)))) }

        model.accept(req1, "ok")

        assertEquals(2, api.listCalls)
        assertEquals(R.string.error_move_request_not_found, model.state.value.errorNoticeRes)
        assertEquals(listOf(req2), model.state.value.requests)
        assertFalse(model.state.value.changed)
    }

    @Test
    fun `service outage maps cleanly to outage state`() {
        val body = "503 Service Unavailable"
            .toResponseBody("text/plain".toMediaType())
        api.listReply = { Response.error(503, body) }

        val model = vm()
        assertEquals(OutageKind.UNAVAILABLE, model.state.value.outage)
        assertEquals(R.string.error_unavailable, model.state.value.loadError)
    }
}
