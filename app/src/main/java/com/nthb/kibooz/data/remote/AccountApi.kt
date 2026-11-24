package com.nthb.kibooz.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.HTTP

interface AccountApi {
    // DELETE membawa body JSON (kata sandi), sedangkan @DELETE Retrofit tidak boleh punya body
    @HTTP(method = "DELETE", path = "users/me", hasBody = true)
    suspend fun deleteAccount(@Body body: DeleteAccountRequest): Response<ApiEnvelope<EmptyData>>
}
