package com.nthb.kibooz.ui.guru

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nthb.kibooz.R
import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.ClassSummary
import com.nthb.kibooz.data.remote.CreateClassRequest
import com.nthb.kibooz.data.remote.ServiceOutage
import com.nthb.kibooz.data.remote.errorMessageRes
import com.nthb.kibooz.data.remote.outage
import com.nthb.kibooz.data.remote.outageKind
import com.nthb.kibooz.data.repository.ClassManagementRepository
import com.nthb.kibooz.data.repository.PendingClass
import com.nthb.kibooz.util.FormRules
import kotlin.random.Random
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Pesan singkat hasil sebuah aksi. [args] mengisi format string-nya. */
data class KelolaNotice(@StringRes val res: Int, val args: List<String> = emptyList())

data class KelolaKelasState(
    val loading: Boolean = true,
    /** Semua kelas Guru dari server, null selama daftar pertama belum berhasil dimuat. */
    val classes: List<ClassSummary>? = null,
    val query: String = "",
    /** Galat muat daftar. Dipakai layar penuh saat [classes] masih null, dan tidak dipakai saat data lama tetap tampil. */
    @StringRes val loadError: Int? = null,
    /** Terisi jika server tidak terjangkau (FE-12). Layar menampilkan hitung mundur lalu memanggil [KelolaKelasViewModel.retryAfterOutage]. */
    val outage: ServiceOutage? = null,
    /** Kelas yang sedang menjalani sebuah aksi. Tombolnya dimatikan sampai server menjawab. */
    val busyClassId: String? = null,
    val creating: Boolean = false,
    @StringRes val createError: Int? = null,
    /** True sekali setelah kelas berhasil dibuat, supaya layar menutup dialog. Reset dengan [KelolaKelasViewModel.createHandled]. */
    val createDone: Boolean = false,
    val notice: KelolaNotice? = null
) {
    private val matching: List<ClassSummary> get() = classes.orEmpty().filter { it.matches(query) }

    /** Kelas aktif yang cocok dengan [query]. */
    val active: List<ClassSummary> get() = matching.filterNot { it.archived }

    /** Kelas yang diarsipkan dan cocok dengan [query]. Hanya bisa dibaca atau dipulihkan. */
    val archived: List<ClassSummary> get() = matching.filter { it.archived }

    /** Jumlah semua kelas aktif, tanpa melihat pencarian. */
    val activeTotal: Int get() = classes.orEmpty().count { !it.archived }

    val archivedTotal: Int get() = classes.orEmpty().count { it.archived }

    /** True jika Guru belum punya kelas sama sekali. */
    val empty: Boolean get() = classes != null && classes.isEmpty()

    /** True jika ada kelas tetapi pencarian tidak cocok dengan satupun. */
    val noMatch: Boolean get() = classes != null && classes.isNotEmpty() && matching.isEmpty()
}

private fun ClassSummary.matches(query: String): Boolean {
    val q = query.trim()
    return q.isEmpty() ||
        name.contains(q, ignoreCase = true) ||
        gradeLevel.contains(q, ignoreCase = true) ||
        joinCode.contains(q, ignoreCase = true)
}

/**
 * Kelola kelas Guru: daftar, buat, kode gabung baru, buka atau tutup pendaftaran, arsip dan pulihkan. Setiap aksi
 * memperbarui kelasnya dari jawaban server lalu memuat ulang daftar tanpa layar muat, supaya jumlah anak dan
 * status lain ikut segar. Data lama tetap tampil jika muat ulang gagal.
 */
class KelolaKelasViewModel(private val repository: ClassManagementRepository) : ViewModel() {

    private val _state = MutableStateFlow(KelolaKelasState())
    val state: StateFlow<KelolaKelasState> = _state.asStateFlow()

    private var listJob: Job? = null

    /** Gangguan berturut-turut sejak muat terakhir yang dimulai pengguna. Menentukan seberapa panjang jeda berikutnya. */
    private var outageAttempts = 0

    /** Buat kelas yang belum berhasil. Dikirim ulang dengan kunci yang sama selama isiannya sama. */
    private var pendingClass: PendingClass? = null

