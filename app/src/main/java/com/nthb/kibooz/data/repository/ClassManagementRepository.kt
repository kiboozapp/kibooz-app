package com.nthb.kibooz.data.repository

import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.ClassSummary
import com.nthb.kibooz.data.remote.CreateClassRequest
import com.nthb.kibooz.data.remote.GuruClassApi
import com.nthb.kibooz.data.remote.NisnVerificationData
import com.nthb.kibooz.data.remote.SetJoiningRequest
import com.nthb.kibooz.data.remote.newIdempotencyKey
import com.nthb.kibooz.data.remote.safeApiCall
import com.nthb.kibooz.data.remote.safeApiCallEmpty
import com.squareup.moshi.Moshi

/**
 * Satu aksi buat kelas. [idempotencyKey] dibuat sekali di sini, jadi mengirim objek yang sama lagi (tombol coba
 * lagi) tidak membuat dua kelas. Jika isiannya berubah, buat objek baru supaya kuncinya baru, karena kunci yang
 * sama dengan isi berbeda ditolak `IDEMPOTENCY_KEY_REUSED`.
 */
class PendingClass(
    val request: CreateClassRequest,
    val idempotencyKey: String = newIdempotencyKey()
)

/** Kelola kelas oleh Guru: daftar, buat, kode gabung, pendaftaran, arsip dan pulihkan. */
class ClassManagementRepository(
    private val classes: ClassRepository,
    private val api: GuruClassApi,
    private val moshi: Moshi
) {

    /** Semua kelas Guru, termasuk yang diarsipkan (ada `archivedAt`), yang paling lama dibuat lebih dulu. */
    suspend fun list(): ApiResult<List<ClassSummary>> = classes.teacherClasses()

    suspend fun create(pending: PendingClass): ApiResult<ClassSummary> =
        safeApiCall(moshi) { api.createClass(pending.idempotencyKey, pending.request) }

    /** Kode baru kelas. Kode lama berhenti berlaku saat itu juga. */
    suspend fun rotateJoinCode(classId: String): ApiResult<String> =
        when (val result = safeApiCall(moshi) { api.rotateJoinCode(classId) }) {
            is ApiResult.Success -> ApiResult.Success(result.data.joinCode, result.message)
            is ApiResult.Failure -> result
            is ApiResult.NetworkError -> result
        }

    /** Mengembalikan nilai `joinOpen` yang kini berlaku di server. */
    suspend fun setJoining(classId: String, open: Boolean): ApiResult<Boolean> =
        when (val result = safeApiCall(moshi) { api.setJoining(classId, SetJoiningRequest(open)) }) {
            is ApiResult.Success -> ApiResult.Success(result.data.joinOpen, result.message)
            is ApiResult.Failure -> result
            is ApiResult.NetworkError -> result
        }

    suspend fun archive(classId: String): ApiResult<ClassSummary> = safeApiCall(moshi) { api.archiveClass(classId) }

    /** Kelas yang dipulihkan mendapat kode gabung baru, yang ada di jawabannya. */
    suspend fun restore(classId: String): ApiResult<ClassSummary> = safeApiCall(moshi) { api.restoreClass(classId) }

    /** Memverifikasi NISN siswa sesuai catatan resmi sekolah. */
    suspend fun verifyNisn(classId: String, studentId: String): ApiResult<NisnVerificationData> =
        safeApiCall(moshi) { api.verifyStudentNisn(classId, studentId) }

    /** Mencabut status verifikasi NISN siswa (misalnya saat salah klik). */
    suspend fun unverifyNisn(classId: String, studentId: String): ApiResult<NisnVerificationData> =
        safeApiCall(moshi) { api.unverifyStudentNisn(classId, studentId) }

    /** Mengeluarkan murid secara permanen dari kelas (soft-delete + bebas NISN). */
    suspend fun removeStudent(classId: String, studentId: String): ApiResult<Unit> =
        safeApiCallEmpty(moshi) { api.removeStudent(classId, studentId) }
}
