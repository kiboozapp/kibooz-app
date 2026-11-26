package com.nthb.kibooz.ui.guru

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nthb.kibooz.R
import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.ClassTeacher
import com.nthb.kibooz.data.remote.ServiceOutage
import com.nthb.kibooz.data.remote.errorMessageRes
import com.nthb.kibooz.data.remote.outage
import com.nthb.kibooz.data.remote.outageKind
import com.nthb.kibooz.data.remote.retryWhenRateLimited
import com.nthb.kibooz.data.repository.ClassMemberRepository
import com.nthb.kibooz.data.repository.PendingTeacher
import com.nthb.kibooz.util.FormRules
import kotlin.random.Random
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Aksi yang boleh dilakukan akun yang masuk pada satu baris guru. */
enum class TeacherAction { MAKE_OWNER, REMOVE, LEAVE }

data class PengajarKelasState(
    val loading: Boolean = true,
    /** Guru di kelas, yang paling lama bergabung lebih dulu. Null selama daftar pertama belum berhasil dimuat. */
    val teachers: List<ClassTeacher>? = null,
    @StringRes val loadError: Int? = null,
    /** Terisi jika server tidak terjangkau. Layar menampilkan hitung mundur lalu memanggil [PengajarKelasViewModel.retryAfterOutage]. */
    val outage: ServiceOutage? = null,
    /** Kelas yang diarsipkan hanya bisa dibaca, jadi tidak ada aksi yang ditampilkan. */
    val archived: Boolean = false,
    /** Baris guru yang sedang menjalani aksi. */
    val busyTeacherId: String? = null,
    /** Isi kolom email di dialog tambah guru. Null berarti dialognya tertutup. */
    val addEmail: String? = null,
    val adding: Boolean = false,
    @StringRes val addError: Int? = null,
    /** Sisa detik sebelum penambahan dikirim ulang otomatis setelah 429. */
    val addWaitSeconds: Int? = null,
    /** Sisa detik aksi dikunci setelah 429. */
    val cooldownSeconds: Int? = null,
    val notice: KelolaNotice? = null,
    /** Terisi jika layar kelas ini harus ditutup. */
    val end: ClassScreenEnd? = null
) {
    /** Baris akun yang sedang masuk. */
    val self: ClassTeacher? get() = teachers?.firstOrNull { it.isSelf }

    /** Guru yang menjadi pemilik jika pemilik yang sedang masuk keluar, yaitu yang bergabung paling lama berikutnya. */
    val successor: ClassTeacher? get() = if (self?.isOwner == true) teachers?.firstOrNull { !it.isSelf } else null

    val busy: Boolean get() = busyTeacherId != null || adding

    /** True selama tombol aksi tidak boleh ditekan. */
    val locked: Boolean get() = busy || cooldownSeconds != null || end != null

    /** Setiap guru di kelas boleh menambahkan guru lain, selama kelasnya tidak diarsipkan. */
    val canAdd: Boolean get() = teachers != null && !archived && end == null

    /**
     * Aksi yang akan diizinkan server. Guru bisa keluar sendiri kecuali ia satu-satunya guru. Hanya pemilik yang
     * bisa mengeluarkan guru lain atau memindahkan kepemilikan.
     */
    fun actionsFor(teacher: ClassTeacher): List<TeacherAction> {
        val all = teachers ?: return emptyList()
        return when {
            archived -> emptyList()
            teacher.isSelf -> if (all.size > 1) listOf(TeacherAction.LEAVE) else emptyList()
            self?.isOwner == true -> listOf(TeacherAction.MAKE_OWNER, TeacherAction.REMOVE)
            else -> emptyList()
        }
    }
}

/**
 * Guru sebuah kelas: daftar, tambah lewat email, keluarkan, keluar dari kelas, dan pindahkan pemilik. Setiap aksi
 * memperbarui daftar dari jawaban server lalu memuat ulang. Aksi yang hasilnya tidak diketahui (jaringan putus
 * atau server bermasalah) juga diikuti muat ulang, supaya layar menampilkan keadaan yang sebenarnya.
 */
