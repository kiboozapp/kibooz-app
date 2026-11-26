package com.nthb.kibooz.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/** Rute `/guru/classes/{classId}` untuk anggota kelas: guru, pemilik, dan mengeluarkan anak. */
interface ClassMemberApi {
    @GET("guru/classes/{classId}/teachers")
    suspend fun listTeachers(@Path("classId") classId: String): Response<ApiEnvelope<ClassTeacherList>>

    @POST("guru/classes/{classId}/teachers")
    suspend fun addTeacher(
        @Path("classId") classId: String,
        @Header(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String,
        @Body body: AddTeacherRequest
    ): Response<ApiEnvelope<ClassTeacher>>

    /** Mengeluarkan guru lain, atau keluar dari kelas jika [teacherId] adalah diri sendiri. */
    @DELETE("guru/classes/{classId}/teachers/{teacherId}")
    suspend fun removeTeacher(
        @Path("classId") classId: String,
        @Path("teacherId") teacherId: String
    ): Response<ApiEnvelope<EmptyData>>

    @PUT("guru/classes/{classId}/owner")
    suspend fun transferOwnership(
        @Path("classId") classId: String,
        @Body body: TransferOwnershipRequest
    ): Response<ApiEnvelope<ClassTeacher>>

    @DELETE("guru/classes/{classId}/students/{studentId}")
    suspend fun removeStudent(
        @Path("classId") classId: String,
        @Path("studentId") studentId: String
    ): Response<ApiEnvelope<EmptyData>>
}
