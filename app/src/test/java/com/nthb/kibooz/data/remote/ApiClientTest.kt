package com.nthb.kibooz.data.remote

import com.squareup.moshi.Types
import kotlinx.coroutines.runBlocking
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.http.GET

class ApiClientTest {

    private data class Ping(val name: String)

    private interface PingService {
        @GET("ping")
        suspend fun ping(): ApiEnvelope<Ping>
    }

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun client(token: String? = "jwt") = ApiClient(
        tokenProvider = { token },
        baseUrl = server.url("/api/v1/").toString(),
        logLevel = HttpLoggingInterceptor.Level.NONE
    )

    private fun adapter(client: ApiClient) = client.moshi.adapter<ApiEnvelope<Ping>>(
        Types.newParameterizedType(ApiEnvelope::class.java, Ping::class.java)
    )

    @Test
    fun `parses a success envelope with data`() {
        val envelope = adapter(client()).fromJson(
            """{"success":true,"message":"OK","data":{"name":"kibooz"}}"""
        )!!

        assertTrue(envelope.success)
        assertEquals("OK", envelope.message)
        assertEquals(Ping("kibooz"), envelope.data)
        assertNull(envelope.errorCode)
    }

    @Test
    fun `parses a success envelope without message or data`() {
        val envelope = adapter(client()).fromJson("""{"success":true}""")!!

        assertTrue(envelope.success)
        assertNull(envelope.message)
        assertNull(envelope.data)
    }

    @Test
    fun `parses a failure envelope with details keyed by field path`() {
        val envelope = adapter(client()).fromJson(
            """{"success":false,"message":"Validation failed","errorCode":"VALIDATION_ERROR",
               "details":{"entries[2].studentId":"is not in this class"}}"""
        )!!

        assertFalse(envelope.success)
        assertEquals("VALIDATION_ERROR", envelope.errorCode)
        assertEquals("is not in this class", envelope.details!!["entries[2].studentId"])
        assertNull(envelope.data)
    }

    @Test
    fun `ignores fields it does not know`() {
        val envelope = adapter(client()).fromJson(
            """{"success":true,"data":{"name":"a","extra":1},"newField":"x"}"""
        )

        assertNotNull(envelope)
    }

    @Test
    fun `a service created by the client sends the token and decodes the envelope`() {
        server.enqueue(MockResponse().setBody("""{"success":true,"data":{"name":"kibooz"}}"""))
        val service = client("jwt").create(PingService::class.java)

        val result = runBlocking { service.ping() }

        assertEquals("kibooz", result.data?.name)
        val request = server.takeRequest()
        assertEquals("/api/v1/ping", request.path)
        assertEquals("Bearer jwt", request.getHeader("Authorization"))
    }
}
