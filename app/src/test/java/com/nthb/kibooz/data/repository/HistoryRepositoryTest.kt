package com.nthb.kibooz.data.repository

import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.HistoryApi
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class HistoryRepositoryTest {

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
    fun `guru and wali history endpoints parse timelines with filters`() = runTest {
        server.enqueue(
            jsonResponse(
                """{"success":true,"data":{"events":[{"id":"h1","type":"mood","classId":"c1","className":"Kelompok B","studentId":"s1","studentName":"Amelia","actorName":"Ibu Sari","moodType":"SENANG","confidenceScore":0.95,"title":"Amelia: Senang","description":"Baik","occurredAt":"2026-11-24T08:00:00Z"}],"page":1,"limit":30,"total":1}}"""
            )
        )
        server.enqueue(
            jsonResponse(
                """{"success":true,"data":{"student":{"id":"s1","fullName":"Amelia","className":"Kelompok B"},"events":[{"id":"wh1","type":"trash","trashType":"ORGANIK","pointsAwarded":10,"title":"ORGANIK (+10 points)","occurredAt":"2026-11-23T09:00:00Z"}],"page":1,"limit":30,"total":1}}"""
            )
        )

        val client = testClient(server)
        val repo = HistoryRepository(client.create(HistoryApi::class.java), client.moshi)

        val guruRes = repo.guruHistory(classId = "c1", type = "mood") as ApiResult.Success
        assertEquals(1, guruRes.data.total)
        assertEquals("Amelia: Senang", guruRes.data.events.first().title)
        assertEquals(
            "GET /api/v1/guru/history?classId=c1&type=mood&page=1&limit=30",
            server.takeRequest().let { "${it.method} ${it.path}" }
        )

        val waliRes = repo.waliChildHistory(studentId = "s1", type = "trash") as ApiResult.Success
        assertEquals("Amelia", waliRes.data.student.fullName)
        assertEquals(10, waliRes.data.events.first().pointsAwarded)
        assertEquals(
            "GET /api/v1/wali/children/s1/history?type=trash&page=1&limit=30",
            server.takeRequest().let { "${it.method} ${it.path}" }
        )
    }
}
