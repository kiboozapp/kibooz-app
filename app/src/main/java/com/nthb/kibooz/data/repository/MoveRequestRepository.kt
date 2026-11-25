package com.nthb.kibooz.data.repository

import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.ClassMoveRequest
import com.nthb.kibooz.data.remote.MoveRequest
import com.nthb.kibooz.data.remote.MoveRequestApi
import com.nthb.kibooz.data.remote.safeApiCall
import com.nthb.kibooz.data.remote.safeApiCallEmpty
import com.squareup.moshi.Moshi

/** Pengelolaan permohonan pindah kelas untuk Guru (daftar, terima, tolak) dan Wali (riwayat dan batal). */
class MoveRequestRepository(private val api: MoveRequestApi, private val moshi: Moshi) {

    suspend fun classMoveRequests(classId: String): ApiResult<List<ClassMoveRequest>> =
        when (val result = safeApiCall(moshi) { api.listClassMoveRequests(classId) }) {
            is ApiResult.Success -> ApiResult.Success(result.data.requests, result.message)
            is ApiResult.Failure -> result
            is ApiResult.NetworkError -> result
        }

    suspend fun acceptClassMove(classId: String, requestId: String): ApiResult<Unit> =
        safeApiCallEmpty(moshi) { api.acceptClassMove(classId, requestId) }

    suspend fun rejectClassMove(classId: String, requestId: String): ApiResult<Unit> =
        safeApiCallEmpty(moshi) { api.rejectClassMove(classId, requestId) }

    suspend fun childMoveRequests(studentId: String): ApiResult<List<MoveRequest>> =
        when (val result = safeApiCall(moshi) { api.listChildMoveRequests(studentId) }) {
            is ApiResult.Success -> ApiResult.Success(result.data.requests, result.message)
            is ApiResult.Failure -> result
            is ApiResult.NetworkError -> result
        }

    suspend fun cancelChildMoveRequest(studentId: String, requestId: String): ApiResult<Unit> =
        safeApiCallEmpty(moshi) { api.cancelChildMoveRequest(studentId, requestId) }
}
