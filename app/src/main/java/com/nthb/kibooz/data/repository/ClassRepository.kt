package com.nthb.kibooz.data.repository

import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.ChildSummary
import com.nthb.kibooz.data.remote.ClassApi
import com.nthb.kibooz.data.remote.ClassStudent
import com.nthb.kibooz.data.remote.ClassStudentList
import com.nthb.kibooz.data.remote.ClassSummary
import com.nthb.kibooz.data.remote.safeApiCall
import com.squareup.moshi.Moshi

/** Daftar kelas dan siswa untuk Guru dan daftar anak untuk Wali, dipakai oleh pemilih di layar kamera. */
class ClassRepository(private val api: ClassApi, private val moshi: Moshi) {

    suspend fun students(classId: String, page: Int = 1, limit: Int = PAGE_LIMIT): ApiResult<ClassStudentList> =
        safeApiCall(moshi) { api.listStudents(classId, page, limit) }

    /** Semua siswa kelas, halaman demi halaman. Berhenti di halaman pertama yang gagal dan mengembalikan kegagalannya. */
    suspend fun allStudents(classId: String, pageSize: Int = PAGE_LIMIT): ApiResult<List<ClassStudent>> =
        allPages { page ->
            when (val result = students(classId, page, pageSize)) {
                is ApiResult.Success -> ApiResult.Success(Page(result.data.students, result.data.total))
                is ApiResult.Failure -> result
                is ApiResult.NetworkError -> result
            }
        }

    /** Semua kelas yang diajar Guru yang login, yang paling lama dibuat lebih dulu. */
    suspend fun teacherClasses(pageSize: Int = PAGE_LIMIT): ApiResult<List<ClassSummary>> =
        allPages { page ->
            when (val result = safeApiCall(moshi) { api.listClasses(page, pageSize) }) {
                is ApiResult.Success -> ApiResult.Success(Page(result.data.classes, result.data.total))
                is ApiResult.Failure -> result
                is ApiResult.NetworkError -> result
            }
        }

    /** Anak milik Wali yang login, yang paling lama terdaftar lebih dulu. */
    suspend fun children(): ApiResult<List<ChildSummary>> =
        when (val result = safeApiCall(moshi) { api.listChildren() }) {
            is ApiResult.Success -> ApiResult.Success(result.data.children, result.message)
            is ApiResult.Failure -> result
            is ApiResult.NetworkError -> result
        }

    private class Page<T>(val items: List<T>, val total: Int)

    /** Membaca halaman 1, 2, ... sampai [total] terpenuhi atau halaman kosong. Kegagalan satu halaman membatalkan semuanya. */
    private suspend fun <T> allPages(fetch: suspend (page: Int) -> ApiResult<Page<T>>): ApiResult<List<T>> {
        val all = mutableListOf<T>()

        for (page in 1..MAX_PAGES) {
            when (val result = fetch(page)) {
                is ApiResult.Success -> {
                    all += result.data.items
                    if (result.data.items.isEmpty() || all.size >= result.data.total) break
                }
                is ApiResult.Failure -> return result
                is ApiResult.NetworkError -> return result
            }
        }

        return ApiResult.Success(all)
    }

    private companion object {
        const val PAGE_LIMIT = 50

        // Pengaman: kelas berisi 20 sampai 25 anak dan seorang Guru paling banyak 20 kelas aktif
        const val MAX_PAGES = 20
    }
}
