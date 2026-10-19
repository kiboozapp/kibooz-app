package com.nthb.kibooz.data.remote

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class AuthTokenInterceptorTest {

    private lateinit var server: MockWebServer
    private var token: String? = null

    private val client by lazy {
        OkHttpClient.Builder().addInterceptor(AuthTokenInterceptor { token }).build()
    }

    @Before
    fun setUp() {
        server = MockWebServer().apply {
            enqueue(MockResponse().setBody("{}"))
            start()
        }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun send(request: Request.Builder = Request.Builder()): String? {
        client.newCall(request.url(server.url("/api/v1/ping")).build()).execute().close()
        return server.takeRequest().getHeader("Authorization")
    }

    @Test
    fun `adds the bearer header when a token exists`() {
        token = "abc.def.ghi"

        assertEquals("Bearer abc.def.ghi", send())
    }

    @Test
    fun `sends no header before login`() {
        token = null

        assertNull(send())
    }

    @Test
    fun `treats a blank token as no token`() {
        token = "  "

        assertNull(send())
    }

    @Test
    fun `keeps a header the caller already set`() {
        token = "stored"

        assertEquals("Bearer other", send(Request.Builder().header("Authorization", "Bearer other")))
    }

    @Test
    fun `reads the token again on every request`() {
        token = "first"
        assertEquals("Bearer first", send())

        server.enqueue(MockResponse().setBody("{}"))
        token = "second"
        assertEquals("Bearer second", send())
    }
}
