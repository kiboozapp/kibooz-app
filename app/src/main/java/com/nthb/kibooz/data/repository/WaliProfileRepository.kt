package com.nthb.kibooz.data.repository

import com.nthb.kibooz.data.remote.AddChildRequest
import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.ChildProfile
import com.nthb.kibooz.data.remote.ChildSummary
import com.nthb.kibooz.data.remote.Guardian
import com.nthb.kibooz.data.remote.NewStudent
import com.nthb.kibooz.data.remote.UpdateChildRequest
import com.nthb.kibooz.data.remote.UpdateWaliProfileRequest
import com.nthb.kibooz.data.remote.WaliProfileApi
import com.nthb.kibooz.data.remote.newIdempotencyKey
import com.nthb.kibooz.data.remote.safeApiCall
import com.nthb.kibooz.data.remote.safeApiCallEmpty
import com.squareup.moshi.Moshi

/**
 * Satu aksi "tambah anak". Kirim objek yang sama lagi saat mencoba ulang supaya [idempotencyKey] tetap sama dan
 * anak tidak terdaftar dua kali. Jika isiannya berubah, buat objek baru supaya kuncinya baru, karena kunci yang sama
 * dengan isi berbeda ditolak `IDEMPOTENCY_KEY_REUSED`.
 */
class PendingAddChild(
    val classCode: String,
    val nisn: String,
    val fullName: String,
    val idempotencyKey: String = newIdempotencyKey()
)

/** Anak dan profil Wali. Batas 10 anak, kode kelas dan aturan NISN dijaga server, aplikasi hanya memeriksa dini. */
class WaliProfileRepository(private val api: WaliProfileApi, private val moshi: Moshi) {

    /** Anak milik Wali yang login, yang paling lama terdaftar lebih dulu. */
    suspend fun children(): ApiResult<List<ChildSummary>> =
        when (val result = safeApiCall(moshi) { api.listChildren() }) {
            is ApiResult.Success -> ApiResult.Success(result.data.children, result.message)
            is ApiResult.Failure -> result
            is ApiResult.NetworkError -> result
        }

    /** Data satu anak beserta kelas dan data Wali. */
    suspend fun child(studentId: String): ApiResult<ChildProfile> = safeApiCall(moshi) { api.child(studentId) }

    suspend fun addChild(add: PendingAddChild): ApiResult<ChildSummary> {
        val request = AddChildRequest(add.classCode.trim().uppercase(), NewStudent(add.nisn.trim(), add.fullName.trim()))

        return safeApiCall(moshi) { api.addChild(add.idempotencyKey, request) }
    }

    /** Hanya isian yang berubah yang dikirim. Keduanya null berarti tidak ada yang perlu dikirim, jadi pemanggil harus mencegahnya. */
    suspend fun updateChild(studentId: String, fullName: String?, nisn: String?): ApiResult<ChildSummary> {
        val request = UpdateChildRequest(fullName?.trim(), nisn?.trim())

        return safeApiCall(moshi) { api.updateChild(studentId, request) }
    }

    suspend fun deleteChild(studentId: String): ApiResult<Unit> = safeApiCallEmpty(moshi) { api.deleteChild(studentId) }

    /**
     * Hanya isian yang berubah yang dikirim. Teks kosong untuk telepon, WhatsApp atau alamat menghapusnya di server,
     * dan null membiarkannya.
     */
    suspend fun updateProfile(
        fullName: String? = null,
        phoneNumber: String? = null,
        whatsappNumber: String? = null,
        address: String? = null
    ): ApiResult<Guardian> {
        val request = UpdateWaliProfileRequest(fullName?.trim(), phoneNumber?.trim(), whatsappNumber?.trim(), address?.trim())

        return safeApiCall(moshi) { api.updateProfile(request) }
    }
}
