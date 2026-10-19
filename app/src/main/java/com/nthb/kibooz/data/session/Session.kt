package com.nthb.kibooz.data.session

/** Peran akun. ADMIN bisa login tetapi belum punya rute di API. */
enum class Role { GURU, WALI, ADMIN }

data class SessionUser(
    val id: String,
    val email: String,
    val fullName: String,
    val role: Role,
    val avatarUrl: String?
)

/** Sesi yang sedang aktif. [refreshToken] sekali pakai, jadi selalu simpan pasangan terbaru. */
data class Session(
    val accessToken: String,
    val refreshToken: String,
    val user: SessionUser
)