class PengajarKelasViewModel(
    private val repository: ClassMemberRepository,
    private val classId: String,
    archived: Boolean = false,
    private val signOut: () -> Unit,
    private val pause: suspend (millis: Long) -> Unit = { delay(it) }
) : ViewModel() {

    private val _state = MutableStateFlow(PengajarKelasState(archived = archived))
    val state: StateFlow<PengajarKelasState> = _state.asStateFlow()

    private var listJob: Job? = null
    private var addJob: Job? = null
    private var outageAttempts = 0

    /** Penambahan yang belum berhasil. Dikirim ulang dengan kunci yang sama selama emailnya sama. */
    private var pendingTeacher: PendingTeacher? = null

    private val cooldown = RateLimitCooldown(viewModelScope, pause) { left ->
        _state.update { it.copy(cooldownSeconds = left) }
    }

    init {
        load()
    }

    /** Memuat ulang atas permintaan pengguna. Diabaikan selama jeda 429 supaya waktu tunggunya tidak bertambah. */
    fun load() {
        if (_state.value.cooldownSeconds != null) return
        outageAttempts = 0
        fetch()
    }

    /** Percobaan otomatis setelah gangguan. Jeda berikutnya lebih panjang selama gangguan berlanjut. */
    fun retryAfterOutage() = fetch()

    /** Dipanggil saat layar tampil lagi, karena guru lain bisa sudah mengubah daftarnya. */
    fun reloadIfIdle() {
        val snapshot = _state.value
        if (!snapshot.loading && snapshot.teachers != null) load()
    }

    private fun fetch() {
        val snapshot = _state.value
        // Aksi yang berjalan memuat ulang daftarnya sendiri
        if (snapshot.end != null || snapshot.busy) return
        if (classId.isBlank()) {
            _state.update { it.copy(loading = false, end = ClassScreenEnd.LOST) }
            return
        }
        listJob?.cancel()
        _state.update { it.copy(loading = true, loadError = null, outage = null) }
        listJob = viewModelScope.launch { loadTeachers() }
    }

    private suspend fun loadTeachers() {
        when (val result = repository.teachers(classId)) {
            is ApiResult.Success -> {
                outageAttempts = 0
                _state.update { it.copy(loading = false, teachers = result.data) }
            }
            is ApiResult.Failure -> {
                if (closeIfEnded(result)) return
                if (result.isRateLimited()) cooldown.start(result.cooldownSeconds())
                showLoadFailure(result)
            }
            is ApiResult.NetworkError -> showLoadFailure(result)
        }
    }

    private fun showLoadFailure(result: ApiResult<*>) {
        val outage = if (result.outageKind() != null) {
            result.outage(++outageAttempts, Random.nextLong(OUTAGE_JITTER_SECONDS))
        } else {
            null
        }
        _state.update {
            it.copy(loading = false, loadError = result.errorMessageRes() ?: R.string.error_unknown, outage = outage)
        }
    }

    /** Muat ulang di belakang layar setelah sebuah aksi. Gagal tidak menghapus daftar yang tampil. */
    private suspend fun refreshQuietly() {
        when (val result = repository.teachers(classId)) {
            is ApiResult.Success -> _state.update { it.copy(teachers = result.data, loadError = null) }
            is ApiResult.Failure ->
                if (!closeIfEnded(result) && result.isRateLimited()) cooldown.start(result.cooldownSeconds())
            is ApiResult.NetworkError -> Unit
        }
    }

    private fun closeIfEnded(failure: ApiResult.Failure): Boolean {
        val end = classScreenEndFor(failure.errorCode) ?: return false
        if (end == ClassScreenEnd.ACCOUNT_GONE) signOut()
        close(end)
        return true
    }

    private fun close(end: ClassScreenEnd) {
        _state.update {
            it.copy(loading = false, addEmail = null, addError = null, addWaitSeconds = null, end = end)
        }
    }

    /** Mengeluarkan guru lain, atau keluar dari kelas jika barisnya milik akun yang masuk. */
    fun remove(teacherId: String) {
        val snapshot = _state.value
        if (snapshot.locked) return
        val target = snapshot.teachers?.firstOrNull { it.id == teacherId } ?: return
        val actions = snapshot.actionsFor(target)
        val leaving = TeacherAction.LEAVE in actions
        if (!leaving && TeacherAction.REMOVE !in actions) return

        act(teacherId) {
            when (val result = repository.removeTeacher(classId, teacherId)) {
                is ApiResult.Success -> {
                    // Kunci lama bisa memutar ulang jawaban "sudah ditambahkan" untuk guru yang baru dikeluarkan
                    pendingTeacher = null
                    if (leaving) {
                        close(ClassScreenEnd.LEFT)
                    } else {
                        _state.update { state ->
                            state.copy(
                                teachers = state.teachers?.filterNot { it.id == teacherId },
                                notice = KelolaNotice(R.string.pengajar_hasil_dikeluarkan, listOf(target.fullName))
                            )
                        }
                        refreshQuietly()
                    }
                }
                else -> onActionFailure(result)
            }
        }
    }

    fun makeOwner(teacherId: String) {
        val snapshot = _state.value
        if (snapshot.locked) return
        val target = snapshot.teachers?.firstOrNull { it.id == teacherId } ?: return
        if (TeacherAction.MAKE_OWNER !in snapshot.actionsFor(target)) return

        act(teacherId) {
            when (val result = repository.transferOwnership(classId, teacherId)) {
                is ApiResult.Success -> {
                    val ownerId = result.data.id
                    _state.update { state ->
                        state.copy(
                            teachers = state.teachers?.map { it.copy(isOwner = it.id == ownerId) },
                            notice = KelolaNotice(R.string.pengajar_hasil_pemilik, listOf(target.fullName))
                        )
                    }
                    refreshQuietly()
                }
                else -> onActionFailure(result)
            }
        }
    }

    private fun act(teacherId: String, block: suspend () -> Unit) {
        listJob?.cancel()
        _state.update { it.copy(busyTeacherId = teacherId, loading = false, loadError = null, outage = null) }
        viewModelScope.launch {
            block()
            _state.update { it.copy(busyTeacherId = null) }
        }
    }

    private suspend fun onActionFailure(result: ApiResult<*>) {
        if (result is ApiResult.Failure) {
            if (closeIfEnded(result)) return
            if (result.isRateLimited()) {
                val seconds = result.cooldownSeconds()
                cooldown.start(seconds)
                _state.update { it.copy(notice = rateLimitNotice(seconds)) }
                return
            }
            if (result.errorCode == "CLASS_ARCHIVED") _state.update { it.copy(archived = true) }
        }
        _state.update { it.copy(notice = KelolaNotice(result.errorMessageRes() ?: R.string.error_unknown)) }
        refreshQuietly()
    }

    fun openAddDialog() {
        val snapshot = _state.value
        if (!snapshot.canAdd || snapshot.busy || snapshot.addEmail != null) return
        _state.update { it.copy(addEmail = "", addError = null) }
    }

    fun setAddEmail(value: String) {
        val snapshot = _state.value
        if (snapshot.addEmail == null || snapshot.addEmail == value || snapshot.adding) return
        _state.update { it.copy(addEmail = value, addError = null) }
    }

    /**
     * Menutup dialog tambah guru. Permintaan yang sedang dikirim tidak bisa dibatalkan. Saat menunggu 429 belum ada
     * yang berjalan di server, jadi menunggunya boleh dibatalkan dan sisa waktunya tetap mengunci aksi.
     */
    fun closeAddDialog() {
        val snapshot = _state.value
        if (snapshot.addEmail == null) return
        if (snapshot.adding) {
            val wait = snapshot.addWaitSeconds ?: return
            addJob?.cancel()
            cooldown.start(wait.toLong())
        }
        _state.update { it.copy(addEmail = null, addError = null, adding = false, addWaitSeconds = null) }
    }

    fun submitAdd() {
        val snapshot = _state.value
        val typed = snapshot.addEmail ?: return
        if (snapshot.locked || !snapshot.canAdd) return

        val email = typed.trim()
        val invalid = FormRules.email(email)
        if (invalid != null) {
            _state.update { it.copy(addError = invalid) }
            return
        }
        val pending = pendingTeacher?.takeIf { it.email == email } ?: PendingTeacher(email).also { pendingTeacher = it }

        listJob?.cancel()
        _state.update { it.copy(adding = true, addError = null, loading = false, loadError = null, outage = null) }
        addJob = viewModelScope.launch {
            val result = retryWhenRateLimited(
                onWait = { left -> _state.update { it.copy(addWaitSeconds = left.takeIf { seconds -> seconds > 0 }) } },
                pause = pause
            ) { repository.addTeacher(classId, pending) }

            when (result) {
                is ApiResult.Success -> onAdded(result.data)
                else -> onAddFailure(result)
            }
            _state.update { it.copy(adding = false, addWaitSeconds = null) }
        }
    }

    private suspend fun onAdded(teacher: ClassTeacher) {
        pendingTeacher = null
        _state.update { state ->
            val others = state.teachers.orEmpty().filterNot { it.id == teacher.id }
            state.copy(
                teachers = others + teacher,
                addEmail = null,
                notice = KelolaNotice(R.string.pengajar_hasil_ditambahkan, listOf(teacher.fullName))
            )
        }
        refreshQuietly()
    }

    private suspend fun onAddFailure(result: ApiResult<*>) {
        val failure = result as? ApiResult.Failure
        if (failure != null) {
            if (closeIfEnded(failure)) return
            if (failure.isRateLimited()) {
                cooldown.start(failure.cooldownSeconds())
                _state.update { it.copy(addError = R.string.error_rate_limited) }
                return
            }
            when (failure.errorCode) {
                // Dialognya tidak berguna lagi, jadi ditutup dan penjelasannya tampil di layar
                "CLASS_ARCHIVED", "AUTH_FORBIDDEN" -> {
                    _state.update {
                        it.copy(
                            archived = it.archived || failure.errorCode == "CLASS_ARCHIVED",
                            addEmail = null,
                            notice = KelolaNotice(failure.errorMessageRes() ?: R.string.error_unknown)
                        )
                    }
                    refreshQuietly()
                    return
                }
                // Kunci yang sama dengan isi lain ditolak, jadi percobaan berikutnya memakai kunci baru
                "IDEMPOTENCY_KEY_REUSED" -> pendingTeacher = null
            }
        }
        _state.update { it.copy(addError = addErrorFor(result)) }

        // Guru yang ternyata sudah ada, dan hasil yang tidak diketahui, berarti daftar di layar bisa basi
        val stale = failure == null || failure.errorCode == null ||
            failure.errorCode in setOf("TEACHER_ALREADY_IN_CLASS", "INTERNAL_ERROR", "SERVER_TIMEOUT")
        if (stale) refreshQuietly()
    }

    @StringRes
    private fun addErrorFor(result: ApiResult<*>): Int {
        val failure = result as? ApiResult.Failure
        return when {
            failure?.errorCode == "TEACHER_NOT_FOUND" -> R.string.pengajar_tidak_ditemukan
            failure?.errorCode == "CLASS_LIMIT_REACHED" -> R.string.pengajar_batas_kelas
            failure?.errorCode == "VALIDATION_ERROR" && failure.details?.containsKey("email") == true ->
                R.string.form_email_tidak_valid
            else -> result.errorMessageRes() ?: R.string.error_unknown
        }
    }

    fun noticeShown() {
        _state.update { it.copy(notice = null) }
    }

    class Factory(
        private val repository: ClassMemberRepository,
        private val classId: String,
        private val archived: Boolean,
        private val signOut: () -> Unit
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PengajarKelasViewModel(repository, classId, archived, signOut) as T
    }

    private companion object {
        /** Jeda acak 0 sampai 5 detik di atas `Retry-After` supaya semua aplikasi tidak mencoba di detik yang sama. */
        const val OUTAGE_JITTER_SECONDS = 6L
    }
}
