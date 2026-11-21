package com.nthb.kibooz.ui.wali

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nthb.kibooz.R
import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.errorMessageRes
import com.nthb.kibooz.data.remote.retryWhenRateLimited
import com.nthb.kibooz.data.repository.PendingGuidanceApply
import com.nthb.kibooz.data.repository.WaliRepository
import com.nthb.kibooz.util.FormRules
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class GuidanceApplyState(
    val sending: Boolean = false,
    /** Sisa detik sebelum kiriman diulang otomatis setelah terkena batas laju (429), null jika tidak menunggu. */
    val retryInSeconds: Int? = null,
    /** True setelah server menerima laporan. Tombolnya tidak boleh dipakai lagi. */
    val applied: Boolean = false,
    @StringRes val error: Int? = null,
    /** True jika mengirim ulang laporan yang sama aman (jaringan putus, server sibuk). */
    val canRetry: Boolean = false
)

/**
 * "Terapkan Panduan & Laporkan ke Guru" (FR-5.4). Laporan yang gagal karena jaringan atau server dikirim ulang
 * dengan [PendingGuidanceApply] dan `Idempotency-Key` yang sama, jadi laporan yang sebenarnya sudah masuk tidak
 * tercatat dua kali. Batas laju (429) menunggu sesuai `Retry-After` lalu mengirim ulang otomatis.
 */
class GuidanceApplyViewModel(
    private val repository: WaliRepository,
    private val pause: suspend (millis: Long) -> Unit = { delay(it) }
) : ViewModel() {

    private val _state = MutableStateFlow(GuidanceApplyState())
    val state: StateFlow<GuidanceApplyState> = _state.asStateFlow()

    private var pending: PendingGuidanceApply? = null

    /**
     * Mengirim laporan. Setelah gagal yang boleh diulang, panggilan berikutnya mengirim laporan yang sama dan
     * mengabaikan [notes] baru, karena catatannya sudah terikat pada kunci laporan itu.
     */
    fun apply(guidanceId: String?, studentId: String?, notes: String) {
        val current = _state.value
        if (current.sending || current.applied) return

        pending?.let { send(it); return }

        if (guidanceId.isNullOrBlank() || studentId.isNullOrBlank()) {
            _state.value = current.copy(error = R.string.panduan_tidak_ada, canRetry = false)
            return
        }
        FormRules.notes(notes)?.let {
            _state.value = current.copy(error = it, canRetry = false)
            return
        }

        send(PendingGuidanceApply(guidanceId, studentId, notes))
    }

    private fun send(apply: PendingGuidanceApply) {
        pending = apply
        _state.value = _state.value.copy(sending = true, error = null, canRetry = false)

        viewModelScope.launch {
            val result = retryWhenRateLimited(
                onWait = { left -> _state.value = _state.value.copy(sending = true, retryInSeconds = left.takeIf { it > 0 }) },
                pause = pause
            ) { repository.applyGuidance(apply) }

            _state.value = when (result) {
                is ApiResult.Success -> {
                    pending = null
                    GuidanceApplyState(applied = true)
                }
                is ApiResult.Failure -> {
                    val retryable = result.status >= 500 || result.errorCode == "IDEMPOTENCY_IN_PROGRESS" ||
                        result.errorCode == "RATE_LIMITED"
                    if (!retryable) pending = null
                    GuidanceApplyState(error = result.errorMessageRes(), canRetry = retryable)
                }
                is ApiResult.NetworkError -> GuidanceApplyState(error = result.errorMessageRes(), canRetry = true)
            }
        }
    }

    fun errorShown() {
        _state.value = _state.value.copy(error = null)
    }

    class Factory(private val repository: WaliRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = GuidanceApplyViewModel(repository) as T
    }
}
