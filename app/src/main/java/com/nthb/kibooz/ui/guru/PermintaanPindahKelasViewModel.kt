package com.nthb.kibooz.ui.guru

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nthb.kibooz.R
import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.ClassMoveRequest
import com.nthb.kibooz.data.remote.OutageKind
import com.nthb.kibooz.data.remote.errorMessageRes
import com.nthb.kibooz.data.remote.outageKind
import com.nthb.kibooz.data.repository.MoveRequestRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PermintaanPindahState(
    val loading: Boolean = true,
    val requests: List<ClassMoveRequest> = emptyList(),
    @StringRes val loadError: Int? = null,
    val outage: OutageKind? = null,
    val busyRequestId: String? = null,
    val noticeText: String? = null,
    @StringRes val errorNoticeRes: Int? = null,
    val changed: Boolean = false
)

/**
 * Mengelola daftar pengajuan pindah masuk ke kelas Guru (`GET /guru/classes/{classId}/move-requests`)
 * serta keputusan Terima (`accept`) dan Tolak (`reject`).
 */
class PermintaanPindahKelasViewModel(
    private val repository: MoveRequestRepository,
    private val classId: String
) : ViewModel() {

    private val _state = MutableStateFlow(PermintaanPindahState())
    val state: StateFlow<PermintaanPindahState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        if (classId.isBlank()) {
            _state.value = _state.value.copy(loading = false, loadError = R.string.error_class_gone)
            return
        }
        _state.value = _state.value.copy(loading = true, loadError = null, outage = null)
        viewModelScope.launch {
            _state.value = when (val res = repository.classMoveRequests(classId)) {
                is ApiResult.Success -> _state.value.copy(loading = false, requests = res.data)
                else -> _state.value.copy(
                    loading = false,
                    loadError = res.errorMessageRes() ?: R.string.error_unknown,
                    outage = res.outageKind()
                )
            }
        }
    }

    fun accept(request: ClassMoveRequest, successNotice: String) {
        decide(request, accept = true, successNotice = successNotice)
    }

    fun reject(request: ClassMoveRequest, successNotice: String) {
        decide(request, accept = false, successNotice = successNotice)
    }

    private fun decide(request: ClassMoveRequest, accept: Boolean, successNotice: String) {
        if (_state.value.busyRequestId != null || classId.isBlank()) return
        _state.value = _state.value.copy(busyRequestId = request.id)

        viewModelScope.launch {
            val res = if (accept) {
                repository.acceptClassMove(classId, request.id)
            } else {
                repository.rejectClassMove(classId, request.id)
            }
            when (res) {
                is ApiResult.Success -> {
                    val remaining = _state.value.requests.filterNot { it.id == request.id }
                    _state.value = _state.value.copy(
                        busyRequestId = null,
                        requests = remaining,
                        noticeText = successNotice,
                        changed = true
                    )
                }
                else -> {
                    val code = (res as? ApiResult.Failure)?.errorCode
                    val errRes = res.errorMessageRes() ?: R.string.error_unknown
                    _state.value = _state.value.copy(busyRequestId = null, errorNoticeRes = errRes)
                    if (code == "MOVE_REQUEST_NOT_FOUND") {
                        load()
                    }
                }
            }
        }
    }

    fun noticeShown() {
        _state.value = _state.value.copy(noticeText = null, errorNoticeRes = null)
    }

    class Factory(
        private val repository: MoveRequestRepository,
        private val classId: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PermintaanPindahKelasViewModel(repository, classId) as T
    }
}
