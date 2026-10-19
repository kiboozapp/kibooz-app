package com.nthb.kibooz.data.remote

/**
 * Sumber access token untuk [AuthTokenInterceptor]. Dipanggil di thread jaringan OkHttp, jadi
 * implementasi tidak boleh menunggu I/O (simpan token di memori, lalu tulis ke DataStore di FE-03).
 */
fun interface TokenProvider {
    /** Access token saat ini, atau null jika belum login. */
    fun accessToken(): String?
}
