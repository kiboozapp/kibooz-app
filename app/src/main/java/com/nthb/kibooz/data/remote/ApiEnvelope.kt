package com.nthb.kibooz.data.remote

/**
 * Amplop JSON semua endpoint kecuali `/healthz`. Sukses: `success=true` dengan `message` dan `data`
 * yang bisa hilang. Gagal: `success=false` dengan `message` (teks Inggris, jangan dijadikan percabangan),
 * `errorCode` stabil, dan `details` berisi satu pesan per path field untuk error validasi.
 */
data class ApiEnvelope<T>(
    val success: Boolean,
    val message: String? = null,
    val data: T? = null,
    val errorCode: String? = null,
    val details: Map<String, String>? = null
)