    init {
        load()
    }

    /** Memuat ulang dari awal, misalnya saat layar tampil lagi atau pengguna menekan coba lagi. */
    fun load() {
        outageAttempts = 0
        fetch()
    }

    /** Percobaan otomatis setelah gangguan. Jeda berikutnya lebih panjang selama gangguan berlanjut. */
    fun retryAfterOutage() = fetch()

    private fun fetch() {
        listJob?.cancel()
        _state.update { it.copy(loading = true, loadError = null, outage = null) }
        listJob = viewModelScope.launch {
            when (val result = repository.list()) {
                is ApiResult.Success -> {
                    outageAttempts = 0
                    _state.update { it.copy(loading = false, classes = result.data) }
                }
                else -> {
                    val outage = if (result.outageKind() != null) {
                        result.outage(++outageAttempts, Random.nextLong(OUTAGE_JITTER_SECONDS))
                    } else {
                        null
                    }
                    _state.update { it.copy(loading = false, loadError = result.errorMessageRes(), outage = outage) }
                }
            }
        }
    }

    /** Memuat ulang di belakang layar setelah sebuah aksi. Gagal tidak mengubah apa pun, data lama tetap tampil. */
    private suspend fun refreshQuietly() {
        listJob?.cancel()
        val result = repository.list()
        if (result is ApiResult.Success) _state.update { it.copy(loading = false, classes = result.data, loadError = null) }
    }

    fun setQuery(query: String) {
        _state.update { it.copy(query = query) }
    }

    fun rotateJoinCode(classId: String) = act(
        classId,
        call = { repository.rotateJoinCode(classId) },
        apply = { current, code -> Applied(current.copy(joinCode = code), KelolaNotice(R.string.kelola_hasil_kode_baru, listOf(current.name, code))) }
    )

    fun setJoining(classId: String, open: Boolean) = act(
        classId,
        call = { repository.setJoining(classId, open) },
        apply = { current, joinOpen ->
            val res = if (joinOpen) R.string.kelola_hasil_dibuka else R.string.kelola_hasil_ditutup
            Applied(current.copy(joinOpen = joinOpen), KelolaNotice(res, listOf(current.name)))
        }
    )

    fun archive(classId: String) = act(
        classId,
        call = { repository.archive(classId) },
        apply = { current, updated -> Applied(updated, KelolaNotice(R.string.kelola_hasil_diarsipkan, listOf(current.name))) }
    )

    /** Memulihkan kelas. Jawaban server membawa kode gabung baru karena kode lama bisa sudah tersebar. */
    fun restore(classId: String) = act(
        classId,
        call = { repository.restore(classId) },
        limitMessage = R.string.kelola_batas_pulihkan,
        apply = { current, updated ->
            Applied(updated, KelolaNotice(R.string.kelola_hasil_dipulihkan, listOf(current.name, updated.joinCode)))
        }
    )

    private class Applied(val updated: ClassSummary, val notice: KelolaNotice)

    private fun <T> act(
        classId: String,
        call: suspend () -> ApiResult<T>,
        apply: (current: ClassSummary, data: T) -> Applied,
        @StringRes limitMessage: Int? = null
    ) {
        val snapshot = _state.value
        if (snapshot.busyClassId != null) return
        val current = snapshot.classes?.firstOrNull { it.id == classId } ?: return

        _state.update { it.copy(busyClassId = classId) }
        viewModelScope.launch {
            when (val result = call()) {
                is ApiResult.Success -> {
                    val applied = apply(current, result.data)
                    _state.update { state ->
                        state.copy(
                            busyClassId = null,
                            classes = state.classes?.map { if (it.id == classId) applied.updated else it },
                            notice = applied.notice
                        )
                    }
                    refreshQuietly()
                }
                else -> {
                    _state.update { it.copy(busyClassId = null, notice = KelolaNotice(failureMessage(result, limitMessage))) }
                    // Kelas sudah lain di server (hilang, diarsipkan atau sudah dipulihkan), jadi daftar di layar basi
                    if (result is ApiResult.Failure && result.errorCode in STALE_CLASS_CODES) refreshQuietly()
                }
            }
        }
    }

