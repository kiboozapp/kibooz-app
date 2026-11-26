package com.nthb.kibooz.ui.chat

import com.nthb.kibooz.data.remote.ChatFrame
import com.nthb.kibooz.data.remote.ChatLink
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Apa yang diminta sebuah tick dari layar. */
enum class ChatTick {
    /** Ambil pesan setelah kursor. */
    NEW,

    /** Ambil pesan baru, muat ulang pesan terbaru, dan segarkan data percakapan. */
    RELOAD
}

/** Sela tick pengaman selagi socket tersambung. Frame yang hilang tanpa terdeteksi tertangkap di sini. */
const val CHAT_SAFETY_INTERVAL_MILLIS = 30_000L

/** Sela polling daftar percakapan selagi socket putus. Daftar tidak dipoll sebelum ada socket, jadi sela ini lebih longgar dari layar percakapan. */
const val CHAT_LIST_POLL_INTERVAL_MILLIS = 15_000L

/** Sela sebelum tick yang belum selesai diminta lagi selagi socket tersambung. */
const val CHAT_TICK_RETRY_MILLIS = 5_000L

/** Sela terpendek antara dua tick, supaya ruang yang ramai tidak menghabiskan batas permintaan API. */
const val CHAT_TICK_GAP_MILLIS = 2_000L

/** Server mengirim `resync` ke semua socket sekaligus, jadi tiap aplikasi menunggu acak sampai selama ini. */
const val CHAT_RESYNC_JITTER_MILLIS = 3_000L

/**
 * Satu-satunya sumber tick untuk sebuah layar chat. Layar dan ViewModel hanya tahu "ada tick, ambil sekarang".
 * Selagi socket tersambung, tick datang dari frame yang relevan ([tickFor]) dan dari tick pengaman yang jarang.
 * Selagi socket putus, atau tidak pernah tersambung, tick datang dari [ChatPoller] tiap [pollMillis] seperti
 * sebelum ada socket.
 *
 * Tick yang menumpuk selagi tick sebelumnya berjalan digabung jadi satu, dan [ChatTick.RELOAD] menang atas
 * [ChatTick.NEW]. Dua tick tidak pernah berjalan bersamaan, dan jaraknya paling sedikit [minGapMillis]. Frame
 * tidak membawa id pesan, jadi tidak ada jalan bagi frame untuk menggeser kursor.
 *
 * `onTick` mengembalikan false jika tick itu belum selesai. Selagi socket tersambung tick yang sama diminta lagi
 * sesudah [retryMillis], karena tick berikutnya bisa lama. Selagi putus, polling sudah mengulanginya.
 *
 * Berhenti saat coroutine pemanggilnya dibatalkan, misalnya lewat `repeatOnLifecycle`, dan melepas socket saat itu.
 */
