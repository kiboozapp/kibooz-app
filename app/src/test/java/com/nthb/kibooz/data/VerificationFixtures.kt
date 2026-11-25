package com.nthb.kibooz.data

/** Balasan register dari server yang mewajibkan verifikasi email: token kosong, belum ada sesi. */
fun registerPendingJson(sent: Boolean = true, resendAfter: Int = 60, deviceToken: String? = null) = """
    {"success":true,"message":"Registration successful","data":{"token":"","refreshToken":"","verificationRequired":true,
    ${deviceToken?.let { "\"deviceToken\":\"$it\"," } ?: ""}
    "verification":{"sent":$sent,"expiresInSeconds":900,"resendAfterSeconds":$resendAfter},
    "user":{"id":"u1","email":"ani@example.com","fullName":"Ani","role":"WALI","avatarUrl":null}}}
""".trimIndent()

/** Balasan kirim ulang kode. */
fun resendJson(sent: Boolean = true, resendAfter: Int = 60) = """
    {"success":true,"data":{"sent":$sent,"expiresInSeconds":900,"resendAfterSeconds":$resendAfter}}
""".trimIndent()
