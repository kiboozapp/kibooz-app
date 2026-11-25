package com.nthb.kibooz.ui.chat

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nthb.kibooz.R
import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.ChatMessage
import com.nthb.kibooz.data.remote.ServiceOutage
import com.nthb.kibooz.data.remote.errorMessageRes
import com.nthb.kibooz.data.remote.messageRes
import com.nthb.kibooz.data.remote.outage
import com.nthb.kibooz.data.remote.outageKind
import com.nthb.kibooz.data.remote.retryWhenRateLimited
import com.nthb.kibooz.data.repository.ChatRepository
import com.nthb.kibooz.data.repository.PendingMessage
import kotlin.random.Random
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Apa yang diketahui layar percakapan saat dibuka, dari daftar percakapan atau dari balasan buka percakapan. */
data class ChatInfo(
    val conversationId: String,
    val isRoom: Boolean,
    val peerDeleted: Boolean,
    val canSend: Boolean,
    val peerLastReadMessageId: Long
)

/** Pesan sendiri yang belum diterima server. Tetap ada sampai ada jawaban 2xx atau pengguna membuangnya. */
data class LocalMessage(val pending: PendingMessage, val status: LocalStatus)

sealed interface LocalStatus {
    /** Sedang dikirim. [waitSeconds] lebih dari 0 selama menunggu jeda `Retry-After` sebelum percobaan berikutnya. */
    data class Sending(val waitSeconds: Int = 0) : LocalStatus

    /** Gagal terkirim. Kirim ulang memakai kunci yang sama, jadi tidak pernah menulis dua pesan. */
    data class Failed(@StringRes val error: Int) : LocalStatus
}

data class ChatNotice(@StringRes val res: Int)

data class ChatState(
    /** True selama muat awal. */
    val loading: Boolean = true,
    /** True setelah muat awal berhasil. Polling dan kirim hanya jalan sesudah ini. */
    val loaded: Boolean = false,
    @StringRes val loadError: Int? = null,
    /** Server tidak terjangkau saat muat awal. Layar menampilkan hitung mundur lalu memanggil [ChatViewModel.retryAfterOutage]. */
    val outage: ServiceOutage? = null,
    /** Percakapan sudah tidak ada atau bukan milik akun ini. Tidak ada gunanya mencoba lagi. */
    val gone: Boolean = false,
    val timeline: ChatTimeline = ChatTimeline(),
    val hasMoreOlder: Boolean = false,
    val loadingOlder: Boolean = false,
    @StringRes val olderError: Int? = null,
    val local: LocalMessage? = null,
    val canSend: Boolean = true,
    val peerLastReadMessageId: Long = 0,
    /** Pesan yang sedang dihapus. */
    val deletingId: Long? = null,
    /** Polling gagal karena tidak ada koneksi. Pesan lama tetap tampil dan polling terus mencoba. */
    val offline: Boolean = false,
    val notice: ChatNotice? = null,
    /** Teks yang tidak jadi terkirim karena percakapan ditutup. Layar mengembalikannya ke kotak tulis. */
    val restoreDraft: String? = null
) {
    /** Kotak tulis dan tombol kirim hidup hanya jika percakapan terbuka dan tidak ada pesan yang masih menunggu. */
    val canCompose: Boolean get() = canSend && loaded && local == null
}

/**
 * Layar percakapan. Pesan dimuat dari yang terbaru, yang lebih lama dimuat saat layar digulir ke atas, dan
 * [poll] mengambil pesan baru selagi layar terlihat. Pesan sendiri dikirim satu per satu dengan satu
 * `Idempotency-Key` untuk tiap pesan yang disimpan sampai ada jawaban 2xx. Selama ada pesan yang menunggu
 * atau gagal, pesan berikutnya tidak bisa dikirim, supaya urutan dan kunci tidak pernah tertukar. Pesan yang
 * belum sampai ke server hilang jika proses mati, dan itu diterima untuk tahap REST ini.
 */
