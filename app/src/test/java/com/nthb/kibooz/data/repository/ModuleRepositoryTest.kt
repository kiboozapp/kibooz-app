package com.nthb.kibooz.data.repository

import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.ModuleApi
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ModuleRepositoryTest {

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
    fun `module CRUD routes serialize methods, paths, idempotency key, and payloads`() = runTest {
        server.enqueue(
            jsonResponse(
                """{"success":true,"data":{"modules":[{"id":"m1","meetingNumber":1,"title":"Mengenal Warna","description":"Dasar warna","status":"ongoing","scheduledAt":"2026-11-25T08:00:00Z","createdAt":"2026-11-25T01:00:00Z","updatedAt":"2026-11-25T01:00:00Z"}],"page":1,"limit":50,"total":1}}"""
            )
        )
        server.enqueue(
            jsonResponse(
                """{"success":true,"message":"Learning module created","data":{"id":"m2","meetingNumber":2,"title":"Kolase Kreatif","description":null,"status":"upcoming","scheduledAt":null,"createdAt":"2026-11-25T02:00:00Z","updatedAt":"2026-11-25T02:00:00Z"}}""",
                201
            )
        )
        server.enqueue(
            jsonResponse(
                """{"success":true,"message":"Learning module updated","data":{"id":"m2","meetingNumber":2,"title":"Kolase Lanjutan","description":"","status":"completed","scheduledAt":null,"createdAt":"2026-11-25T02:00:00Z","updatedAt":"2026-11-25T03:00:00Z"}}"""
            )
        )
        server.enqueue(jsonResponse("""{"success":true,"message":"Learning module deleted","data":null}"""))

        val client = testClient(server)
        val repo = ModuleRepository(client.create(ModuleApi::class.java), client.moshi)

        val listed = repo.modules("c1") as ApiResult.Success
        assertEquals(1, listed.data.modules.size)
        assertEquals("GET /api/v1/classes/c1/modules?page=1&limit=50", server.takeRequest().let { "${it.method} ${it.path}" })

        val created = repo.createModule("c1", 2, "  Kolase Kreatif  ", "  ", "upcoming") as ApiResult.Success
        assertEquals("Kolase Kreatif", created.data.title)
        val createReq = server.takeRequest()
        assertEquals("POST /api/v1/classes/c1/modules", "${createReq.method} ${createReq.path}")
        assertFalse(createReq.getHeader("Idempotency-Key").isNullOrBlank())
        val createBody = createReq.body.readUtf8()
        assertTrue(createBody.contains("\"meetingNumber\":2"))
        assertTrue(createBody.contains("\"title\":\"Kolase Kreatif\""))
        assertFalse(createBody.contains("\"description\""))

        val updated = repo.updateModule("c1", "m2", title = "Kolase Lanjutan", description = "", status = "completed") as ApiResult.Success
        assertEquals("completed", updated.data.status)
        val patchReq = server.takeRequest()
        assertEquals("PATCH /api/v1/classes/c1/modules/m2", "${patchReq.method} ${patchReq.path}")

        val deleted = repo.deleteModule("c1", "m2")
        assertTrue(deleted is ApiResult.Success)
        assertEquals("DELETE /api/v1/classes/c1/modules/m2", server.takeRequest().let { "${it.method} ${it.path}" })
    }
}
