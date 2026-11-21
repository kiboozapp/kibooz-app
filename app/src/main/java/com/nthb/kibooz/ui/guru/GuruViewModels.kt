package com.nthb.kibooz.ui.guru

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.ClassStudent
import com.nthb.kibooz.data.remote.DailyMoodDistribution
import com.nthb.kibooz.data.remote.GuruDashboard
import com.nthb.kibooz.data.remote.MonthlyCount
import com.nthb.kibooz.data.remote.WeeklyPoint
import com.nthb.kibooz.data.remote.errorMessageRes
import com.nthb.kibooz.data.repository.AnalyticsRange
import com.nthb.kibooz.data.repository.ClassRepository
import com.nthb.kibooz.data.repository.GuruRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class GuruDashboardState(
    val loading: Boolean = true,
    val dashboard: GuruDashboard? = null,
    @StringRes val error: Int? = null
)

/** Dasbor Guru (FR-2.1): satu panggilan, semua angka dihitung server. Data lama tetap tampil jika muat ulang gagal. */
class GuruDashboardViewModel(private val repository: GuruRepository) : ViewModel() {

    private val _state = MutableStateFlow(GuruDashboardState())
    val state: StateFlow<GuruDashboardState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        load()
    }

    fun load() {
        job?.cancel()
        _state.value = _state.value.copy(loading = true, error = null)
        job = viewModelScope.launch {
            _state.value = when (val result = repository.dashboard()) {
                is ApiResult.Success -> GuruDashboardState(loading = false, dashboard = result.data)
                else -> _state.value.copy(loading = false, error = result.errorMessageRes())
            }
        }
    }

    fun errorShown() {
        _state.value = _state.value.copy(error = null)
    }

    class Factory(private val repository: GuruRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = GuruDashboardViewModel(repository) as T
    }
}

data class MoodStatsState(
    val loading: Boolean = true,
    val className: String? = null,
    /** Sebaran mood hari ini untuk donat, dari dasbor. */
    val today: DailyMoodDistribution? = null,
    val weekly: List<WeeklyPoint> = emptyList(),
    val monthly: List<MonthlyCount> = emptyList(),
    /** Siswa kelas beserta mood terbarunya hari ini, untuk tabel. */
    val students: List<ClassStudent> = emptyList(),
    @StringRes val error: Int? = null
)

/**
 * Statistik mood kelas (FR-3). Tiga panggilan berjalan bersamaan: dasbor (donat hari ini), analitik bulanan
 * (tren Senin sampai Jumat dan sebaran bulan) dan daftar siswa (tabel). Jika salah satunya gagal, yang lain tetap
 * ditampilkan dan galatnya dilaporkan sekali.
 */
class MoodStatsViewModel(
    private val guruRepository: GuruRepository,
    private val classRepository: ClassRepository
) : ViewModel() {

    private val _state = MutableStateFlow(MoodStatsState())
    val state: StateFlow<MoodStatsState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        load()
    }

    fun load() {
        job?.cancel()
        _state.value = _state.value.copy(loading = true, error = null)
        job = viewModelScope.launch {
            coroutineScope {
                val dashboard = async { guruRepository.dashboard() }
                val analytics = async { guruRepository.analytics(AnalyticsRange.MONTHLY) }

                val dashboardResult = dashboard.await()
                var next = _state.value.copy(loading = false)
                var error: Int? = null

                when (dashboardResult) {
                    is ApiResult.Success -> next = next.copy(
                        className = dashboardResult.data.classOverview.className,
                        today = dashboardResult.data.dailyMoodDistribution
                    )
                    else -> error = dashboardResult.errorMessageRes()
                }

                when (val result = analytics.await()) {
                    is ApiResult.Success -> next = next.copy(
                        weekly = result.data.weeklyTrend,
                        monthly = result.data.monthlyDistribution.orEmpty()
                    )
                    else -> error = error ?: result.errorMessageRes()
                }

                // Daftar siswa butuh id kelas dari dasbor, jadi menunggu dasbor
                if (dashboardResult is ApiResult.Success) {
                    when (val result = classRepository.allStudents(dashboardResult.data.classOverview.classId)) {
                        is ApiResult.Success -> next = next.copy(students = result.data)
                        else -> error = error ?: result.errorMessageRes()
                    }
                }

                _state.value = next.copy(error = error)
            }
        }
    }

    fun errorShown() {
        _state.value = _state.value.copy(error = null)
    }

    class Factory(
        private val guruRepository: GuruRepository,
        private val classRepository: ClassRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MoodStatsViewModel(guruRepository, classRepository) as T
    }
}