class ChatViewModel(
    private val repository: ChatRepository,
    private val info: ChatInfo,
    private val ownUserId: String,
    private val pause: suspend (millis: Long) -> Unit = { delay(it) },
    private val jitter: () -> Long = { Random.nextLong(OUTAGE_JITTER_SECONDS) }
) : ViewModel() {

    private val conversationId = info.conversationId

    private val _state = MutableStateFlow(
        ChatState(canSend = info.canSend, peerLastReadMessageId = info.peerLastReadMessageId)
    )
    val state: StateFlow<ChatState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var olderJob: Job? = null

    private var outageAttempts = 0
    private var pollTicks = 0
    private var pollSkips = 0

    /**
     * Kursor `after` untuk polling. Hanya maju dari jawaban daftar server, tidak pernah dari pesan yang baru dikirim
     * atau dihapus sendiri. Kalau tidak, pesan lawan bicara dengan id di bawah pesan kita terlewat selamanya.
     */
    private var pollCursor = 0L

    /** Id pesan terakhir yang tanda bacanya sudah dikirim atau sedang dikirim. */
    private var lastReadSent = 0L
    private var visible = false
    private var atBottom = true

    init {
        load()
    }

    // Muat awal

    /** Memuat pesan terbaru dari awal, misalnya saat pengguna menekan coba lagi. */
    fun load() {
        outageAttempts = 0
        fetchLatest()
    }

    /** Percobaan otomatis setelah gangguan. Jeda berikutnya lebih panjang selama gangguan berlanjut. */
    fun retryAfterOutage() = fetchLatest()

    private fun fetchLatest() {
        loadJob?.cancel()
        _state.update { it.copy(loading = true, loadError = null, outage = null) }
        loadJob = viewModelScope.launch {
            when (val result = repository.latestMessages(conversationId)) {
                is ApiResult.Success -> {
                    outageAttempts = 0
                    advancePollCursor(result.data.messages)
                    _state.update {
                        it.copy(
                            loading = false,
                            loaded = true,
                            timeline = it.timeline.merge(result.data.messages),
                            hasMoreOlder = if (it.timeline.messages.isEmpty()) {
                                result.data.hasMore && result.data.messages.isNotEmpty()
                            } else {
                                it.hasMoreOlder
                            }
                        )
                    }
                    markReadIfSeen()
                }
                else -> {
                    val outage = if (result.outageKind() != null) result.outage(++outageAttempts, jitter()) else null
                    val gone = result is ApiResult.Failure && result.errorCode == CONVERSATION_NOT_FOUND
                    _state.update {
                        it.copy(loading = false, loadError = result.errorMessageRes(), outage = outage, gone = gone)
                    }
                }
            }
        }
    }

    // Riwayat

    /**
     * Memuat pesan yang lebih lama. Dipanggil otomatis saat layar digulir dekat ke pesan teratas, dan dengan
     * [retry] true saat pengguna menekan baris galat. Setelah galat, panggilan otomatis tidak mencoba lagi sendiri.
     */
    fun loadOlder(retry: Boolean = false) {
        val snapshot = _state.value
        if (!snapshot.loaded || !snapshot.hasMoreOlder || snapshot.loadingOlder) return
        if (snapshot.olderError != null && !retry) return
        val before = snapshot.timeline.oldestId ?: return

        _state.update { it.copy(loadingOlder = true, olderError = null) }
        olderJob = viewModelScope.launch {
            when (val result = repository.olderMessages(conversationId, before)) {
                is ApiResult.Success -> _state.update {
                    it.copy(
                        loadingOlder = false,
                        timeline = it.timeline.merge(result.data.messages),
                        hasMoreOlder = result.data.hasMore && result.data.messages.isNotEmpty()
                    )
                }
                else -> _state.update { it.copy(loadingOlder = false, olderError = result.errorMessageRes()) }
            }
        }
    }

    // Polling

    /**
     * Satu tick polling, dipanggil oleh [ChatPoller] selagi layar terlihat. Mengambil pesan setelah `id` terbesar
     * dan menggabungkannya. Setiap [SLOW_EVERY] tick juga memuat ulang pesan terbaru, karena `after` tidak membawa
     * pesan lama yang dihapus lawan bicara, dan menyegarkan `canSend` serta tanda baca lawan bicara.
     */
    suspend fun poll() {
        val start = _state.value
        if (!start.loaded || start.gone || start.local?.status is LocalStatus.Sending) return
        if (pollSkips > 0) {
            pollSkips--
            return
        }
        pollTicks++

        var cursor = pollCursor
        val fetched = mutableListOf<ChatMessage>()

        for (page in 1..MAX_POLL_PAGES) {
            when (val result = repository.newerMessages(conversationId, cursor)) {
                is ApiResult.Success -> {
                    fetched += result.data.messages
                    cursor = result.data.messages.lastOrNull()?.id ?: break
                    if (!result.data.hasMore) break
                }
                is ApiResult.Failure -> {
                    onPollFailure(result)
                    return
                }
                is ApiResult.NetworkError -> {
                    _state.update { it.copy(offline = true) }
                    return
                }
            }
        }

        // Pesan sendiri sedang dikirim, jadi hasilnya dibuang dan tick berikutnya mengambilnya lagi tanpa pesan ganda
        if (_state.value.local?.status is LocalStatus.Sending) return

        advancePollCursor(fetched)
        _state.update { it.copy(offline = false, timeline = it.timeline.merge(fetched)) }
        if (fetched.isNotEmpty()) markReadIfSeen()
        if (pollTicks % SLOW_EVERY == 0) refreshSlowly()
    }

    private fun onPollFailure(failure: ApiResult.Failure) {
        when {
            failure.status == 429 -> pollSkips = ((failure.retryAfterSeconds ?: DEFAULT_POLL_WAIT_SECONDS) / POLL_SECONDS).toInt() + 1
            failure.errorCode == CONVERSATION_NOT_FOUND ->
                _state.update { it.copy(gone = true, loadError = failure.messageRes()) }
            failure.outageKind() != null -> _state.update { it.copy(offline = true) }
        }
    }

    private suspend fun refreshSlowly() {
        val latest = repository.latestMessages(conversationId)
        if (latest is ApiResult.Success && _state.value.local?.status !is LocalStatus.Sending) {
            advancePollCursor(latest.data.messages)
            _state.update { it.copy(timeline = it.timeline.merge(latest.data.messages)) }
        }

        val found = repository.findConversation(conversationId)
        if (found is ApiResult.Success) {
            found.data?.let { conversation ->
                _state.update { it.copy(canSend = conversation.canSend, peerLastReadMessageId = conversation.peerLastReadMessageId) }
            }
        }
    }

    // Kirim

    /**
     * Mengirim [text] sebagai pesan baru. Mengembalikan true jika pesan diterima untuk dikirim, supaya layar baru
     * mengosongkan kotak tulis saat itu. Ditolak jika kosong, terlalu panjang, jika percakapan tertutup, atau jika
     * masih ada pesan sendiri yang menunggu atau gagal.
     */
    fun send(text: String): Boolean {
        val body = text.trim()
        val snapshot = _state.value
        if (body.isEmpty() || !snapshot.canCompose) return false
        if (body.length > MAX_BODY_LENGTH) {
            _state.update { it.copy(notice = ChatNotice(R.string.chat_error_terlalu_panjang)) }
            return false
        }

        launchSend(LocalMessage(PendingMessage(body), LocalStatus.Sending()))
        return true
    }

    /** Mengirim ulang pesan yang gagal dengan kunci yang sama. */
    fun retry() {
        val local = _state.value.local ?: return
        if (local.status !is LocalStatus.Failed) return

        launchSend(local.copy(status = LocalStatus.Sending()))
    }

    /** Membuang pesan yang gagal supaya pesan berikutnya bisa dikirim. Server mungkin sudah menerimanya, dan itu muncul lewat polling. */
    fun discardFailed() {
        _state.update { if (it.local?.status is LocalStatus.Failed) it.copy(local = null) else it }
    }

    private fun launchSend(local: LocalMessage) {
        _state.update { it.copy(local = local) }
        viewModelScope.launch { deliver(local.pending) }
    }

    private suspend fun deliver(pending: PendingMessage) {
        val first = sendOnce(pending)
        val result = if (first is ApiResult.Failure && first.errorCode == IN_PROGRESS) {
            // Percobaan pertama dengan kunci ini masih berjalan di server, tunggu lalu kirim sekali lagi
            pause(IN_PROGRESS_WAIT_MILLIS)
            sendOnce(pending)
        } else {
            first
        }

        when (result) {
            is ApiResult.Success -> {
                _state.update { it.copy(timeline = it.timeline.merge(listOf(result.data)), local = null) }
                markReadIfSeen()
            }
            is ApiResult.Failure -> onSendFailure(pending, result)
            is ApiResult.NetworkError -> setFailed(pending, R.string.error_network)
        }
    }

    private suspend fun sendOnce(pending: PendingMessage): ApiResult<ChatMessage> = retryWhenRateLimited(
        onWait = { secondsLeft ->
            _state.update { state ->
                state.local?.let { state.copy(local = it.copy(status = LocalStatus.Sending(secondsLeft))) } ?: state
            }
        },
        pause = pause
    ) { repository.send(conversationId, pending) }

    private fun onSendFailure(pending: PendingMessage, failure: ApiResult.Failure) {
        when (failure.errorCode) {
            SENDING_CLOSED -> _state.update {
                it.copy(
                    local = null,
                    canSend = false,
                    restoreDraft = pending.body,
                    notice = ChatNotice(failure.messageRes())
                )
            }
            CONVERSATION_NOT_FOUND -> _state.update {
                it.copy(local = null, gone = true, loadError = failure.messageRes(), restoreDraft = pending.body)
            }
            // Kunci ini sudah dipakai untuk isi lain, jadi pesan yang sama mendapat kunci baru untuk percobaan berikutnya
            KEY_REUSED -> _state.update {
                it.copy(local = LocalMessage(PendingMessage(pending.body), LocalStatus.Failed(failure.messageRes())))
            }
            else -> setFailed(pending, failure.messageRes())
        }
    }

    private fun setFailed(pending: PendingMessage, @StringRes error: Int) {
        _state.update { it.copy(local = LocalMessage(pending, LocalStatus.Failed(error))) }
    }

    // Hapus pesan sendiri

    fun delete(messageId: Long) {
        val snapshot = _state.value
        if (snapshot.deletingId != null) return
        val message = snapshot.timeline.messages.firstOrNull { it.id == messageId } ?: return
        if (message.deleted || message.senderId != ownUserId) return

        _state.update { it.copy(deletingId = messageId) }
        viewModelScope.launch {
            when (val result = repository.delete(conversationId, messageId)) {
                is ApiResult.Success -> _state.update {
                    it.copy(deletingId = null, timeline = it.timeline.merge(listOf(result.data)))
                }
                else -> {
                    _state.update { it.copy(deletingId = null, notice = ChatNotice(result.errorMessageRes() ?: R.string.error_unknown)) }
                    // Pesannya sudah tidak ada di server, jadi daftar di layar basi
                    if (result is ApiResult.Failure && result.errorCode == MESSAGE_NOT_FOUND) refreshLatestQuietly()
                }
            }
        }
    }

    private suspend fun refreshLatestQuietly() {
        val latest = repository.latestMessages(conversationId)
        if (latest is ApiResult.Success) {
            advancePollCursor(latest.data.messages)
            _state.update { it.copy(timeline = it.timeline.merge(latest.data.messages)) }
        }
    }

    private fun advancePollCursor(fromServerList: List<ChatMessage>) {
        fromServerList.maxOfOrNull { it.id }?.let { if (it > pollCursor) pollCursor = it }
    }

    // Tanda baca

    /** Layar terlihat atau tidak. Tanda baca hanya dikirim selagi layar terlihat. */
    fun setVisible(value: Boolean) {
        visible = value
        if (value) markReadIfSeen()
    }

    /** Pengguna sedang melihat pesan terbawah atau sedang menggulir riwayat. Pesan baru yang belum terlihat tidak ditandai terbaca. */
    fun setAtBottom(value: Boolean) {
        atBottom = value
        if (value) markReadIfSeen()
    }

    private fun markReadIfSeen() {
        if (!visible || !atBottom) return
        val messages = _state.value.timeline.messages
        val newest = messages.lastOrNull()?.id ?: return
        // Tidak ada pesan lawan bicara yang lebih baru dari tanda terakhir, jadi tidak ada yang perlu ditandai
        if (messages.none { it.senderId != ownUserId && it.id > lastReadSent }) return

        val previous = lastReadSent
        lastReadSent = newest
        viewModelScope.launch {
            // Gagal tidak mengganggu pengguna, tanda dicoba lagi saat ada pesan baru atau layar terlihat lagi
            if (repository.markRead(conversationId, newest) !is ApiResult.Success && lastReadSent == newest) lastReadSent = previous
        }
    }

    // Layar sudah menanggapi

    fun noticeShown() {
        _state.update { it.copy(notice = null) }
    }

    fun draftRestored() {
        _state.update { it.copy(restoreDraft = null) }
    }

    class Factory(
        private val repository: ChatRepository,
        private val info: ChatInfo,
        private val ownUserId: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ChatViewModel(repository, info, ownUserId) as T
    }

    private companion object {
        const val MAX_BODY_LENGTH = 4000

        /** Jeda acak 0 sampai 5 detik di atas `Retry-After` supaya semua aplikasi tidak mencoba di detik yang sama. */
        const val OUTAGE_JITTER_SECONDS = 6L

        const val POLL_SECONDS = CHAT_POLL_INTERVAL_MILLIS / 1_000
        const val DEFAULT_POLL_WAIT_SECONDS = 30L

        /** Pengaman: satu tick mengambil paling banyak 5 halaman pesan baru, sisanya diambil tick berikutnya. */
        const val MAX_POLL_PAGES = 5

        /** Tick ke berapa polling juga memuat ulang pesan terbaru dan metadata. Enam tick berarti sekitar 30 detik. */
        const val SLOW_EVERY = 6

        const val IN_PROGRESS_WAIT_MILLIS = 2_000L

        const val CONVERSATION_NOT_FOUND = "CHAT_CONVERSATION_NOT_FOUND"
        const val MESSAGE_NOT_FOUND = "CHAT_MESSAGE_NOT_FOUND"
        const val SENDING_CLOSED = "CHAT_SENDING_CLOSED"
        const val IN_PROGRESS = "IDEMPOTENCY_IN_PROGRESS"
        const val KEY_REUSED = "IDEMPOTENCY_KEY_REUSED"
    }
}
