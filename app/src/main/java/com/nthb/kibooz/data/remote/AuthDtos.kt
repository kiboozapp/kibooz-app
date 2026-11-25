package com.nthb.kibooz.data.remote

import com.nthb.kibooz.data.session.Role
import com.squareup.moshi.Json

data class LoginRequest(
    val email: String,
    val password: String,
    val role: Role,
    val deviceToken: String? = null
)

data class RefreshRequest(val refreshToken: String)

data class LogoutRequest(val refreshToken: String)

data class ChangePasswordRequest(val currentPassword: String, val newPassword: String)

data class UserDto(
    val id: String,
    val email: String,
    val fullName: String,
    val role: Role,
    val avatarUrl: String?
)

/** Kata sandi diperiksa lebih dulu di server, jadi kode yang salah tidak memakai jatah percobaan orang yang hanya tahu emailnya. */
data class VerifyEmailRequest(
    val email: String,
    val password: String,
    val code: String,
    val deviceToken: String? = null
)

data class ResendVerificationRequest(val email: String, val password: String, val deviceToken: String? = null)

/** Keadaan kode verifikasi email. Ada di balasan register (jika server mewajibkannya) dan di balasan kirim ulang. */
data class VerificationDto(
    val sent: Boolean = true,
    val expiresInSeconds: Int = 900,
    val resendAfterSeconds: Int = 60
)

/**
 * `deviceToken` hanya ada di balasan register dan login, tidak di refresh. Saat `verificationRequired` benar,
 * `token` dan `refreshToken` kosong dan akunnya belum punya sesi. Isian baru punya nilai bawaan supaya balasan lama tetap terbaca.
 */
data class AuthData(
    val token: String = "",
    val refreshToken: String = "",
    val deviceToken: String? = null,
    val verificationRequired: Boolean = false,
    val verification: VerificationDto? = null,
    val user: UserDto
)

/**
 * Pendaftaran. GURU wajib mengirim [classInfo] (kelas baru yang ia ajar), WALI wajib mengirim [classCode] dan
 * [student]. Isian yang null tidak ikut terkirim.
 */
data class RegisterRequest(
    val email: String,
    val password: String,
    val fullName: String,
    val role: Role,
    val phoneNumber: String? = null,
    val nip: String? = null,
    val schoolName: String? = null,
    @Json(name = "class") val classInfo: NewClass? = null,
    val classCode: String? = null,
    val student: NewStudent? = null
)

data class NewClass(
    val name: String,
    val gradeLevel: String? = null,
    val academicYear: String? = null
)

data class NewStudent(val nisn: String, val fullName: String)