class ChatTicker(
    private val link: ChatLink,
    private val pollMillis: Long = CHAT_POLL_INTERVAL_MILLIS,
    private val safetyMillis: Long = CHAT_SAFETY_INTERVAL_MILLIS,
    private val catchUpOnStart: Boolean = true,
    private val pause: suspend (millis: Long) -> Unit = { delay(it) },
    private val resyncJitter: () -> Long = { Random.nextLong(CHAT_RESYNC_JITTER_MILLIS) },
    private val retryMillis: Long = CHAT_TICK_RETRY_MILLIS,
    private val minGapMillis: Long = CHAT_TICK_GAP_MILLIS,
    private val gap: suspend (millis: Long) -> Unit = { delay(it) }
) {
    suspend fun run(tickFor: (ChatFrame) -> ChatTick?, onTick: suspend (ChatTick) -> Boolean) {
        // Tingkat tick yang menunggu, NONE jika tidak ada. Dikosongkan hanya oleh pengonsumsi di bawah
        val pending = AtomicInteger(NONE)
        val wake = Channel<Unit>(Channel.CONFLATED)
        val resyncWaiting = AtomicBoolean(false)
        // Tingkat tick yang harus diulang, dan apakah jeda ulangnya sedang berjalan
        val retryLevel = AtomicInteger(NONE)
        val retryWaiting = AtomicBoolean(false)

        fun request(tick: ChatTick) {
            val level = if (tick == ChatTick.RELOAD) LEVEL_RELOAD else LEVEL_NEW
            pending.updateAndGet { maxOf(it, level) }
            wake.trySend(Unit)
        }

        link.acquire()
        try {
            coroutineScope {
                // Mulai langsung sampai ada di daftar pengamat, supaya frame `ready` tidak terlewat
                launch(start = CoroutineStart.UNDISPATCHED) {
                    link.frames.collect { frame ->
                        val tick = tickFor(frame) ?: return@collect
                        if (frame != ChatFrame.Resync) {
                            request(tick)
                        } else if (resyncWaiting.compareAndSet(false, true)) {
                            // Beberapa resync berdekatan menjadi satu kerja, dan jedanya acak
                            launch {
                                pause(resyncJitter())
                                resyncWaiting.set(false)
                                request(tick)
                            }
                        }
                    }
                }

                launch {
                    link.connected.collectLatest { connected ->
                        val interval = if (connected) safetyMillis else pollMillis
                        val tick = if (connected) ChatTick.RELOAD else ChatTick.NEW
                        ChatPoller(interval, pause).run { request(tick) }
                    }
                }

                // Layar tampil lagi dengan socket yang sudah tersambung, jadi tidak ada frame `ready` yang akan datang
                if (catchUpOnStart && link.connected.value) request(ChatTick.RELOAD)

                for (signal in wake) {
                    val level = pending.getAndSet(NONE)
                    if (level == NONE) continue

                    if (onTick(tickOf(level))) {
                        retryLevel.updateAndGet { if (it <= level) NONE else it }
                    } else if (link.connected.value) {
                        retryLevel.updateAndGet { maxOf(it, level) }
                        if (retryWaiting.compareAndSet(false, true)) {
                            launch {
                                pause(retryMillis)
                                retryWaiting.set(false)
                                val again = retryLevel.getAndSet(NONE)
                                if (again != NONE) request(tickOf(again))
                            }
                        }
                    }
                    gap(minGapMillis)
                }
            }
        } finally {
            link.release()
        }
    }

    private fun tickOf(level: Int): ChatTick = if (level == LEVEL_RELOAD) ChatTick.RELOAD else ChatTick.NEW

    private companion object {
        const val NONE = 0
        const val LEVEL_NEW = 1
        const val LEVEL_RELOAD = 2
    }
}

/**
 * Frame yang berarti sesuatu untuk layar satu percakapan. Pesan baru cukup mengambil pesan setelah kursor. Pesan
 * yang dihapus dan tanda baca lawan bicara tidak ikut di `after`, jadi keduanya memuat ulang. Tanda baca dari akun
 * sendiri tidak mengubah apa pun di layar ini. Frame tanpa nama percakapan dianggap relevan.
 */
fun ChatFrame.tickForConversation(conversationId: String, ownUserId: String): ChatTick? {
    fun aboutThis(id: String?) = id.isNullOrEmpty() || id == conversationId

    return when (val frame = this) {
        ChatFrame.Ready, ChatFrame.Resync -> ChatTick.RELOAD
        is ChatFrame.NewMessage -> if (aboutThis(frame.conversationId)) ChatTick.NEW else null
        is ChatFrame.MessageDeleted -> if (aboutThis(frame.conversationId)) ChatTick.RELOAD else null
        is ChatFrame.Read -> if (aboutThis(frame.conversationId) && frame.userId != ownUserId) ChatTick.RELOAD else null
    }
}

/**
 * Frame yang mengubah daftar percakapan, yaitu pesan baru, pesan dihapus, dan tanda baca akun sendiri dari
 * perangkat lain karena itu mengubah jumlah belum dibaca. Tanda baca lawan bicara tidak mengubah daftar.
 */
fun ChatFrame.tickForList(ownUserId: String): ChatTick? = when (val frame = this) {
    ChatFrame.Ready, ChatFrame.Resync, is ChatFrame.NewMessage, is ChatFrame.MessageDeleted -> ChatTick.RELOAD
    is ChatFrame.Read -> if (frame.userId == null || frame.userId == ownUserId) ChatTick.RELOAD else null
}
