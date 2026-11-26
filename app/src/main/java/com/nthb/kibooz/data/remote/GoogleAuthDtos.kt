package com.nthb.kibooz.data.remote

import com.nthb.kibooz.data.session.Role
import com.squareup.moshi.Json

data class GoogleLoginRequest(
    val idToken: String,
    val role: Role,
    val deviceToken: String? = null
)

data class GoogleProfileData(
    val email: String,
    val fullName: String,
    val avatarUrl: String? = null
)

data class GoogleAuthData(
    val needsRegistration: Boolean = false,
    val googleProfile: GoogleProfileData? = null,
    val token: String? = null,
    val refreshToken: String? = null,
    val deviceToken: String? = null,
    val user: UserDto? = null
)

data class GoogleRegisterRequest(
    val idToken: String,
    val fullName: String,
    val role: Role,
    val phoneNumber: String? = null,
    val nip: String? = null,
    val schoolName: String? = null,
    val address: String? = null,
    val whatsappNumber: String? = null,
    @Json(name = "class") val classInfo: NewClass? = null,
    val classCode: String? = null,
    val student: NewStudent? = null,
    val deviceToken: String? = null
)
