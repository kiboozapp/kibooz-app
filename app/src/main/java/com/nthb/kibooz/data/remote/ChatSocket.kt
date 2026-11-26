package com.nthb.kibooz.data.remote

import com.squareup.moshi.Moshi
import kotlin.random.Random
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Yang dipakai layar dari socket chat. Layar memanggil [acquire] saat mulai terlihat dan [release] saat berhenti,
 * dan tidak tahu apa pun tentang koneksinya. Dipisah dari [ChatSocket] supaya tes layar memakai tiruan.
 */
interface ChatLink {
    /** True antara frame `ready` dan koneksi terputus. Selama true, tick datang dari [frames]. */
    val connected: StateFlow<Boolean>

    /** Frame yang sudah diurai. Frame yang tidak ada pengamatnya terbuang. */
    val frames: SharedFlow<ChatFrame>

    fun acquire()
    fun release()
}

/** Kenapa satu koneksi berakhir, dari sudut pandang aturan sambung ulang. */
data class ChatSocketEnd(
    /** Kode tutup dari server, atau null jika koneksi putus tanpa kode. */
    val closeCode: Int? = null,
    /** Status HTTP dari handshake yang ditolak, atau null. */
    val httpStatus: Int? = null,
    /** Koneksi sudah menerima `ready` dan bertahan sampai [HEALTHY_CONNECTION_MILLIS]. */
    val healthy: Boolean = false
)

const val CLOSE_GOING_AWAY = 1001
const val CLOSE_POLICY_VIOLATION = 1008
const val CLOSE_TRY_AGAIN_LATER = 1013
const val CLOSE_TOKEN_ENDED = 4001

/** Koneksi yang bertahan selama ini sesudah `ready` dianggap sehat, jadi hitungan kegagalan mulai dari nol lagi. */
const val HEALTHY_CONNECTION_MILLIS = 10_000L

const val RECONNECT_BASE_MILLIS = 1_000L
const val RECONNECT_MAX_MILLIS = 60_000L

/** Server sibuk (1013 atau 503) atau proxy tidak meneruskan upgrade (426), jadi jangan cepat-cepat mencoba lagi. */
const val BUSY_MIN_WAIT_MILLIS = 5_000L

/** Socket tetap terbuka sebentar setelah layar terakhir berhenti, supaya putar layar atau pindah layar tidak memutus koneksi. */
const val CLOSE_GRACE_MILLIS = 15_000L

/** Keputusan sesudah satu koneksi berakhir. */
sealed interface ReconnectPlan {
    /** Jangan sambung ulang sampai layar chat berikutnya terlihat. Polling REST tetap jalan. */
    data object Stop : ReconnectPlan

    /** Tunggu [millis], perbarui token lebih dulu jika [refreshFirst], lalu sambung ulang. */
    data class Wait(val millis: Long, val refreshFirst: Boolean = false) : ReconnectPlan
}

/**
 * Jeda sebelum sambung ulang. [failures] adalah jumlah koneksi gagal berturut-turut sampai sekarang, 0 untuk
 * koneksi sehat yang baru berakhir, dan jedanya berlipat dua tiap kegagalan sampai [RECONNECT_MAX_MILLIS].
 * [floorMillis] adalah jeda terpendek yang boleh, dan jitter menambah sampai satu kali jeda supaya ribuan
 * aplikasi tidak kembali di detik yang sama.
 */
fun reconnectWait(failures: Int, floorMillis: Long = 0L, jitter: (bound: Long) -> Long): Long {
    val grown = minOf(RECONNECT_MAX_MILLIS, RECONNECT_BASE_MILLIS shl minOf(failures.coerceAtLeast(0), 6))
    val base = maxOf(grown, floorMillis)
    return base + jitter(base)
}

/**
 * Aturan sambung ulang dari kode tutup dan status handshake. [failures] sudah termasuk koneksi yang baru berakhir.
 * 1008 berarti aplikasi mengirim frame yang salah, itu bug dan bukan alasan mencoba terus.
 */
fun planReconnect(end: ChatSocketEnd, failures: Int, jitter: (bound: Long) -> Long): ReconnectPlan = when {
    end.closeCode == CLOSE_POLICY_VIOLATION -> ReconnectPlan.Stop
    end.closeCode == CLOSE_TOKEN_ENDED -> ReconnectPlan.Wait(reconnectWait(failures, jitter = jitter), refreshFirst = true)
    end.closeCode == CLOSE_TRY_AGAIN_LATER || end.httpStatus == 503 ->
        ReconnectPlan.Wait(reconnectWait(failures, BUSY_MIN_WAIT_MILLIS, jitter))
    end.httpStatus == 426 -> ReconnectPlan.Wait(reconnectWait(failures, RECONNECT_MAX_MILLIS, jitter))
    else -> ReconnectPlan.Wait(reconnectWait(failures, jitter = jitter))
}

