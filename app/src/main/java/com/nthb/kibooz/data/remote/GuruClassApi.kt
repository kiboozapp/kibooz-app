package com.nthb.kibooz.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/** Rute `/guru/classes` yang mengubah kelas. Daftar kelasnya ada di [ClassApi.listClasses]. */
interface GuruClassApi {
    @POST("guru/classes")
    suspend fun createClass(
        @Header(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String,
        @Body body: CreateClassRequest
    ): Response<ApiEnvelope<ClassSummary>>

    @POST("guru/classes/{classId}/join-code")
    suspend fun rotateJoinCode(@Path("classId") classId: String): Response<ApiEnvelope<JoinCodeData>>

    @PUT("guru/classes/{classId}/joining")
    suspend fun setJoining(
        @Path("classId") classId: String,
        @Body body: SetJoiningRequest
    ): Response<ApiEnvelope<JoiningData>>

    @POST("guru/classes/{classId}/archive")
    suspend fun archiveClass(@Path("classId") classId: String): Response<ApiEnvelope<ClassSummary>>

    @POST("guru/classes/{classId}/restore")
    suspend fun restoreClass(@Path("classId") classId: String): Response<ApiEnvelope<ClassSummary>>

    @POST("guru/classes/{classId}/students/{studentId}/nisn-verification")
    suspend fun verifyStudentNisn(
        @Path("classId") classId: String,
        @Path("studentId") studentId: String
    ): Response<ApiEnvelope<NisnVerificationData>>

    @DELETE("guru/classes/{classId}/students/{studentId}/nisn-verification")
    suspend fun unverifyStudentNisn(
        @Path("classId") classId: String,
        @Path("studentId") studentId: String
    ): Response<ApiEnvelope<NisnVerificationData>>
}
