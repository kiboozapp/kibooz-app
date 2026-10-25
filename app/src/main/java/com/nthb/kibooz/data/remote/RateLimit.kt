package com.nthb.kibooz.data.remote

import kotlinx.coroutines.delay

/** Berapa kali satu aksi dicoba ulang otomatis setelah 429 sebelum menyerah dan menyerahkannya ke pengguna. */
const val MAX_RATE_LIMIT_RETRIES = 3

private const val DEFAULT_WAIT_SECONDS = 30L

/** Tunggu yang lebih lama dari ini tidak ditunggu otomatis, karena layar yang terkunci berdetik-detik terasa rusak. */
private const val MAX_AUTO_WAIT_SECONDS = 120L

/**
 * Berapa detik menunggu sebelum mengirim ulang setelah 429, atau null jika tidak boleh dicoba ulang otomatis.
 * Header `Retry-After` dipatuhi. Jika tidak ada (misalnya 429 dari proxy tanpa body JSON) menunggu 30 detik dan
 * menggandakannya di setiap percobaan ulang. [attempt] dimulai dari 1 untuk percobaan ulang pertama.
 *
 * `TRASH_DAILY_LIMIT_REACHED` juga 429 tetapi tanpa jeda: batasnya baru reset tengah malam.
 */
fun ApiResult.Failure.rateLimitWaitSeconds(attempt: Int): Long? {
    if (status != 429 || errorCode == "TRASH_DAILY_LIMIT_REACHED") return null

    val wait = retryAfterSeconds ?: (DEFAULT_WAIT_SECONDS shl (attempt - 1).coerceIn(0, 4))

    return if (wait > MAX_AUTO_WAIT_SECONDS) null else wait.coerceAtLeast(1)
}

/**
 * Menjalankan [call] dan mengulanginya sampai [MAX_RATE_LIMIT_RETRIES] kali selama jawabannya 429 yang boleh
 * ditunggu. [onWait] dipanggil tiap detik dengan sisa detik, lalu sekali dengan 0 sebelum percobaan ulang.
 * Hanya cocok untuk aksi yang aman diulang, yaitu yang membawa `Idempotency-Key` yang sama di setiap percobaan.
 * [pause] dipisah supaya pengujian tidak menunggu sungguhan.
 */
suspend fun <T> retryWhenRateLimited(
    onWait: (secondsLeft: Int) -> Unit = {},
    pause: suspend (millis: Long) -> Unit = { delay(it) },
    call: suspend () -> ApiResult<T>
): ApiResult<T> {
    var attempt = 0

    while (true) {
        val result = call()
        val wait = (result as? ApiResult.Failure)?.rateLimitWaitSeconds(attempt + 1)
        if (wait == null || attempt >= MAX_RATE_LIMIT_RETRIES) return result

        attempt++
        for (left in wait downTo 1) {
            onWait(left.toInt())
            pause(1_000)
        }
        onWait(0)
    }
}
