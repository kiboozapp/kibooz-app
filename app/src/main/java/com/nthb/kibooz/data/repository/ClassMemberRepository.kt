package com.nthb.kibooz.data.repository

import com.nthb.kibooz.data.remote.AddTeacherRequest
import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.ClassMemberApi
import com.nthb.kibooz.data.remote.ClassTeacher
import com.nthb.kibooz.data.remote.TransferOwnershipRequest
import com.nthb.kibooz.data.remote.newIdempotencyKey
import com.nthb.kibooz.data.remote.safeApiCall
import com.nthb.kibooz.data.remote.safeApiCallEmpty
import com.squareup.moshi.Moshi

/**
 * Satu aksi tambah guru. [idempotencyKey] dibuat sekali di sini, jadi mengirim objek yang sama lagi tidak
 * menambahkan dua kali. Email yang berbeda memakai objek baru supaya kuncinya baru.
 */
class PendingTeacher(
    val email: String,
    val idempotencyKey: String = newIdempotencyKey()
)

/** Anggota kelas yang dikelola Guru: daftar guru, tambah, keluarkan, pindahkan pemilik, dan keluarkan anak. */
class ClassMemberRepository(private val api: ClassMemberApi, private val moshi: Moshi) {

    /** Guru di kelas, yang paling lama bergabung lebih dulu. */
    suspend fun teachers(classId: String): ApiResult<List<ClassTeacher>> =
        when (val result = safeApiCall(moshi) { api.listTeachers(classId) }) {
            is ApiResult.Success -> ApiResult.Success(result.data.teachers, result.message)
            is ApiResult.Failure -> result
            is ApiResult.NetworkError -> result
        }

    suspend fun addTeacher(classId: String, pending: PendingTeacher): ApiResult<ClassTeacher> =
        safeApiCall(moshi) { api.addTeacher(classId, pending.idempotencyKey, AddTeacherRequest(pending.email)) }

    /** Untuk diri sendiri berarti keluar dari kelas. Server mengganti kode kelas setiap kali berhasil. */
    suspend fun removeTeacher(classId: String, teacherId: String): ApiResult<Unit> =
        safeApiCallEmpty(moshi) { api.removeTeacher(classId, teacherId) }

    /** Mengembalikan pemilik yang baru. */
    suspend fun transferOwnership(classId: String, teacherId: String): ApiResult<ClassTeacher> =
        safeApiCall(moshi) { api.transferOwnership(classId, TransferOwnershipRequest(teacherId)) }

    /** Anak yang dikeluarkan tidak bisa dipulihkan. */
    suspend fun removeStudent(classId: String, studentId: String): ApiResult<Unit> =
        safeApiCallEmpty(moshi) { api.removeStudent(classId, studentId) }
}
