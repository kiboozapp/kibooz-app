package com.nthb.kibooz.ui.wali

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.LeaderboardData
import com.nthb.kibooz.data.remote.errorMessageRes
import com.nthb.kibooz.data.repository.LeaderboardRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LeaderboardState(
    val loading: Boolean = true,
    val data: LeaderboardData? = null,
    @StringRes val error: Int? = null
) {
    /** True jika kelas belum punya siswa sama sekali. */
    val empty: Boolean get() = data != null && data.podium.isEmpty() && data.rankings.isEmpty()
}

/** Papan peringkat (FR-6.3, FR-6.4). [classId] null berarti kelas anak yang dipilih server. Data lama tetap tampil jika muat ulang gagal. */
class LeaderboardViewModel(
    private val repository: LeaderboardRepository,
    private val classId: String?
) : ViewModel() {

    private val _state = MutableStateFlow(LeaderboardState())
    val state: StateFlow<LeaderboardState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        load()
    }

    fun load() {
        job?.cancel()
        _state.value = _state.value.copy(loading = true, error = null)
        job = viewModelScope.launch {
            _state.value = when (val result = repository.leaderboard(classId)) {
                is ApiResult.Success -> LeaderboardState(loading = false, data = result.data)
                else -> _state.value.copy(loading = false, error = result.errorMessageRes())
            }
        }
    }

    fun errorShown() {
        _state.value = _state.value.copy(error = null)
    }

    class Factory(private val repository: LeaderboardRepository, private val classId: String?) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = LeaderboardViewModel(repository, classId) as T
    }
}
