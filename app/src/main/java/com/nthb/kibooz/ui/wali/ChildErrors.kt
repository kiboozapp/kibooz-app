package com.nthb.kibooz.ui.wali

import androidx.annotation.StringRes
import com.nthb.kibooz.R
import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.errorMessageRes

/**
 * Pesan untuk galat rute anak dan profil Wali. Kode yang artinya bergantung pada layar ini (anak yang sudah tidak
 * ada, NISN yang sudah dijamin guru, kelas tutup) diberi kalimat yang menyebut tindakan berikutnya. Kode lain memakai
 * pesan bersama di [errorMessageRes].
 */
@StringRes
fun ApiResult<*>.childErrorRes(): Int {
    if (this is ApiResult.Failure) {
        when (errorCode) {
            "STUDENT_NOT_FOUND" -> return R.string.anak_error_tidak_ditemukan
            "NISN_ALREADY_REGISTERED" -> return R.string.anak_error_nisn_terverifikasi
            "CLASS_CODE_NOT_FOUND" -> return R.string.anak_error_kode_tidak_ditemukan
            "CLASS_JOINING_CLOSED" -> return R.string.anak_error_kelas_tutup
            "CLASS_FULL" -> return R.string.anak_error_kelas_penuh
            "CLASS_NO_ACTIVE_TEACHER" -> return R.string.anak_error_kelas_tanpa_guru
            "CHILD_LIMIT_REACHED" -> return R.string.anak_error_batas
            "PROFILE_NOT_FOUND" -> return R.string.anak_error_profil_tidak_ada
        }
    }
    return errorMessageRes() ?: R.string.error_unknown
}

/** Batas jumlah anak untuk satu akun Wali di server. */
const val MAX_CHILDREN = 10
