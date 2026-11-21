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

data class UserDto(
    val id: String,
    val email: String,
    val fullName: String,
    val role: Role,
    val avatarUrl: String?
)

/** `deviceToken` hanya ada di balasan register dan login, tidak di refresh. */
data class AuthData(
    val token: String,
    val refreshToken: String,
    val deviceToken: String? = null,
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
