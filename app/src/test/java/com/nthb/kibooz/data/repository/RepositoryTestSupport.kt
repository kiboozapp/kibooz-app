package com.nthb.kibooz.data.repository

import com.nthb.kibooz.data.remote.ApiClient
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

fun testClient(server: MockWebServer) = ApiClient(
    tokenProvider = { "jwt" },
    baseUrl = server.url("/api/v1/").toString(),
    logLevel = HttpLoggingInterceptor.Level.NONE
)

fun jsonResponse(body: String, code: Int = 200) = MockResponse().setResponseCode(code).setBody(body)