/**
 * Satu socket untuk seluruh proses. Dibuka saat ada layar chat yang terlihat ([acquire]) dan ditutup sesudah
 * layar terakhir berhenti ([release]), dengan [CLOSE_GRACE_MILLIS] sebagai jeda. Server membatasi 5 socket untuk
 * tiap akun dan yang keenam menutup yang tertua, jadi tidak ada satu socket untuk tiap layar.
 *
 * Aturan sambung ulang ada di [planReconnect]. Token diperbarui lewat [refresh], yang sama dengan jalur
 * [TokenAuthenticator], saat server menutup dengan 4001. Untuk 401 saat handshake, authenticator klien sudah
 * memperbarui token dan mengulang sekali. Tidak ada koneksi baru jika tidak ada token (keluar akun) atau tokennya
 * token mock pengembang. Tidak ada token, isi pesan, atau alamat bertoken yang dicatat.
 *
 * [now] adalah jam milidetik monoton, dan [pause] serta [jitter] disuntikkan supaya tes tidak menunggu waktu nyata.
 */
class ChatSocket(
    private val transport: ChatSocketTransport,
    private val tokenProvider: TokenProvider,
    private val refresh: suspend (failedAccessToken: String) -> String?,
    private val moshi: Moshi,
    private val scope: CoroutineScope,
    private val now: () -> Long = { System.nanoTime() / NANOS_PER_MILLI },
    private val pause: suspend (millis: Long) -> Unit = { delay(it) },
    private val jitter: (bound: Long) -> Long = { Random.nextLong(it.coerceAtLeast(1L)) }
) : ChatLink {

    private val _connected = MutableStateFlow(false)
    override val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _frames = MutableSharedFlow<ChatFrame>(extraBufferCapacity = FRAME_BUFFER, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val frames: SharedFlow<ChatFrame> = _frames.asSharedFlow()

    private val lock = Any()
    private var holders = 0
    private var runner: Job? = null
    private var closer: Job? = null

    @Volatile
    private var current: Attempt? = null

    override fun acquire() {
        synchronized(lock) {
            holders++
            closer?.cancel()
            closer = null
            if (runner?.isActive != true) runner = scope.launch { loop() }
        }
    }

    override fun release() {
        synchronized(lock) {
            if (holders == 0) return
            holders--
            if (holders == 0) {
                closer = scope.launch {
                    pause(CLOSE_GRACE_MILLIS)
                    synchronized(lock) {
                        if (holders == 0) {
                            runner?.cancel()
                            runner = null
                        }
                    }
                }
            }
        }
    }

    /**
     * Menutup koneksi sekarang dan membukanya lagi jika masih ada layar yang terlihat. Dipanggil saat akun yang
     * masuk berganti, supaya socket milik akun lama tidak dipakai akun baru.
     */
    fun reset() {
        synchronized(lock) {
            runner?.cancel()
            runner = null
            if (holders > 0) runner = scope.launch { loop() }
        }
    }

    private suspend fun loop() {
        var failures = 0
        var refreshFirst = false

        while (currentCoroutineContext().isActive) {
            if (refreshFirst) {
                refreshFirst = false
                val expired = tokenProvider.accessToken()
                // Token belum bisa diperbarui (jaringan atau 429) tapi sesinya masih ada, jadi coba lagi nanti
                if (!expired.isNullOrBlank() && refresh(expired) == null && !tokenProvider.accessToken().isNullOrBlank()) {
                    failures++
                    refreshFirst = true
                    pause(reconnectWait(failures, jitter = jitter))
                    continue
                }
            }

            val token = tokenProvider.accessToken()
            if (token.isNullOrBlank() || token.startsWith(MOCK_TOKEN_PREFIX)) return

            val end = connectOnce()
            failures = if (end.healthy) 0 else failures + 1

            when (val plan = planReconnect(end, failures, jitter)) {
                ReconnectPlan.Stop -> return
                is ReconnectPlan.Wait -> {
                    refreshFirst = plan.refreshFirst
                    pause(plan.millis)
                }
            }
        }
    }

    private suspend fun connectOnce(): ChatSocketEnd {
        val ended = CompletableDeferred<ChatSocketEnd>()
        val attempt = Attempt()
        current = attempt

        val listener = object : ChatSocketTransport.Listener {
            override fun onOpen() = Unit

            override fun onText(text: String) {
                if (current !== attempt) return
                val frame = parseChatFrame(moshi, text) ?: return
                if (frame == ChatFrame.Ready) {
                    attempt.readyAt = now()
                    _connected.value = true
                }
                _frames.tryEmit(frame)
            }

            override fun onClosed(code: Int) {
                ended.complete(ChatSocketEnd(closeCode = code, healthy = attempt.healthy()))
            }

            override fun onFailure(httpStatus: Int?) {
                ended.complete(ChatSocketEnd(httpStatus = httpStatus, healthy = attempt.healthy()))
            }
        }

        val connection = transport.connect(listener)
        try {
            return ended.await()
        } finally {
            connection.close()
            // Koneksi lama yang dibatalkan oleh reset() tidak boleh menandai koneksi baru sebagai putus
            if (current === attempt) {
                current = null
                _connected.value = false
            }
        }
    }

    private inner class Attempt {
        @Volatile
        var readyAt = NOT_READY

        fun healthy(): Boolean = readyAt != NOT_READY && now() - readyAt >= HEALTHY_CONNECTION_MILLIS
    }

    private companion object {
        const val FRAME_BUFFER = 64
        const val NANOS_PER_MILLI = 1_000_000L
        const val MOCK_TOKEN_PREFIX = "mock-"
        const val NOT_READY = Long.MIN_VALUE
    }
}
