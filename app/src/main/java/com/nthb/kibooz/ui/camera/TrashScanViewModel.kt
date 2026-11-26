package com.nthb.kibooz.ui.camera

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nthb.kibooz.R
import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.ChildSummary
import com.nthb.kibooz.data.remote.ScanClaimData
import com.nthb.kibooz.data.remote.errorMessageRes
import com.nthb.kibooz.data.remote.retryWhenRateLimited
import com.nthb.kibooz.data.repository.ClassRepository
import com.nthb.kibooz.data.repository.PendingTrashClaim
import com.nthb.kibooz.data.repository.TrashRepository
import com.nthb.kibooz.ml.TrashCategory
import com.nthb.kibooz.ml.TrashResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Pilihan manual di bawah 75%, urut seperti di dialog. B3 ikut supaya barang berbahaya yang terbaca ragu tidak
 * tercatat sebagai organik atau anorganik. B3 tetap tidak diberi poin oleh server.
 */
val MANUAL_TRASH_CHOICES = listOf(TrashCategory.ORGANIK, TrashCategory.ANORGANIK, TrashCategory.B3)

data class TrashScanState(
    val loadingChildren: Boolean = true,
    /** Anak yang membuang sampah. Null selama belum diketahui atau belum dipilih. */
    val childId: String? = null,
    /** Tidak kosong jika Wali punya beberapa anak dan harus memilih satu. */
    val childChoices: List<ChildSummary> = emptyList(),
    /** Hasil terakhir dari kamera yang sudah stabil, null jika belum ada. */
    val detected: TrashResult? = null,
    /** Keyakinan di bawah 75%: pengguna harus memilih sendiri salah satu dari [MANUAL_TRASH_CHOICES]. */
    val askCategory: Boolean = false,
    val claiming: Boolean = false,
    /** Sisa detik sebelum klaim dicoba ulang otomatis setelah terkena batas laju (429), null jika tidak menunggu. */
    val retryInSeconds: Int? = null,
    /** Hasil klaim yang berhasil, dipakai untuk kartu poin. Poinnya dari server, bukan dihitung di aplikasi. */
    val claimed: ScanClaimData? = null,
    @StringRes val error: Int? = null,
    /** True jika mengulang klaim yang sama aman dan masuk akal (jaringan putus, server sibuk). */
    val canRetry: Boolean = false
)

/**
 * Alur satu layar pemindai sampah: menentukan anak, menerima hasil kamera, meminta pilihan manual di bawah 75%
 * (BR-04) lalu mengirim klaim lewat [TrashRepository]. Terkena batas laju (429) menunggu sesuai `Retry-After`
 * lalu mengirim ulang otomatis. Klaim yang gagal karena jaringan atau server sibuk dikirim
 * ulang dengan [PendingTrashClaim] dan `Idempotency-Key` yang sama, jadi klaim yang sebenarnya sudah masuk tidak
 * terhitung dua kali. Tidak ada foto yang dikirim.
 */
