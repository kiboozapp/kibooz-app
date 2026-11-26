package com.nthb.kibooz.ui.chat

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nthb.kibooz.R
import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.ChatAttachment
import com.nthb.kibooz.data.remote.ChatMessage
import com.nthb.kibooz.data.remote.ERROR_INSECURE_ADDRESS
import com.nthb.kibooz.data.remote.ERROR_UPLOAD_REJECTED
import com.nthb.kibooz.data.remote.ERROR_UPLOAD_SOURCE
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
data class LocalMessage(val pending: PendingMessage, val status: LocalStatus, val attachment: ChatAttachment? = null)

sealed interface LocalStatus {
    /**
     * Sedang dikirim. [waitSeconds] lebih dari 0 selama menunggu jeda `Retry-After` sebelum percobaan berikutnya.
     * [uploadPercent] terisi selama lampiran diunggah, dan null selama pesannya sendiri dikirim.
     */
    data class Sending(val waitSeconds: Int = 0, val uploadPercent: Int? = null) : LocalStatus

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
    val restoreDraft: String? = null,
    /** Lampiran yang sudah dipilih dan belum dikirim. */
    val attachment: ChatAttachment? = null,
    /** Berkas pilihan sedang disiapkan, misalnya foto sedang diperkecil. */
    val preparing: Boolean = false,
    /** Server menjawab `STORAGE_UNAVAILABLE`, jadi lampiran tidak ditawarkan lagi. */
    val attachmentsOff: Boolean = false,
    /** Pesan yang lampirannya siap dibuka. Layar membukanya lalu memanggil [ChatViewModel.mediaOpened]. */
    val openMedia: ChatMessage? = null
) {
    /** Kotak tulis dan tombol kirim hidup hanya jika percakapan terbuka dan tidak ada pesan yang masih menunggu. */
    val canCompose: Boolean get() = canSend && loaded && local == null

    val canAttach: Boolean get() = canCompose && !preparing && !attachmentsOff

    /** True selagi pesannya sendiri sedang dikirim. Mengunggah lampiran tidak dihitung. */
    val posting: Boolean get() = (local?.status as? LocalStatus.Sending)?.let { it.uploadPercent == null } == true
}

/**
 * Layar percakapan. Pesan dimuat dari yang terbaru, yang lebih lama dimuat saat layar digulir ke atas, dan
 * [poll] mengambil pesan baru selagi layar terlihat. Pesan sendiri dikirim satu per satu dengan satu
 * `Idempotency-Key` untuk tiap pesan yang disimpan sampai ada jawaban 2xx. Selama ada pesan yang menunggu
 * atau gagal, pesan berikutnya tidak bisa dikirim, supaya urutan dan kunci tidak pernah tertukar. Pesan yang
 * belum sampai ke server hilang jika proses mati, dan itu diterima untuk tahap REST ini.
 *
 * Lampiran diunggah dulu lalu `mediaKey`-nya ikut disimpan di pesan yang menunggu. Unggahan hidup di
 * [viewModelScope], jadi memutar layar tidak mengulanginya, dan menutup layar membatalkannya serta menghapus
 * berkas sementara.
 */
