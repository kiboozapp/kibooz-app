package com.nthb.kibooz.data.remote

import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/** Endpoint permohonan pindah kelas untuk kedua portal (persetujuan Guru dan riwayat/batal Wali). */
interface MoveRequestApi {
    @GET("guru/classes/{classId}/move-requests")
    suspend fun listClassMoveRequests(
        @Path("classId") classId: String
    ): Response<ApiEnvelope<ClassMoveRequestList>>

    @POST("guru/classes/{classId}/move-requests/{requestId}/accept")
    suspend fun acceptClassMove(
        @Path("classId") classId: String,
        @Path("requestId") requestId: String
    ): Response<ApiEnvelope<EmptyData>>

    @POST("guru/classes/{classId}/move-requests/{requestId}/reject")
    suspend fun rejectClassMove(
        @Path("classId") classId: String,
        @Path("requestId") requestId: String
    ): Response<ApiEnvelope<EmptyData>>

    @GET("wali/children/{studentId}/move-requests")
    suspend fun listChildMoveRequests(
        @Path("studentId") studentId: String
    ): Response<ApiEnvelope<MoveRequestList>>

    @DELETE("wali/children/{studentId}/move-requests/{requestId}")
    suspend fun cancelChildMoveRequest(
        @Path("studentId") studentId: String,
        @Path("requestId") requestId: String
    ): Response<ApiEnvelope<EmptyData>>
}
