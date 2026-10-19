package com.nthb.kibooz.data

import com.nthb.kibooz.data.session.Role
import com.nthb.kibooz.data.session.Session
import com.nthb.kibooz.data.session.SessionStore
import com.nthb.kibooz.data.session.SessionUser

val sampleUser = SessionUser("u1", "ani@example.com", "Ani", Role.WALI, null)
val sampleSession = Session("old-access", "old-refresh", sampleUser)

fun authJson(access: String, refresh: String, deviceToken: String? = null) = """
    {"success":true,"data":{"token":"$access","refreshToken":"$refresh",
    ${deviceToken?.let { "\"deviceToken\":\"$it\"," } ?: ""}
    "user":{"id":"u1","email":"ani@example.com","fullName":"Ani","role":"WALI","avatarUrl":null}}}
""".trimIndent()

fun failureJson(errorCode: String) = """{"success":false,"message":"nope","errorCode":"$errorCode"}"""

/** SessionStore di memori. [failWrites] meniru penyimpanan yang gagal menulis. */
class InMemorySessionStore(var session: Session? = null) : SessionStore {
    val deviceTokens = mutableMapOf<String, String>()
    var failWrites = false

    override suspend fun readSession() = session

    override suspend fun writeSession(session: Session) {
        if (failWrites) throw java.io.IOException("disk full")
        this.session = session
    }

    override suspend fun clearSession() {
        session = null
    }

    override suspend fun readDeviceToken(email: String) = deviceTokens[email.trim().lowercase()]

    override suspend fun writeDeviceToken(email: String, deviceToken: String) {
        deviceTokens[email.trim().lowercase()] = deviceToken
    }
}