    /**
     * Buat kelas dari isian dialog. Isian kosong untuk tingkat dan tahun ajaran tidak dikirim, jadi server memakai
     * nilai bawaannya. Kunci idempotensi sama selama isiannya sama, jadi coba lagi setelah jaringan putus tidak
     * membuat dua kelas.
     */
    fun createClass(name: String, gradeLevel: String, academicYear: String) {
        val snapshot = _state.value
        if (snapshot.creating || snapshot.classes == null) return

        val request = buildRequest(name, gradeLevel, academicYear)
        if (request is Built.Invalid) {
            _state.update { it.copy(createError = request.error) }
            return
        }
        request as Built.Valid

        val pending = pendingClass?.takeIf { it.request == request.value } ?: PendingClass(request.value).also { pendingClass = it }

        _state.update { it.copy(creating = true, createError = null) }
        viewModelScope.launch {
            when (val result = repository.create(pending)) {
                is ApiResult.Success -> {
                    pendingClass = null
                    _state.update { state ->
                        state.copy(
                            creating = false,
                            createDone = true,
                            classes = state.classes?.plus(result.data),
                            notice = KelolaNotice(R.string.kelola_hasil_dibuat, listOf(result.data.name))
                        )
                    }
                    refreshQuietly()
                }
                else -> {
                    // Kunci yang sama dengan isi lain ditolak, jadi percobaan berikutnya memakai kunci baru
                    if (result is ApiResult.Failure && result.errorCode == "IDEMPOTENCY_KEY_REUSED") pendingClass = null
                    _state.update { it.copy(creating = false, createError = failureMessage(result, R.string.kelola_batas_buat)) }
                }
            }
        }
    }

    private sealed interface Built {
        class Valid(val value: CreateClassRequest) : Built
        class Invalid(@StringRes val error: Int) : Built
    }

    private fun buildRequest(name: String, gradeLevel: String, academicYear: String): Built {
        val trimmedName = name.trim()
        val grade = gradeLevel.trim().ifEmpty { null }
        val year = academicYear.trim().ifEmpty { null }
        return when {
            trimmedName.isEmpty() -> Built.Invalid(R.string.kelola_nama_wajib)
            trimmedName.length > FormRules.CLASS_NAME_MAX -> Built.Invalid(R.string.kelola_nama_panjang)
            grade != null && grade.length > GRADE_LEVEL_MAX -> Built.Invalid(R.string.kelola_tingkat_panjang)
            year != null && year.length > ACADEMIC_YEAR_MAX -> Built.Invalid(R.string.kelola_tahun_panjang)
            else -> Built.Valid(CreateClassRequest(name = trimmedName, gradeLevel = grade, academicYear = year))
        }
    }

    @StringRes
    private fun failureMessage(result: ApiResult<*>, @StringRes limitMessage: Int?): Int {
        val limitReached = result is ApiResult.Failure && result.errorCode == "CLASS_LIMIT_REACHED"
        if (limitReached && limitMessage != null) return limitMessage
        return result.errorMessageRes() ?: R.string.error_unknown
    }

    fun clearCreateError() {
        _state.update { it.copy(createError = null) }
    }

    fun createHandled() {
        _state.update { it.copy(createDone = false) }
    }

    fun noticeShown() {
        _state.update { it.copy(notice = null) }
    }

    class Factory(private val repository: ClassManagementRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = KelolaKelasViewModel(repository) as T
    }

    private companion object {
        const val GRADE_LEVEL_MAX = 20
        const val ACADEMIC_YEAR_MAX = 20

        /** Jeda acak 0 sampai 5 detik di atas `Retry-After` supaya semua aplikasi tidak mencoba di detik yang sama. */
        const val OUTAGE_JITTER_SECONDS = 6L

        val STALE_CLASS_CODES = setOf("CLASS_NOT_FOUND", "CLASS_ARCHIVED", "CLASS_NOT_ARCHIVED")
    }
}
