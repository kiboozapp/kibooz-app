package com.nthb.kibooz.ui.chat

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nthb.kibooz.R
import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.ChatContact
import com.nthb.kibooz.data.remote.ChatConversation
import com.nthb.kibooz.data.remote.ServiceOutage
import com.nthb.kibooz.data.remote.errorMessageRes
import com.nthb.kibooz.data.remote.outage
import com.nthb.kibooz.data.remote.outageKind
import com.nthb.kibooz.data.repository.ChatRepository
import com.nthb.kibooz.data.repository.ClassRepository
import com.nthb.kibooz.data.session.Role
import kotlin.random.Random
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ChatListTab { CONVERSATIONS, NEW }

/** Kelas yang ruangnya bisa dibuka oleh akun ini. */
data class ChatClassOption(val id: String, val name: String)

/** Isi tab "Mulai baru": kontak dan ruang kelas. */
data class ChatStarters(val contacts: List<ChatContact>, val classes: List<ChatClassOption>)

/** Satu bagian layar dengan keadaan muat, data, galat dan gangguannya sendiri. */
data class ChatSection<T>(
    val loading: Boolean = true,
    val data: T? = null,
    @StringRes val error: Int? = null,
    /** Server tidak terjangkau. Layar menampilkan hitung mundur lalu memanggil [ChatListViewModel.retryAfterOutage]. */
    val outage: ServiceOutage? = null
)

data class ChatListState(
    val tab: ChatListTab = ChatListTab.CONVERSATIONS,
    val conversations: ChatSection<List<ChatConversation>> = ChatSection(),
    /** Null selama tab "Mulai baru" belum pernah dibuka. */
    val starters: ChatSection<ChatStarters>? = null,
    /** Kunci kontak atau kelas yang sedang dibuka, supaya satu ketukan tidak membuka dua kali. */
    val opening: String? = null,
    @StringRes val notice: Int? = null,
    /** Percakapan yang siap dibuka. Layar membukanya sekali lalu memanggil [ChatListViewModel.openedHandled]. */
    val opened: ChatConversation? = null
)

/** Jeda muat ulang di belakang layar setelah 429 tanpa `Retry-After`. */
private const val QUIET_WAIT_SECONDS = 30L

/** Kunci "sedang dibuka" untuk satu kontak. */
fun openingKey(contact: ChatContact): String = "u:${contact.id}"

/** Kunci "sedang dibuka" untuk satu ruang kelas. */
fun openingKey(option: ChatClassOption): String = "c:${option.id}"

/**
 * Daftar percakapan dan pemilih untuk memulai yang baru. Tab "Mulai baru" baru dimuat saat dibuka pertama kali.
 * Guru mendapat kelas aktifnya dari daftar kelas, Wali dari kelas anak-anaknya, dan kelas yang diarsipkan tidak
 * ditawarkan karena ruangnya tidak bisa dibuat setelah diarsipkan. Data lama tetap tampil jika muat ulang gagal.
 */
