package com.nthb.kibooz.data.remote

import com.nthb.kibooz.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.concurrent.TimeUnit
import okhttp3.Authenticator
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/**
 * Klien jaringan tunggal untuk API Kibooz. Bangun satu instance untuk seluruh proses, lalu buat
 * service Retrofit lewat [create].
 *
 * Log hanya mencetak baris request dan status, dengan header Authorization disamarkan. Body tidak
 * dicetak karena memuat kata sandi dan token. Naikkan [logLevel] secara lokal untuk memeriksa payload.
 *
 * [authenticator] menangani 401 (lihat [TokenAuthenticator]). Tanpa itu 401 langsung diteruskan.
 */
class ApiClient(
    tokenProvider: TokenProvider,
    authenticator: Authenticator? = null,
    baseUrl: String = BuildConfig.API_BASE_URL,
    logLevel: HttpLoggingInterceptor.Level =
        if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
) {
    val moshi: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .addInterceptor(AuthTokenInterceptor(tokenProvider, baseUrl.toHttpUrl()))
        .apply { authenticator?.let { authenticator(it) } }
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = logLevel
            redactHeader("Authorization")
        })
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    fun <T> create(service: Class<T>): T = retrofit.create(service)

    private companion object {
        // Server memutus request di 10 detik, upload video dikirim langsung ke storage dan tidak lewat sini
        const val TIMEOUT_SECONDS = 15L
    }
}
