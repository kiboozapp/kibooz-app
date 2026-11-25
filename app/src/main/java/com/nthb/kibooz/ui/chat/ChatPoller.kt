package com.nthb.kibooz.ui.chat

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Jeda polling pesan baru selagi layar percakapan terlihat. */
const val CHAT_POLL_INTERVAL_MILLIS = 5_000L

/**
 * Satu-satunya tempat pengulangan 5 detik. Socket nanti menggantikan kelas ini, jadi layar dan ViewModel
 * hanya tahu "ada tick" dan tidak tahu bahwa ini polling. Berhenti saat coroutine pemanggilnya dibatalkan,
 * misalnya lewat `repeatOnLifecycle`, sehingga polling tidak berjalan selagi layar tidak terlihat. Tick
 * berikutnya baru dimulai setelah [onTick] selesai, jadi dua polling tidak pernah tumpang tindih.
 */
class ChatPoller(
    private val intervalMillis: Long = CHAT_POLL_INTERVAL_MILLIS,
    private val pause: suspend (millis: Long) -> Unit = { delay(it) }
) {
    suspend fun run(onTick: suspend () -> Unit) {
        while (currentCoroutineContext().isActive) {
            pause(intervalMillis)
            onTick()
        }
    }
}