class ChatListViewModel(
    private val repository: ChatRepository,
    private val classes: ClassRepository,
    private val role: Role?,
    private val jitter: () -> Long = { Random.nextLong(OUTAGE_JITTER_SECONDS) },
    /** Jam milidetik monoton, untuk menghitung jeda `Retry-After`. */
    private val now: () -> Long = { System.nanoTime() / 1_000_000L }
) : ViewModel() {

    private val _state = MutableStateFlow(ChatListState())
    val state: StateFlow<ChatListState> = _state.asStateFlow()

    private var conversationsJob: Job? = null
    private var startersJob: Job? = null
    private var outageAttempts = 0
    private var quietFailed = false
    private var quietBlockedUntil: Long? = null

    init {
        loadConversations()
    }

    /** Memuat ulang tab yang sedang terbuka. */
    fun refresh() {
        outageAttempts = 0
        when (_state.value.tab) {
            ChatListTab.CONVERSATIONS -> loadConversations()
            ChatListTab.NEW -> loadStarters()
        }
    }

    /** Percobaan otomatis setelah gangguan. Jeda berikutnya lebih panjang selama gangguan berlanjut. */
    fun retryAfterOutage() {
        when (_state.value.tab) {
            ChatListTab.CONVERSATIONS -> loadConversations()
            ChatListTab.NEW -> loadStarters()
        }
    }

    /** Layar tampil lagi. Memuat ulang di belakang layar supaya pesan baru dan jumlah belum dibaca segar. */
    fun refreshQuietly() {
        val snapshot = _state.value
        if (snapshot.conversations.data == null || snapshot.conversations.loading) return
        // Muat yang sedang jalan tidak dibatalkan, supaya frame yang beruntun tidak membuatnya tidak pernah selesai
        if (conversationsJob?.isActive == true || quietBlocked()) return

        startQuietRefresh()
    }

    /**
     * Satu tick dari [ChatTicker]. Menunggu sampai muat ulang selesai, supaya tick yang menumpuk digabung. Mengembalikan
     * false jika perlu diulang, yaitu muat lain masih jalan, masih menunggu `Retry-After`, atau muat ulang gagal.
     */
    suspend fun refreshOnTick(): Boolean {
        val running = conversationsJob?.takeIf { it.isActive }
        if (running != null) {
            // Muat itu mungkin dimulai sebelum frame ini, jadi diminta lagi
            running.join()
            return false
        }
        if (quietBlocked()) return false
        val section = _state.value.conversations
        if (section.data == null) return !section.loading

        startQuietRefresh().join()
        return !quietFailed
    }

    private fun quietBlocked(): Boolean = quietBlockedUntil?.let { now() - it < 0 } ?: false

    private fun startQuietRefresh(): Job {
        val job = viewModelScope.launch {
            val result = repository.allConversations()
            quietFailed = result !is ApiResult.Success
            if (result is ApiResult.Success) {
                _state.update { it.copy(conversations = ChatSection(loading = false, data = result.data)) }
            } else if (result is ApiResult.Failure && result.status == 429) {
                quietBlockedUntil = now() + (result.retryAfterSeconds ?: QUIET_WAIT_SECONDS) * 1_000L
            }
        }
        conversationsJob = job
        return job
    }

    fun selectTab(tab: ChatListTab) {
        if (tab == _state.value.tab) return

        _state.update { it.copy(tab = tab) }
        if (tab == ChatListTab.NEW && _state.value.starters == null) {
            outageAttempts = 0
            loadStarters()
        }
    }

    private fun loadConversations() {
        conversationsJob?.cancel()
        _state.update { it.copy(conversations = it.conversations.copy(loading = true, error = null, outage = null)) }
        conversationsJob = viewModelScope.launch {
            when (val result = repository.allConversations()) {
                is ApiResult.Success -> {
                    outageAttempts = 0
                    _state.update { it.copy(conversations = ChatSection(loading = false, data = result.data)) }
                }
                else -> {
                    val outage = outageOf(result)
                    _state.update {
                        it.copy(
                            conversations = it.conversations.copy(
                                loading = false,
                                error = result.errorMessageRes(),
                                outage = outage
                            )
                        )
                    }
                }
            }
        }
    }

    private fun loadStarters() {
        startersJob?.cancel()
        _state.update {
            it.copy(starters = (it.starters ?: ChatSection()).copy(loading = true, error = null, outage = null))
        }
        startersJob = viewModelScope.launch {
            val contacts = repository.allContacts()
            if (contacts !is ApiResult.Success) {
                failStarters(contacts)
                return@launch
            }

            when (val options = classOptions()) {
                is ApiResult.Success -> {
                    outageAttempts = 0
                    _state.update {
                        it.copy(starters = ChatSection(loading = false, data = ChatStarters(contacts.data, options.data)))
                    }
                }
                else -> failStarters(options)
            }
        }
    }

    private fun failStarters(result: ApiResult<*>) {
        val outage = outageOf(result)
        _state.update {
            it.copy(
                starters = (it.starters ?: ChatSection()).copy(
                    loading = false,
                    error = result.errorMessageRes(),
                    outage = outage
                )
            )
        }
    }

    private suspend fun classOptions(): ApiResult<List<ChatClassOption>> = when (role) {
        Role.GURU -> when (val result = classes.teacherClasses()) {
            is ApiResult.Success -> ApiResult.Success(
                result.data.filterNot { it.archived }.map { ChatClassOption(it.id, it.name) }
            )
            is ApiResult.Failure -> result
            is ApiResult.NetworkError -> result
        }
        Role.WALI -> when (val result = classes.children()) {
            is ApiResult.Success -> ApiResult.Success(
                result.data
                    .filter { it.classArchivedAt == null }
                    .distinctBy { it.classId }
                    .map { ChatClassOption(it.classId, it.className) }
            )
            is ApiResult.Failure -> result
            is ApiResult.NetworkError -> result
        }
        else -> ApiResult.Success(emptyList())
    }

    private fun outageOf(result: ApiResult<*>): ServiceOutage? =
        if (result.outageKind() != null) result.outage(++outageAttempts, jitter()) else null

    // Membuka percakapan

    /** Membuka percakapan yang sudah ada di daftar tanpa memanggil server. */
    fun openConversation(conversation: ChatConversation) {
        _state.update { it.copy(opened = conversation) }
    }

    /** Membuka percakapan langsung dengan [contact]. Server membuatnya jika belum ada. */
    fun openContact(contact: ChatContact) = open(openingKey(contact)) { repository.startDirect(contact.id) }

    /** Membuka ruang kelas. Server membuatnya pada panggilan pertama. */
    fun openClass(option: ChatClassOption) = open(openingKey(option)) { repository.openClassRoom(option.id) }

    private fun open(key: String, call: suspend () -> ApiResult<ChatConversation>) {
        if (_state.value.opening != null) return

        _state.update { it.copy(opening = key, notice = null) }
        viewModelScope.launch {
            when (val result = call()) {
                is ApiResult.Success -> _state.update { it.copy(opening = null, opened = result.data) }
                else -> {
                    _state.update { it.copy(opening = null, notice = result.errorMessageRes() ?: R.string.error_unknown) }
                    // Orang atau kelasnya sudah berubah di server, jadi daftar pilihan di layar basi
                    if (result is ApiResult.Failure && result.errorCode == CONTACT_NOT_ALLOWED) loadStarters()
                }
            }
        }
    }

    fun openedHandled() {
        _state.update { it.copy(opened = null) }
    }

    fun noticeShown() {
        _state.update { it.copy(notice = null) }
    }

    class Factory(
        private val repository: ChatRepository,
        private val classes: ClassRepository,
        private val role: Role?
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ChatListViewModel(repository, classes, role) as T
    }

    private companion object {
        /** Jeda acak 0 sampai 5 detik di atas `Retry-After` supaya semua aplikasi tidak mencoba di detik yang sama. */
        const val OUTAGE_JITTER_SECONDS = 6L

        const val CONTACT_NOT_ALLOWED = "CHAT_CONTACT_NOT_ALLOWED"
    }
}