class TrashScanViewModel(
    private val classRepository: ClassRepository,
    private val trashRepository: TrashRepository,
    private val pause: suspend (millis: Long) -> Unit = { delay(it) }
) : ViewModel() {

    private val _state = MutableStateFlow(TrashScanState())
    val state: StateFlow<TrashScanState> = _state.asStateFlow()

    private var started = false
    private var pending: PendingTrashClaim? = null
    private var childrenJob: Job? = null

    /** Hanya pemanggilan pertama yang berlaku, jadi aman dipanggil dari `onCreate` setiap kali layar dibuat ulang. */
    fun start(initialStudentId: String?) {
        if (started) return
        started = true

        if (initialStudentId.isNullOrBlank()) {
            loadChildren()
        } else {
            _state.value = _state.value.copy(loadingChildren = false, childId = initialStudentId)
        }
    }

    fun selectChild(childId: String) {
        pending = null
        _state.value = _state.value.copy(loadingChildren = false, childId = childId, childChoices = emptyList(), error = null)
    }

    /** Hasil stabil dari analyzer. Diabaikan saat klaim sedang berjalan agar objek yang diklaim tidak berubah. */
    fun onDetected(result: TrashResult?) {
        if (_state.value.claiming) return
        _state.value = _state.value.copy(detected = result)
    }

    /** Tombol klaim. Mengulang klaim yang gagal jika ada, selain itu memulai klaim dari hasil kamera. */
    fun claim() {
        val state = _state.value
        if (state.claiming) return

        pending?.let { send(it); return }

        val detected = state.detected
        val error = when {
            state.childId == null -> R.string.sampah_anak_belum_dipilih
            detected == null -> R.string.sampah_belum_terdeteksi
            else -> null
        }
        if (error != null || detected == null || state.childId == null) {
            _state.value = state.copy(error = error, canRetry = false)
            return
        }

        if (detected.confidence < MIN_CONFIDENCE) {
            _state.value = state.copy(askCategory = true, error = null)
        } else {
            send(PendingTrashClaim(state.childId, detected.category, detected.confidence))
        }
    }

    /**
     * Pilihan manual di bawah 75%. Skor yang dikirim tetap skor model untuk objek ini, karena klaim tidak
     * punya kolom sumber dan skor rendah itu satu-satunya tanda bahwa manusia yang menentukan kategorinya.
     */
    fun claimManual(category: TrashCategory) {
        val state = _state.value
        val detected = state.detected
        val childId = state.childId
        if (state.claiming || detected == null || childId == null) return

        _state.value = state.copy(askCategory = false)
        send(PendingTrashClaim(childId, category, detected.confidence))
    }

    fun dismissCategoryChoice() {
        _state.value = _state.value.copy(askCategory = false)
    }

    /** Setelah kartu poin, mulai pemindaian objek berikutnya. */
    fun scanAgain() {
        pending = null
        _state.value = _state.value.copy(claimed = null, detected = null, error = null, canRetry = false)
    }

    /** Dipanggil setelah galat ditampilkan supaya tidak muncul lagi saat layar diputar. */
    fun errorShown() {
        _state.value = _state.value.copy(error = null)
    }

    fun retryChildren() {
        _state.value = _state.value.copy(loadingChildren = true, error = null)
        loadChildren()
    }

    private fun send(claim: PendingTrashClaim) {
        pending = claim
        _state.value = _state.value.copy(claiming = true, error = null, canRetry = false)

        viewModelScope.launch {
            val result = retryWhenRateLimited(
                onWait = { left -> _state.value = _state.value.copy(claiming = true, retryInSeconds = left.takeIf { it > 0 }) },
                pause = pause
            ) { trashRepository.claim(claim) }

            when (result) {
                is ApiResult.Success -> {
                    pending = null
                    _state.value = _state.value.copy(claiming = false, retryInSeconds = null, claimed = result.data)
                }
                is ApiResult.Failure -> {
                    val retryable = result.isRetryable()
                    if (!retryable) pending = null
                    _state.value = _state.value.copy(
                        claiming = false,
                        retryInSeconds = null,
                        error = result.errorMessageRes(),
                        canRetry = retryable
                    )
                }
                is ApiResult.NetworkError -> _state.value = _state.value.copy(
                    claiming = false,
                    retryInSeconds = null,
                    error = result.errorMessageRes(),
                    canRetry = true
                )
            }
        }
    }

    /**
     * Permintaan yang belum diproses server boleh dikirim ulang dengan kunci yang sama. Server sekarang tidak lagi
     * menolak klaim di atas batas harian (poinnya dikurangi), tetapi server lama masih bisa membalas
     * TRASH_DAILY_LIMIT_REACHED yang baru reset tengah malam, jadi tidak diulang. Galat validasi butuh permintaan baru.
     */
    private fun ApiResult.Failure.isRetryable() =
        status >= 500 || errorCode == "IDEMPOTENCY_IN_PROGRESS" || errorCode == "RATE_LIMITED"

    private fun loadChildren() {
        childrenJob?.cancel()
        childrenJob = viewModelScope.launch {
            when (val result = classRepository.children()) {
                is ApiResult.Success -> when (result.data.size) {
                    0 -> _state.value = _state.value.copy(loadingChildren = false, error = R.string.error_no_child)
                    1 -> selectChild(result.data.single().id)
                    else -> _state.value = _state.value.copy(
                        loadingChildren = false,
                        childChoices = result.data,
                        error = null
                    )
                }
                is ApiResult.Failure ->
                    _state.value = _state.value.copy(loadingChildren = false, error = result.errorMessageRes())
                is ApiResult.NetworkError ->
                    _state.value = _state.value.copy(loadingChildren = false, error = result.errorMessageRes())
            }
        }
    }

    class Factory(
        private val classRepository: ClassRepository,
        private val trashRepository: TrashRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            TrashScanViewModel(classRepository, trashRepository) as T
    }
}
