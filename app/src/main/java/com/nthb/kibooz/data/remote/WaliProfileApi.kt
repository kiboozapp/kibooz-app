package com.nthb.kibooz.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

/** Anak dan profil Wali. Daftar anak untuk dasbor dan kamera tetap lewat [ClassApi.listChildren]. */
interface WaliProfileApi {
    @GET("wali/children")
    suspend fun listChildren(): Response<ApiEnvelope<ChildList>>

    @GET("wali/child/{studentId}")
    suspend fun child(@Path("studentId") studentId: String): Response<ApiEnvelope<ChildProfile>>

    /** Membalas 201 dengan anak yang baru. Mengirim ulang kunci yang sama memutar balik jawaban pertama. */
    @POST("wali/children")
    suspend fun addChild(
        @Header(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String,
        @Body body: AddChildRequest
    ): Response<ApiEnvelope<ChildSummary>>

    @PATCH("wali/children/{studentId}")
    suspend fun updateChild(
        @Path("studentId") studentId: String,
        @Body body: UpdateChildRequest
    ): Response<ApiEnvelope<ChildSummary>>

    /** Balasannya tanpa `data`. */
    @DELETE("wali/children/{studentId}")
    suspend fun deleteChild(@Path("studentId") studentId: String): Response<ApiEnvelope<EmptyData>>

    @PATCH("wali/profile")
    suspend fun updateProfile(@Body body: UpdateWaliProfileRequest): Response<ApiEnvelope<Guardian>>
}
