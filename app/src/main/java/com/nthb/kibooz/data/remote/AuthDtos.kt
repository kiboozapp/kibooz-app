package com.nthb.kibooz.data.remote

import com.nthb.kibooz.data.session.Role

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
