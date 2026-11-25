package com.nthb.kibooz.data.repository

import com.nthb.kibooz.data.remote.AccountApi
import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.DeleteAccountRequest
import com.nthb.kibooz.data.remote.safeApiCall
import com.nthb.kibooz.data.remote.safeApiCallEmpty
import com.squareup.moshi.Moshi
import java.io.File
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/** Pengelolaan akun dan unggah foto profil milik pengguna yang login. */
class AccountRepository(private val api: AccountApi, private val moshi: Moshi) {

    /**
     * Menghapus akun setelah kata sandi dikonfirmasi. Server mencabut semua sesi, jadi pemanggil menghapus
     * sesi lokal setelah sukses. [successorEmail] kosong dikirim sebagai tidak ada. Kata sandi dikirim apa
     * adanya karena spasi bisa menjadi bagian dari kata sandi.
     */
    suspend fun deleteAccount(password: String, successorEmail: String? = null): ApiResult<Unit> =
        safeApiCallEmpty(moshi) {
            api.deleteAccount(DeleteAccountRequest(password, successorEmail?.trim()?.takeIf { it.isNotEmpty() }))
        }

    /**
     * Mengunggah foto profil JPEG hasil crop ke `POST /users/avatar`.
     * Untuk Wali yang mengganti foto anak, kirim [studentId]. Jika null, mengganti foto akun yang sedang login.
     */
    suspend fun uploadAvatar(jpegFile: File, studentId: String? = null): ApiResult<String> {
        val mediaType = "image/jpeg".toMediaType()
        val part = MultipartBody.Part.createFormData("file", jpegFile.name, jpegFile.asRequestBody(mediaType))
        val studentPart = studentId?.trim()?.takeIf { it.isNotEmpty() }?.toRequestBody("text/plain".toMediaType())
        return when (val result = safeApiCall(moshi) { api.uploadAvatar(part, studentPart) }) {
            is ApiResult.Success -> ApiResult.Success(result.data.avatarUrl, result.message)
            is ApiResult.Failure -> result
            is ApiResult.NetworkError -> result
        }
    }
}
