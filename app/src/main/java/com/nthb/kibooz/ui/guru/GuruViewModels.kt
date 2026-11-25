package com.nthb.kibooz.ui.guru

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.ClassStudent
import com.nthb.kibooz.data.remote.ClassSummary
import com.nthb.kibooz.data.remote.DailyMoodDistribution
import com.nthb.kibooz.data.remote.GuruDashboard
import com.nthb.kibooz.data.remote.MonthlyCount
import com.nthb.kibooz.data.remote.ServiceOutage
import com.nthb.kibooz.data.remote.WeeklyPoint
import com.nthb.kibooz.data.remote.errorMessageRes
import com.nthb.kibooz.data.remote.outage
import com.nthb.kibooz.data.remote.outageKind
import com.nthb.kibooz.data.repository.AnalyticsRange
import com.nthb.kibooz.data.repository.ClassRepository
import com.nthb.kibooz.data.repository.GuruRepository
import kotlin.random.Random
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
    val classes: List<ClassSummary> = emptyList(),
    val selectedClassId: String? = null,
    @StringRes val error: Int? = null,
    /** Terisi jika server tidak terjangkau (FE-12). Layar menampilkan hitung mundur lalu memanggil [GuruDashboardViewModel.retryAfterOutage]. */
    val outage: ServiceOutage? = null
)

/** Dasbor Guru (FR-2.1): angka dihitung server per kelas aktif. Data lama tetap tampil jika muat ulang gagal. */
class GuruDashboardViewModel(
    private val repository: GuruRepository,
    private val classRepository: ClassRepository? = null,
    initialClassId: String? = null
) : ViewModel() {

    private val _state = MutableStateFlow(
        GuruDashboardState(selectedClassId = initialClassId?.takeIf { it.isNotBlank() })
    )
    val state: StateFlow<GuruDashboardState> = _state.asStateFlow()

    private var job: Job? = null

    /** Gangguan berturut-turut sejak muat terakhir yang dimulai pengguna. Menentukan seberapa panjang jeda berikutnya. */
    private var outageAttempts = 0

    init {
        load()
    }

    /** Memuat ulang dari awal, misalnya saat layar tampil lagi atau pengguna menekan coba lagi. */
    fun load(classId: String? = _state.value.selectedClassId) {
        val targetClassId = classId?.takeIf { it.isNotBlank() } ?: _state.value.selectedClassId
        if (targetClassId != _state.value.selectedClassId) {
            _state.value = _state.value.copy(selectedClassId = targetClassId)
        }
        outageAttempts = 0
        fetch(targetClassId)
    }

    /** Mengganti kelas yang ditampilkan di dasbor dan memuat ulang metrik untuk kelas tersebut. */
    fun selectClass(classId: String) {
        val cleanId = classId.takeIf { it.isNotBlank() } ?: return
        if (cleanId == _state.value.selectedClassId && _state.value.dashboard?.classOverview?.classId == cleanId) return
        _state.value = _state.value.copy(
            dashboard = if (_state.value.dashboard?.classOverview?.classId == cleanId) _state.value.dashboard else null,
            selectedClassId = cleanId
        )
        outageAttempts = 0
        fetch(cleanId)
    }

    /** Percobaan otomatis setelah gangguan. Jeda berikutnya lebih panjang selama gangguan berlanjut. */
    fun retryAfterOutage() = fetch(_state.value.selectedClassId)

    private fun fetch(classId: String?) {
        job?.cancel()
        _state.value = _state.value.copy(loading = true, error = null, outage = null)
        job = viewModelScope.launch {
            coroutineScope {
                val dashboardDeferred = async { repository.dashboard(classId) }
                val classesDeferred = classRepository?.let { repo -> async { fetchVisibleTeacherClasses(repo) } }

                val result = dashboardDeferred.await()
                val loadedClasses = classesDeferred?.await()
                val nextClasses = loadedClasses ?: _state.value.classes

                _state.value = when (result) {
                    is ApiResult.Success -> {
                        outageAttempts = 0
                        GuruDashboardState(
                            loading = false,
                            dashboard = result.data,
                            classes = nextClasses,
                            selectedClassId = classId ?: result.data.classOverview.classId
                        )
                    }
                    else -> {
                        val outage = if (result.outageKind() != null) {
                            result.outage(++outageAttempts, Random.nextLong(OUTAGE_JITTER_SECONDS))
                        } else {
                            null
                        }
                        _state.value.copy(
                            loading = false,
                            classes = nextClasses,
                            selectedClassId = classId,
                            error = result.errorMessageRes(),
                            outage = outage
                        )
                    }
                }
            }
        }
    }

    fun errorShown() {
        _state.value = _state.value.copy(error = null)
    }

    class Factory(
        private val repository: GuruRepository,
        private val classRepository: ClassRepository? = null,
        private val initialClassId: String? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            GuruDashboardViewModel(repository, classRepository, initialClassId) as T
    }

    private companion object {
        /** Jeda acak 0 sampai 5 detik di atas `Retry-After` supaya semua aplikasi tidak mencoba di detik yang sama. */
        const val OUTAGE_JITTER_SECONDS = 6L
    }
}

data class MoodStatsState(
    val loading: Boolean = true,
    val className: String? = null,
    val selectedClassId: String? = null,
    val classes: List<ClassSummary> = emptyList(),
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
    private val classRepository: ClassRepository,
    initialClassId: String? = null,
    private val loadClassOptions: Boolean = false
) : ViewModel() {

    private val _state = MutableStateFlow(
        MoodStatsState(selectedClassId = initialClassId?.takeIf { it.isNotBlank() })
    )
    val state: StateFlow<MoodStatsState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        load()
    }

    fun selectClass(classId: String) {
        val cleanId = classId.takeIf { it.isNotBlank() } ?: return
        if (cleanId == _state.value.selectedClassId && _state.value.today != null) return
        _state.value = _state.value.copy(
            selectedClassId = cleanId,
            today = null,
            weekly = emptyList(),
            monthly = emptyList(),
            students = emptyList()
        )
        load(cleanId)
    }

    fun load(classId: String? = _state.value.selectedClassId) {
        val targetClassId = classId?.takeIf { it.isNotBlank() } ?: _state.value.selectedClassId
        job?.cancel()
        _state.value = _state.value.copy(loading = true, selectedClassId = targetClassId, error = null)
        job = viewModelScope.launch {
            coroutineScope {
                val dashboard = async { guruRepository.dashboard(targetClassId) }
                val analytics = async { guruRepository.analytics(AnalyticsRange.MONTHLY, targetClassId) }
                val classesDeferred = if (loadClassOptions) async { fetchVisibleTeacherClasses(classRepository) } else null

                val dashboardResult = dashboard.await()
                val loadedClasses = classesDeferred?.await()
                var next = _state.value.copy(
                    loading = false,
                    classes = loadedClasses ?: _state.value.classes
                )
                var error: Int? = null

                when (dashboardResult) {
                    is ApiResult.Success -> next = next.copy(
                        className = dashboardResult.data.classOverview.className,
                        selectedClassId = targetClassId ?: dashboardResult.data.classOverview.classId,
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
        private val classRepository: ClassRepository,
        private val initialClassId: String? = null,
        private val loadClassOptions: Boolean = false
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MoodStatsViewModel(guruRepository, classRepository, initialClassId, loadClassOptions) as T
    }
}
