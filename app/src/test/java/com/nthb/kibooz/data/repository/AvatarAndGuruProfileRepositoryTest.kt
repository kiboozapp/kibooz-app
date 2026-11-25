package com.nthb.kibooz.data.repository

import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.GuruProfileApi
import java.io.File
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AvatarAndGuruProfileRepositoryTest {

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
    fun `uploadAvatar sends multipart jpeg and optional studentId`() = runTest {
        server.enqueue(jsonResponse("""{"success":true,"data":{"avatarUrl":"https://cdn.example/a.jpg"}}"""))
        val client = testClient(server)
        val repo = AccountRepository(client.create(com.nthb.kibooz.data.remote.AccountApi::class.java), client.moshi)

        val temp = File.createTempFile("avatar-test-", ".jpg").apply {
            writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()))
            deleteOnExit()
        }

        val withChild = repo.uploadAvatar(temp, studentId = " st-9 ")
        assertTrue(withChild is ApiResult.Success)
        assertEquals("https://cdn.example/a.jpg", (withChild as ApiResult.Success).data)

        val req1 = server.takeRequest()
        assertEquals("POST", req1.method)
        assertEquals("/api/v1/users/avatar", req1.path)
        val body1 = req1.body.readUtf8()
        assertTrue(body1.contains("name=\"file\""))
        assertTrue(body1.contains("image/jpeg"))
        assertTrue(body1.contains("name=\"studentId\""))
        assertTrue(body1.contains("st-9"))

        server.enqueue(jsonResponse("""{"success":true,"data":{"avatarUrl":"https://cdn.example/guru.jpg"}}"""))
        val selfUpload = repo.uploadAvatar(temp, studentId = null)
        assertEquals("https://cdn.example/guru.jpg", (selfUpload as ApiResult.Success).data)

        val req2 = server.takeRequest()
        val body2 = req2.body.readUtf8()
        assertTrue(body2.contains("name=\"file\""))
        assertFalse(body2.contains("name=\"studentId\""))
    }

    @Test
    fun `guru profile detail and PUT update trim inputs and decode payload`() = runTest {
        val detailJson = """
            {
              "success": true,
              "data": {
                "id": "g1",
                "fullName": "Ibu Siti Aminah",
                "nip": "198705152010012001",
                "schoolName": "TK Harapan",
                "avatarUrl": "https://cdn.example/siti.jpg",
                "email": "guru.kibooz1@gmail.com",
                "phoneNumber": "08123456789",
                "address": "Jl. Mawar 10",
                "joinedAt": "2026-08-01T07:00:00Z"
              }
            }
        """.trimIndent()
        server.enqueue(jsonResponse(detailJson))
        server.enqueue(jsonResponse(detailJson))

        val client = testClient(server)
        val repo = GuruProfileRepository(client.create(GuruProfileApi::class.java), client.moshi)

        val detail = repo.profileDetail() as ApiResult.Success
        assertEquals("198705152010012001", detail.data.nip)
        assertEquals("guru.kibooz1@gmail.com", detail.data.email)
        val getReq = server.takeRequest()
        assertEquals("GET", getReq.method)
        assertEquals("/api/v1/guru/profile/detail", getReq.path)

        val updated = repo.updateProfile(fullName = "  Ibu Siti Baru  ", phoneNumber = "  ", address = null)
        assertTrue(updated is ApiResult.Success)
        val putReq = server.takeRequest()
        assertEquals("PUT", putReq.method)
        assertEquals("/api/v1/guru/profile", putReq.path)
        val sentJson = putReq.body.readUtf8()
        assertTrue(sentJson.contains("\"fullName\":\"Ibu Siti Baru\""))
        assertTrue(sentJson.contains("\"phoneNumber\":\"\""))
        assertFalse(sentJson.contains("address"))
    }
}
