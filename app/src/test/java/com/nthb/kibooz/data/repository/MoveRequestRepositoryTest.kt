package com.nthb.kibooz.data.repository

import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.MoveRequestApi
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MoveRequestRepositoryTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `guru class move endpoints call list, accept, and reject routes`() = runTest {
        server.enqueue(
            jsonResponse(
                """{"success":true,"data":{"requests":[{"id":"mr1","studentId":"st1","studentName":"Adit Pratama","requestedAt":"2026-11-25T01:00:00Z"}]}}"""
            )
        )
        server.enqueue(jsonResponse("""{"success":true,"message":"disetujui","data":{}}"""))
        server.enqueue(jsonResponse("""{"success":true,"message":"ditolak","data":{}}"""))

        val client = testClient(server)
        val repo = MoveRequestRepository(client.create(MoveRequestApi::class.java), client.moshi)

        val list = repo.classMoveRequests("c1") as ApiResult.Success
        assertEquals(1, list.data.size)
        assertEquals("Adit Pratama", list.data.first().studentName)
        assertEquals("GET /api/v1/guru/classes/c1/move-requests", "${server.takeRequest().let { "${it.method} ${it.path}" }}")

        val accept = repo.acceptClassMove("c1", "mr1")
        assertTrue(accept is ApiResult.Success)
        assertEquals("POST /api/v1/guru/classes/c1/move-requests/mr1/accept", "${server.takeRequest().let { "${it.method} ${it.path}" }}")

        val reject = repo.rejectClassMove("c1", "mr2")
        assertTrue(reject is ApiResult.Success)
        assertEquals("POST /api/v1/guru/classes/c1/move-requests/mr2/reject", "${server.takeRequest().let { "${it.method} ${it.path}" }}")
    }

    @Test
    fun `wali child move endpoints list and cancel requests`() = runTest {
        server.enqueue(
            jsonResponse(
                """{"success":true,"data":{"requests":[{"id":"mr9","studentId":"st1","classId":"c2","className":"Kelas Sakura","status":"pending","requestedAt":"2026-11-25T01:00:00Z"}]}}"""
            )
        )
        server.enqueue(jsonResponse("""{"success":true,"message":"dibatalkan","data":{}}"""))

        val client = testClient(server)
        val repo = MoveRequestRepository(client.create(MoveRequestApi::class.java), client.moshi)

        val childList = repo.childMoveRequests("st1") as ApiResult.Success
        assertEquals(1, childList.data.size)
        assertEquals("Kelas Sakura", childList.data.first().className)
        assertEquals("pending", childList.data.first().status)
        assertEquals("GET /api/v1/wali/children/st1/move-requests", "${server.takeRequest().let { "${it.method} ${it.path}" }}")

        val cancelled = repo.cancelChildMoveRequest("st1", "mr9")
        assertTrue(cancelled is ApiResult.Success)
        assertEquals("DELETE /api/v1/wali/children/st1/move-requests/mr9", "${server.takeRequest().let { "${it.method} ${it.path}" }}")
    }
}
