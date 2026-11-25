package com.nthb.kibooz.data.remote

/**
 * Hapus akun. [successorEmail] hanya dibaca server untuk Guru, dan wajib jika Guru ini satu-satunya pengajar
 * kelas yang belum diarsipkan dan masih punya anak aktif. Isian yang null tidak ikut terkirim.
 */
data class DeleteAccountRequest(
    val password: String,
    val successorEmail: String? = null
)

/** `data` dari `POST /users/avatar`. */
data class AvatarData(val avatarUrl: String)