class ChatViewModel(
    private val repository: ChatRepository,
    private val info: ChatInfo,
    private val ownUserId: String,
    private val pause: suspend (millis: Long) -> Unit = { delay(it) },
    private val jitter: () -> Long = { Random.nextLong(OUTAGE_JITTER_SECONDS) },
    /** Jam milidetik monoton, untuk menghitung jeda `Retry-After`. */
    private val now: () -> Long = { System.nanoTime() / 1_000_000L },
    private val preparer: AttachmentPreparer =
        AttachmentPreparer { PreparedAttachment.Rejected(R.string.chat_error_lampiran_tidak_terbaca) }
) : ViewModel() {

    private val conversationId = info.conversationId

    private val _state = MutableStateFlow(
        ChatState(canSend = info.canSend, peerLastReadMessageId = info.peerLastReadMessageId)
    )
    val state: StateFlow<ChatState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var olderJob: Job? = null
    private var prepareJob: Job? = null
    private var openJob: Job? = null

    /** Waktu menurut [now] saat alamat lampiran tiap pesan terakhir diminta ulang. */
    private val mediaRefreshedAt = mutableMapOf<Long, Long>()

    private var outageAttempts = 0
    private var pollTicks = 0

    /** Waktu menurut [now] sebelum polling tidak boleh meminta apa pun, atau null jika tidak sedang dibatasi. */
    private var pollBlockedUntil: Long? = null

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
     * Satu tick, dipanggil oleh [ChatTicker] selagi layar terlihat, entah dari polling atau dari frame socket.
     * Layar tidak tahu bedanya. Mengambil pesan setelah `id` terbesar dari kursor dan menggabungkannya. Setiap
     * [SLOW_EVERY] tick, dan pada tick dengan [reload] true, juga memuat ulang pesan terbaru, karena `after` tidak
     * membawa pesan lama yang dihapus lawan bicara, dan menyegarkan `canSend` serta tanda baca lawan bicara.
     *
     * Mengembalikan false jika tick ini belum selesai dan perlu diulang, misalnya selagi muat awal atau kirim masih
     * jalan, selagi menunggu `Retry-After`, atau jika permintaannya gagal. Unggahan lampiran tidak menahan tick,
     * karena belum ada pesan yang bisa tampil ganda dan unggahan video bisa lama.
     */
    suspend fun poll(reload: Boolean = false): Boolean {
        val start = _state.value
        if (start.gone) return true
        if (!start.loaded || start.posting) return false
        pollBlockedUntil?.let { if (now() - it < 0) return false }
        pollBlockedUntil = null
        pollTicks++

        var cursor = pollCursor
        val fetched = mutableListOf<ChatMessage>()
        var more = false

        for (page in 1..MAX_POLL_PAGES) {
            when (val result = repository.newerMessages(conversationId, cursor)) {
                is ApiResult.Success -> {
                    fetched += result.data.messages
                    val last = result.data.messages.lastOrNull()?.id
                    more = result.data.hasMore && last != null
                    if (last == null || !more) break
                    cursor = last
                }
                is ApiResult.Failure -> {
                    onPollFailure(result)
                    return _state.value.gone
                }
                is ApiResult.NetworkError -> {
                    _state.update { it.copy(offline = true) }
                    return false
                }
            }
        }

        // Pesan sendiri sedang dikirim, jadi hasilnya dibuang dan tick berikutnya mengambilnya lagi tanpa pesan ganda
        if (_state.value.posting) return false

        advancePollCursor(fetched)
        _state.update { it.copy(offline = false, timeline = it.timeline.merge(fetched)) }
        if (fetched.isNotEmpty()) markReadIfSeen()
        val refreshed = if (reload || pollTicks % SLOW_EVERY == 0) refreshSlowly() else true
        return !more && (refreshed || !reload)
    }

    private fun onPollFailure(failure: ApiResult.Failure) {
        when {
            failure.status == 429 ->
                pollBlockedUntil = now() + (failure.retryAfterSeconds ?: DEFAULT_POLL_WAIT_SECONDS) * 1_000L
            failure.errorCode == CONVERSATION_NOT_FOUND ->
                _state.update { it.copy(gone = true, loadError = failure.messageRes()) }
            failure.outageKind() != null -> _state.update { it.copy(offline = true) }
        }
    }

    /** True jika pesan terbaru dan data percakapan sama-sama berhasil disegarkan. */
    private suspend fun refreshSlowly(): Boolean {
        val latest = repository.latestMessages(conversationId)
        if (latest is ApiResult.Failure) {
            onPollFailure(latest)
            if (latest.status == 429) return false
        }
        val merged = latest is ApiResult.Success && !_state.value.posting
        if (latest is ApiResult.Success && merged) {
            advancePollCursor(latest.data.messages)
            _state.update { it.copy(timeline = it.timeline.merge(latest.data.messages)) }
        }

        val found = repository.findConversation(conversationId)
        if (found is ApiResult.Success) {
            found.data?.let { conversation ->
                _state.update { it.copy(canSend = conversation.canSend, peerLastReadMessageId = conversation.peerLastReadMessageId) }
            }
        } else if (found is ApiResult.Failure) {
            onPollFailure(found)
        }
        return merged && found is ApiResult.Success
    }

    // Lampiran yang dipilih

    /** Menyiapkan berkas [uri] sebagai lampiran pesan berikutnya, menggantikan yang sudah dipilih. */
    fun attach(uri: String) {
        val snapshot = _state.value
        // Muat awal tidak ditunggu, karena hasil pemilih bisa datang ke proses yang baru hidup lagi
        if (!snapshot.canSend || snapshot.gone || snapshot.local != null || snapshot.attachmentsOff) return

        removeAttachment()
        _state.update { it.copy(preparing = true) }
        prepareJob = viewModelScope.launch {
            when (val result = preparer.prepare(uri)) {
                is PreparedAttachment.Ready -> _state.update { it.copy(preparing = false, attachment = result.attachment) }
                is PreparedAttachment.Rejected -> _state.update {
                    it.copy(preparing = false, notice = ChatNotice(result.error))
                }
            }
        }
    }

    /** Membatalkan lampiran yang belum dikirim. */
    fun removeAttachment() {
        prepareJob?.cancel()
        _state.value.attachment?.release()
        _state.update { it.copy(attachment = null, preparing = false) }
    }

    // Kirim

    /**
     * Mengirim [text] sebagai pesan baru, bersama lampiran yang dipilih jika ada. Mengembalikan true jika pesan
     * diterima untuk dikirim, supaya layar baru mengosongkan kotak tulis saat itu. Ditolak jika kosong tanpa
     * lampiran, terlalu panjang, jika percakapan tertutup, selagi lampiran disiapkan, atau jika masih ada pesan
     * sendiri yang menunggu atau gagal.
     */
    fun send(text: String): Boolean {
        val body = text.trim()
        val snapshot = _state.value
        val attachment = snapshot.attachment
        if ((body.isEmpty() && attachment == null) || !snapshot.canCompose || snapshot.preparing) return false
        if (body.length > MAX_BODY_LENGTH) {
            _state.update { it.copy(notice = ChatNotice(R.string.chat_error_terlalu_panjang)) }
            return false
        }

        _state.update { it.copy(attachment = null) }
        launchSend(PendingMessage(body), attachment)
        return true
    }

    /** Mengirim ulang pesan yang gagal dengan kunci yang sama. Lampiran yang sudah terunggah tidak diunggah lagi. */
    fun retry() {
        val local = _state.value.local ?: return
        if (local.status !is LocalStatus.Failed) return

        launchSend(local.pending, local.attachment)
    }

    /** Membuang pesan yang gagal supaya pesan berikutnya bisa dikirim. Server mungkin sudah menerimanya, dan itu muncul lewat polling. */
    fun discardFailed() {
        val local = _state.value.local ?: return
        if (local.status !is LocalStatus.Failed) return

        local.attachment?.release()
        _state.update { it.copy(local = null) }
    }

    private fun launchSend(pending: PendingMessage, attachment: ChatAttachment?) {
        val needsUpload = attachment != null && pending.mediaKey == null
        showSending(pending, attachment, uploadPercent = if (needsUpload) 0 else null)
        viewModelScope.launch { deliver(pending, attachment) }
    }

    private suspend fun deliver(start: PendingMessage, attachment: ChatAttachment?) {
        // Kunci unggahan dari percobaan sebelumnya bisa sudah tidak berlaku, jadi boleh diunggah ulang sekali
        val keyMayBeStale = start.mediaKey != null
        var pending = start
        if (attachment != null && pending.mediaKey == null) pending = upload(pending, attachment) ?: return

        var result = post(pending)
        if (attachment != null && keyMayBeStale && result.rejectsMediaKey()) {
            pending = upload(pending.withMediaKey(null), attachment) ?: return
            result = post(pending)
        }

        when (result) {
            is ApiResult.Success -> {
                attachment?.release()
                _state.update { it.copy(timeline = it.timeline.merge(listOf(result.data)), local = null) }
                markReadIfSeen()
            }
            is ApiResult.Failure -> onSendFailure(pending, attachment, result)
            is ApiResult.NetworkError -> setFailed(pending, attachment, R.string.error_network)
        }
    }

    /** Mengunggah lampiran dan mengembalikan pesan dengan `mediaKey`-nya, atau null setelah menampilkan kegagalannya. */
    private suspend fun upload(pending: PendingMessage, attachment: ChatAttachment): PendingMessage? {
        showSending(pending, attachment, uploadPercent = 0)
        val result = retryWhenRateLimited(onWait = ::showWait, pause = pause) {
            repository.upload(conversationId, attachment) { sent -> showProgress(sent, attachment.sizeBytes) }
        }

        return when (result) {
            is ApiResult.Success -> pending.withMediaKey(result.data).also { showSending(it, attachment, uploadPercent = null) }
            is ApiResult.Failure -> {
                onSendFailure(pending, attachment, result)
                null
            }
            is ApiResult.NetworkError -> {
                setFailed(pending, attachment, R.string.error_network)
                null
            }
        }
    }

    private suspend fun post(pending: PendingMessage): ApiResult<ChatMessage> {
        val first = sendOnce(pending)
        if (first !is ApiResult.Failure || first.errorCode != IN_PROGRESS) return first

        // Percobaan pertama dengan kunci ini masih berjalan di server, tunggu lalu kirim sekali lagi
        pause(IN_PROGRESS_WAIT_MILLIS)
        return sendOnce(pending)
    }

    private suspend fun sendOnce(pending: PendingMessage): ApiResult<ChatMessage> =
        retryWhenRateLimited(onWait = ::showWait, pause = pause) { repository.send(conversationId, pending) }

    private fun showSending(pending: PendingMessage, attachment: ChatAttachment?, uploadPercent: Int?) {
        val status = LocalStatus.Sending(uploadPercent = uploadPercent)
        _state.update { it.copy(local = LocalMessage(pending, status, attachment)) }
    }

    private fun showWait(secondsLeft: Int) = updateSending { it.copy(waitSeconds = secondsLeft) }

    // Dipanggil dari thread jaringan. Dibulatkan ke kelipatan 5 supaya layar tidak digambar ulang tiap potongan
    private fun showProgress(sentBytes: Long, totalBytes: Long) {
        val percent = (sentBytes * 100 / totalBytes.coerceAtLeast(1)).toInt().coerceIn(0, 100)
        updateSending { if (it.uploadPercent == null) it else it.copy(uploadPercent = percent - percent % PROGRESS_STEP) }
    }

    private fun updateSending(change: (LocalStatus.Sending) -> LocalStatus.Sending) {
        _state.update { state ->
            val local = state.local ?: return@update state
            val status = local.status as? LocalStatus.Sending ?: return@update state
            state.copy(local = local.copy(status = change(status)))
        }
    }

    private fun onSendFailure(pending: PendingMessage, attachment: ChatAttachment?, failure: ApiResult.Failure) {
        when {
            failure.errorCode == SENDING_CLOSED -> giveBack(pending, attachment) {
                it.copy(canSend = false, notice = ChatNotice(failure.messageRes()))
            }
            failure.errorCode == CONVERSATION_NOT_FOUND -> giveBack(pending, attachment) {
                it.copy(gone = true, loadError = failure.messageRes())
            }
            // Mencoba lagi tidak menolong selama server tidak punya tempat menyimpan berkas
            failure.errorCode == STORAGE_UNAVAILABLE && attachment != null -> giveBack(pending, attachment) {
                it.copy(attachmentsOff = true, notice = ChatNotice(failure.messageRes()))
            }
            // Kunci ini sudah dipakai untuk isi lain, jadi pesan yang sama mendapat kunci baru untuk percobaan berikutnya
            failure.errorCode == KEY_REUSED -> setFailed(
                PendingMessage(pending.body, mediaKey = pending.mediaKey),
                attachment,
                failure.messageRes()
            )
            attachment != null && failure.rejectsMediaKey() ->
                setFailed(pending.withMediaKey(null), attachment, R.string.chat_error_lampiran_belum_diterima)
            failure.errorCode == ERROR_UPLOAD_SOURCE ->
                setFailed(pending, attachment, R.string.chat_error_berkas_berubah)
            failure.errorCode == ERROR_UPLOAD_REJECTED || failure.errorCode == ERROR_INSECURE_ADDRESS ->
                setFailed(pending, attachment, R.string.chat_error_unggah_ditolak)
            else -> setFailed(pending, attachment, failure.messageRes())
        }
    }

    /** Membatalkan pesan yang tidak akan pernah terkirim. Teksnya kembali ke kotak tulis dan lampirannya dibuang. */
    private fun giveBack(pending: PendingMessage, attachment: ChatAttachment?, change: (ChatState) -> ChatState) {
        attachment?.release()
        _state.update { change(it.copy(local = null, restoreDraft = pending.body)) }
    }

    private fun setFailed(pending: PendingMessage, attachment: ChatAttachment?, @StringRes error: Int) {
        _state.update { it.copy(local = LocalMessage(pending, LocalStatus.Failed(error), attachment)) }
    }

    private fun ApiResult<*>.rejectsMediaKey(): Boolean =
        this is ApiResult.Failure && this.errorCode == VALIDATION_ERROR && this.details?.containsKey("mediaKey") == true

    // Lampiran pesan

    /** Meminta lampiran sebuah pesan dibuka. Alamatnya diminta ulang dulu, karena alamat lama bisa sudah habis. */
    fun openMedia(messageId: Long) {
        if (openJob?.isActive == true) return
        val shown = messageWithMedia(messageId) ?: return

        openJob = viewModelScope.launch {
            // Jika permintaannya gagal, pesan yang ada tetap dibuka karena fotonya bisa sudah tersimpan
            val latest = fetchMessage(messageId) ?: shown
            _state.update {
                if (latest.deleted || latest.media == null) {
                    it.copy(notice = ChatNotice(R.string.chat_pesan_dihapus))
                } else {
                    it.copy(openMedia = latest)
                }
            }
        }
    }

    /** Meminta alamat baru untuk lampiran yang gagal dimuat, paling sering sekali semenit untuk tiap pesan. */
    fun refreshMedia(messageId: Long) {
        if (messageWithMedia(messageId) == null) return
        val last = mediaRefreshedAt[messageId]
        if (last != null && now() - last < MEDIA_REFRESH_GAP_MILLIS) return

        mediaRefreshedAt[messageId] = now()
        viewModelScope.launch { fetchMessage(messageId) }
    }

    private fun messageWithMedia(messageId: Long): ChatMessage? =
        _state.value.timeline.messages.firstOrNull { it.id == messageId && !it.deleted && it.media != null }

    /** Tidak ada rute untuk satu pesan, tetapi `before` id sesudahnya dengan batas 1 mengembalikan pesan itu. */
    private suspend fun fetchMessage(messageId: Long): ChatMessage? {
        val result = repository.olderMessages(conversationId, messageId + 1, limit = 1)
        val message = (result as? ApiResult.Success)?.data?.messages?.firstOrNull { it.id == messageId } ?: return null
        // Kursor polling sengaja tidak digeser, karena jawaban satu pesan bisa melompati pesan yang belum diambil
        _state.update { it.copy(timeline = it.timeline.merge(listOf(message))) }
        return message
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

    fun mediaOpened() {
        _state.update { it.copy(openMedia = null) }
    }

    // Unggahan sudah batal bersama viewModelScope, tinggal berkas sementaranya
    override fun onCleared() {
        _state.value.attachment?.release()
        _state.value.local?.attachment?.release()
    }

    class Factory(
        private val repository: ChatRepository,
        private val info: ChatInfo,
        private val ownUserId: String,
        private val preparer: AttachmentPreparer? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val viewModel = if (preparer == null) {
                ChatViewModel(repository, info, ownUserId)
            } else {
                ChatViewModel(repository, info, ownUserId, preparer = preparer)
            }
            return viewModel as T
        }
    }

    private companion object {
        const val MAX_BODY_LENGTH = 4000

        /** Jeda acak 0 sampai 5 detik di atas `Retry-After` supaya semua aplikasi tidak mencoba di detik yang sama. */
        const val OUTAGE_JITTER_SECONDS = 6L

        const val DEFAULT_POLL_WAIT_SECONDS = 30L

        /** Pengaman: satu tick mengambil paling banyak 5 halaman pesan baru, sisanya diambil tick berikutnya. */
        const val MAX_POLL_PAGES = 5

        /** Tick ke berapa polling juga memuat ulang pesan terbaru dan metadata. Enam tick berarti sekitar 30 detik. */
        const val SLOW_EVERY = 6

        const val IN_PROGRESS_WAIT_MILLIS = 2_000L

        const val PROGRESS_STEP = 5
        const val MEDIA_REFRESH_GAP_MILLIS = 60_000L

        const val STORAGE_UNAVAILABLE = "STORAGE_UNAVAILABLE"
        const val VALIDATION_ERROR = "VALIDATION_ERROR"
        const val CONVERSATION_NOT_FOUND = "CHAT_CONVERSATION_NOT_FOUND"
        const val MESSAGE_NOT_FOUND = "CHAT_MESSAGE_NOT_FOUND"
        const val SENDING_CLOSED = "CHAT_SENDING_CLOSED"
        const val IN_PROGRESS = "IDEMPOTENCY_IN_PROGRESS"
        const val KEY_REUSED = "IDEMPOTENCY_KEY_REUSED"
    }
}
