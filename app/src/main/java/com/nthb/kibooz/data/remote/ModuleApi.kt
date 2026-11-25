package com.nthb.kibooz.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ModuleApi {
    @GET("classes/{classId}/modules")
    suspend fun listModules(
        @Path("classId") classId: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 50
    ): Response<ApiEnvelope<ModuleList>>

    @POST("classes/{classId}/modules")
    suspend fun createModule(
        @Path("classId") classId: String,
        @Header(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String,
        @Body body: CreateModuleRequest
    ): Response<ApiEnvelope<ModuleItem>>

    @PATCH("classes/{classId}/modules/{moduleId}")
    suspend fun updateModule(
        @Path("classId") classId: String,
        @Path("moduleId") moduleId: String,
        @Body body: UpdateModuleRequest
    ): Response<ApiEnvelope<ModuleItem>>

    @DELETE("classes/{classId}/modules/{moduleId}")
    suspend fun deleteModule(
        @Path("classId") classId: String,
        @Path("moduleId") moduleId: String
    ): Response<ApiEnvelope<EmptyData>>
}
