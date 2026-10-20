package com.nthb.kibooz.data.remote

/**
 * Sumber access token untuk [AuthTokenInterceptor]. Dipanggil di thread jaringan OkHttp, jadi
 * implementasi harus membaca dari memori dan paling banyak menunggu I/O satu kali saat pertama dimuat.
 */
fun interface TokenProvider {
    /** Access token saat ini, atau null jika belum login. */
    fun accessToken(): String?
}
