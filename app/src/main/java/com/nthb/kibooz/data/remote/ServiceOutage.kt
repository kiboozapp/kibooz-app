package com.nthb.kibooz.data.remote

/** Jenis gangguan yang membuat server tidak terjangkau. */
enum class OutageKind {
    /** Tidak ada balasan sama sekali: tidak ada internet atau koneksi ke server gagal. */
    OFFLINE,

    /** Balasan dari jaringan atau proxy (502, 503 dan 504 tanpa body JSON) atau server yang terlalu lambat. */
    UNAVAILABLE
}

/** Gangguan beserta berapa detik aplikasi menunggu sebelum mencoba lagi. */
data class ServiceOutage(val kind: OutageKind, val waitSeconds: Long)

private const val BASE_WAIT_SECONDS = 30L

/** Jeda yang terus bertambah berhenti di sini supaya layar yang dibiarkan terbuka tetap mencoba tiap beberapa menit. */
private const val MAX_WAIT_SECONDS = 300L

/**
 * Jenis gangguan dari satu hasil API, atau null jika bukan gangguan (galat biasa dari API, atau berhasil).
 * Mengikuti bagian "When the API is down" di `docs/client-guide.md` backend: gagal terhubung, atau 502, 503 dan
 * 504 tanpa body JSON, berasal dari jaringan atau proxy dan bukan dari API. 503 `SERVER_TIMEOUT` juga dihitung.
 */
fun ApiResult<*>.outageKind(): OutageKind? = when (this) {
    is ApiResult.NetworkError -> OutageKind.OFFLINE
    is ApiResult.Failure -> when {
        status in 502..504 && errorCode == null -> OutageKind.UNAVAILABLE
        status == 503 && errorCode == "SERVER_TIMEOUT" -> OutageKind.UNAVAILABLE
        else -> null
    }
    is ApiResult.Success -> null
}

/**
 * Gangguan beserta lama tunggunya, atau null jika hasil ini bukan gangguan. Header `Retry-After` dipatuhi dan
 * ditambah [jitterSeconds] supaya semua aplikasi tidak mencoba di detik yang sama. Tanpa header, menunggu 30 detik
 * dan menggandakannya tiap percobaan sampai 5 menit. [attempt] dimulai dari 1 untuk gangguan pertama.
 */
fun ApiResult<*>.outage(attempt: Int, jitterSeconds: Long = 0): ServiceOutage? {
    val kind = outageKind() ?: return null
    val retryAfter = (this as? ApiResult.Failure)?.retryAfterSeconds

    val wait = if (retryAfter != null) {
        retryAfter + jitterSeconds
    } else {
        (BASE_WAIT_SECONDS shl (attempt - 1).coerceIn(0, 4)).coerceAtMost(MAX_WAIT_SECONDS)
    }

    return ServiceOutage(kind, wait.coerceAtLeast(1))
}
