package com.nthb.kibooz.ui.wali

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.TrashStats
import com.nthb.kibooz.data.remote.errorMessageRes
import com.nthb.kibooz.data.repository.TrashRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TrashStatsState(
    val loading: Boolean = true,
    val stats: TrashStats? = null,
    /** True jika server menjawab bahwa Wali belum punya anak (`NO_CHILD_REGISTERED`). */
    val noChild: Boolean = false,
    @StringRes val error: Int? = null
)

/** Statistik pilah sampah seorang anak (FE-W05). [studentId] null berarti anak yang paling lama terdaftar. */
class TrashStatsViewModel(
    private val repository: TrashRepository,
    private val studentId: String?
) : ViewModel() {

    private val _state = MutableStateFlow(TrashStatsState())
    val state: StateFlow<TrashStatsState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        load()
    }

    fun load() {
        job?.cancel()
        _state.value = _state.value.copy(loading = true, error = null, noChild = false)
        job = viewModelScope.launch {
            _state.value = when (val result = repository.stats(studentId)) {
                is ApiResult.Success -> TrashStatsState(loading = false, stats = result.data)
                is ApiResult.Failure ->
                    if (result.errorCode == "NO_CHILD_REGISTERED") {
                        TrashStatsState(loading = false, noChild = true)
                    } else {
                        _state.value.copy(loading = false, error = result.errorMessageRes())
                    }
                is ApiResult.NetworkError -> _state.value.copy(loading = false, error = result.errorMessageRes())
            }
        }
    }

    fun errorShown() {
        _state.value = _state.value.copy(error = null)
    }

    class Factory(private val repository: TrashRepository, private val studentId: String?) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = TrashStatsViewModel(repository, studentId) as T
    }
}
