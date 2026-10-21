package com.nthb.kibooz.data.remote

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface ClassApi {
    @GET("classes/{classId}/students")
    suspend fun listStudents(
        @Path("classId") classId: String,
        @Query("page") page: Int,
        @Query("limit") limit: Int
    ): Response<ApiEnvelope<ClassStudentList>>

    @GET("wali/children")
    suspend fun listChildren(): Response<ApiEnvelope<ChildList>>
}
