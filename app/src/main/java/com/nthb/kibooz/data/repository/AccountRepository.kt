package com.nthb.kibooz.data.repository

import com.nthb.kibooz.data.remote.AccountApi
import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.DeleteAccountRequest
import com.nthb.kibooz.data.remote.safeApiCallEmpty
import com.squareup.moshi.Moshi

/** Pengelolaan akun milik pengguna yang login. Saat ini hanya penghapusan akun. */
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
}
