package com.nthb.kibooz.data.repository

import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.NotificationApi
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NotificationRepositoryTest {

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
    fun `notification inbox, read single, and read-all hit expected endpoints`() = runTest {
        server.enqueue(
            jsonResponse(
                """{"success":true,"data":{"notifications":[{"id":"n1","kind":"mood_logged","title":"Mood Baru","body":"Adit senang","referenceType":"mood_log","referenceId":"s1","readAt":null,"createdAt":"2026-11-25T08:00:00Z"}],"unreadCount":1,"page":1,"limit":30,"total":1}}"""
            )
        )
        server.enqueue(jsonResponse("""{"success":true,"message":"Notification marked as read","data":null}"""))
        server.enqueue(jsonResponse("""{"success":true,"message":"All notifications marked as read","data":null}"""))

        val client = testClient(server)
        val repo = NotificationRepository(client.create(NotificationApi::class.java), client.moshi)

        val listed = repo.notifications(unreadOnly = true) as ApiResult.Success
        assertEquals(1, listed.data.unreadCount)
        assertEquals("Mood Baru", listed.data.notifications.first().title)
        assertEquals(
            "GET /api/v1/notifications?unreadOnly=true&page=1&limit=30",
            server.takeRequest().let { "${it.method} ${it.path}" }
        )

        val single = repo.markRead("n1")
        assertTrue(single is ApiResult.Success)
        assertEquals("POST /api/v1/notifications/n1/read", server.takeRequest().let { "${it.method} ${it.path}" })

        val all = repo.markAllRead()
        assertTrue(all is ApiResult.Success)
        assertEquals("POST /api/v1/notifications/read-all", server.takeRequest().let { "${it.method} ${it.path}" })
    }
}
