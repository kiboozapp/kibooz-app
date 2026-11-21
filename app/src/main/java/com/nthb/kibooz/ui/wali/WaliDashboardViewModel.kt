package com.nthb.kibooz.ui.wali

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.ChildSummary
import com.nthb.kibooz.data.remote.WaliDashboard
import com.nthb.kibooz.data.remote.errorMessageRes
import com.nthb.kibooz.data.repository.ClassRepository
import com.nthb.kibooz.data.repository.WaliRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class WaliDashboardState(
    val loading: Boolean = true,
    val dashboard: WaliDashboard? = null,
    /** Semua anak Wali, untuk berpindah anak. Kosong jika daftarnya belum termuat. */
    val children: List<ChildSummary> = emptyList(),
    /** True jika server menjawab bahwa Wali belum punya anak (`NO_CHILD_REGISTERED`). */
    val noChild: Boolean = false,
    @StringRes val error: Int? = null
)

/**
 * Dasbor Wali (FR-5.1, FR-5.2). Tanpa pilihan, server memakai anak yang paling lama terdaftar. Daftar anak dimuat
 * bersamaan supaya Wali dengan beberapa anak bisa berpindah. Data lama tetap tampil jika muat ulang gagal.
 */
class WaliDashboardViewModel(
    private val repository: WaliRepository,
    private val classRepository: ClassRepository
) : ViewModel() {

    private val _state = MutableStateFlow(WaliDashboardState())
    val state: StateFlow<WaliDashboardState> = _state.asStateFlow()

    private var selectedChildId: String? = null
    private var job: Job? = null

    init {
        load()
    }

    /** Memuat ulang anak yang sedang dipilih. */
    fun load() = load(selectedChildId)

    fun selectChild(childId: String) {
        selectedChildId = childId
        load(childId)
    }

    private fun load(studentId: String?) {
        job?.cancel()
        _state.value = _state.value.copy(loading = true, error = null, noChild = false)
        job = viewModelScope.launch {
            coroutineScope {
                val dashboard = async { repository.dashboard(studentId) }
                val children = async { classRepository.children() }

                var next = _state.value.copy(loading = false)

                when (val result = dashboard.await()) {
                    is ApiResult.Success -> {
                        selectedChildId = result.data.student.id
                        next = next.copy(dashboard = result.data)
                    }
                    is ApiResult.Failure ->
                        next = if (result.errorCode == "NO_CHILD_REGISTERED") {
                            next.copy(noChild = true, dashboard = null)
                        } else {
                            next.copy(error = result.errorMessageRes())
                        }
                    is ApiResult.NetworkError -> next = next.copy(error = result.errorMessageRes())
                }

                // Daftar anak hanya pelengkap, galatnya tidak menutupi dasbor
                (children.await() as? ApiResult.Success)?.let { next = next.copy(children = it.data) }

                _state.value = next
            }
        }
    }

    fun errorShown() {
        _state.value = _state.value.copy(error = null)
    }

    class Factory(
        private val repository: WaliRepository,
        private val classRepository: ClassRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            WaliDashboardViewModel(repository, classRepository) as T
    }
}
