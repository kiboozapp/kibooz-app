package com.nthb.kibooz.data.repository

import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.VideoApi
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VideoRepositoryTest {

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
    fun `video CRUD routes serialize expected methods, paths, and bodies`() = runTest {
        server.enqueue(
            jsonResponse(
                """{"success":true,"data":{"videos":[{"id":"v1","title":"Berhitung","description":"Angka 1-10","videoUrl":"https://www.youtube.com/watch?v=e23kDLhj_qA","thumbnailUrl":null,"durationSeconds":180,"createdAt":"2026-11-25T01:00:00Z"}],"page":1,"limit":20,"total":1}}"""
            )
        )
        server.enqueue(
            jsonResponse(
                """{"success":true,"data":{"id":"v1","title":"Berhitung Lanjut","description":"","videoUrl":"https://www.youtube.com/watch?v=e23kDLhj_qA","thumbnailUrl":null,"durationSeconds":180,"createdAt":"2026-11-25T01:00:00Z"}}"""
            )
        )
        server.enqueue(jsonResponse("""{"success":true,"message":"Learning video deleted","data":null}"""))

        val client = testClient(server)
        val repo = VideoRepository(client.create(VideoApi::class.java), client.moshi)

        val listed = repo.videos("c1") as ApiResult.Success
        assertEquals(1, listed.data.videos.size)
        assertEquals("GET /api/v1/classes/c1/videos?page=1&limit=20", server.takeRequest().let { "${it.method} ${it.path}" })

        val updated = repo.updateVideo("c1", "v1", title = "Berhitung Lanjut", description = "") as ApiResult.Success
        assertEquals("Berhitung Lanjut", updated.data.title)
        val patchReq = server.takeRequest()
        assertEquals("PATCH /api/v1/classes/c1/videos/v1", "${patchReq.method} ${patchReq.path}")
        val patchBody = patchReq.body.readUtf8()
        assertTrue(patchBody.contains("\"title\":\"Berhitung Lanjut\""))
        assertTrue(patchBody.contains("\"description\":\"\""))
        assertFalse(patchBody.contains("\"thumbnailUrl\""))

        val deleted = repo.deleteVideo("c1", "v1")
        assertTrue(deleted is ApiResult.Success)
        assertEquals("DELETE /api/v1/classes/c1/videos/v1", server.takeRequest().let { "${it.method} ${it.path}" })
    }
}
