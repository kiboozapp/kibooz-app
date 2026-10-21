package com.nthb.kibooz.data.repository

import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.ChildSummary
import com.nthb.kibooz.data.remote.ClassApi
import com.nthb.kibooz.data.remote.ClassStudent
import com.nthb.kibooz.data.remote.ClassStudentList
import com.nthb.kibooz.data.remote.safeApiCall
import com.squareup.moshi.Moshi

/** Daftar siswa kelas untuk Guru dan daftar anak untuk Wali, dipakai oleh pemilih di layar kamera. */
class ClassRepository(private val api: ClassApi, private val moshi: Moshi) {

    suspend fun students(classId: String, page: Int = 1, limit: Int = PAGE_LIMIT): ApiResult<ClassStudentList> =
        safeApiCall(moshi) { api.listStudents(classId, page, limit) }

    /** Semua siswa kelas, halaman demi halaman. Berhenti di halaman pertama yang gagal dan mengembalikan kegagalannya. */
    suspend fun allStudents(classId: String, pageSize: Int = PAGE_LIMIT): ApiResult<List<ClassStudent>> {
        val all = mutableListOf<ClassStudent>()

        for (page in 1..MAX_PAGES) {
            when (val result = students(classId, page, pageSize)) {
                is ApiResult.Success -> {
                    all += result.data.students
                    if (result.data.students.isEmpty() || all.size >= result.data.total) break
                }
                is ApiResult.Failure -> return result
                is ApiResult.NetworkError -> return result
            }
        }

        return ApiResult.Success(all)
    }

    /** Anak milik Wali yang login, yang paling lama terdaftar lebih dulu. */
    suspend fun children(): ApiResult<List<ChildSummary>> =
        when (val result = safeApiCall(moshi) { api.listChildren() }) {
            is ApiResult.Success -> ApiResult.Success(result.data.children, result.message)
            is ApiResult.Failure -> result
            is ApiResult.NetworkError -> result
        }

    private companion object {
        const val PAGE_LIMIT = 50

        // Pengaman: kelas berisi 20 sampai 25 anak, jadi satu halaman sudah cukup
        const val MAX_PAGES = 20
    }
}
