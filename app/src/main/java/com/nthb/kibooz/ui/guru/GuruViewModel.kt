package com.nthb.kibooz.ui.guru

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.errorMessageRes
import com.nthb.kibooz.data.repository.GuruRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class GuruDashboardState(
    val loading: Boolean = true,
    val teacherName: String? = null,
    val summary: DashboardSummary? = null,
    /** Galat terakhir saat memuat. Hilang saat [GuruViewModel.load] dipanggil lagi. */
    @StringRes val error: Int? = null
)

/**
 * Memuat ringkasan kelas untuk dasbor Guru. Data yang sudah tampil dipertahankan selama memuat ulang dan saat
 * pemuatan gagal, jadi jaringan yang putus sebentar tidak mengosongkan layar.
 */
class GuruViewModel(private val repository: GuruRepository) : ViewModel() {

    private val _state = MutableStateFlow(GuruDashboardState())
    val state: StateFlow<GuruDashboardState> = _state.asStateFlow()

    private var job: Job? = null

    /** Memuat ringkasan. Panggilan baru membatalkan yang masih berjalan. */
    fun load() {
        job?.cancel()
        _state.value = _state.value.copy(loading = true, error = null)

        job = viewModelScope.launch {
            when (val result = repository.dashboard()) {
                is ApiResult.Success -> _state.value = GuruDashboardState(
                    loading = false,
                    teacherName = result.data.teacher.fullName,
                    summary = result.data.toSummary()
                )
                is ApiResult.Failure -> _state.value = _state.value.copy(loading = false, error = result.errorMessageRes())
                is ApiResult.NetworkError -> _state.value = _state.value.copy(loading = false, error = result.errorMessageRes())
            }
        }
    }

    class Factory(private val repository: GuruRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = GuruViewModel(repository) as T
    }
}
