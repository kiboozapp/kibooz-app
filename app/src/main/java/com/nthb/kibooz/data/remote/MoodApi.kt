package com.nthb.kibooz.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface MoodApi {
    @POST("guru/mood/log/batch")
    suspend fun logMoodBatch(
        @Header(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String,
        @Body body: LogMoodBatchRequest
    ): Response<ApiEnvelope<LogMoodBatchData>>
}
