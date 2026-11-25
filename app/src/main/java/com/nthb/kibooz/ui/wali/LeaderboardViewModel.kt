package com.nthb.kibooz.ui.wali

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.ClassSummary
import com.nthb.kibooz.data.remote.LeaderboardData
import com.nthb.kibooz.data.remote.LeaderboardPeriod
import com.nthb.kibooz.data.remote.RankEntry
import com.nthb.kibooz.data.remote.errorMessageRes
import com.nthb.kibooz.data.repository.ClassRepository
import com.nthb.kibooz.data.repository.LeaderboardRepository
import com.nthb.kibooz.ui.guru.fetchVisibleTeacherClasses
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LeaderboardState(
    val loading: Boolean = true,
    val data: LeaderboardData? = null,
    val selectedClassId: String? = null,
    val classes: List<ClassSummary> = emptyList(),
    @StringRes val error: Int? = null,
    /** Rentang poin yang dipilih. [data] null selama rentang baru belum selesai dimuat. */
    val period: LeaderboardPeriod = LeaderboardPeriod.SEMESTER
) {
    /** True jika kelas belum punya siswa sama sekali. */
    val empty: Boolean get() = data != null && data.podium.isEmpty() && data.rankings.isEmpty()

    /** Semua siswa urut peringkat untuk daftar di bawah podium, tiga besar ikut di depan. Kosong selama data belum ada. */
    val allRanks: List<RankEntry> get() = data?.let { it.podium + it.rankings } ?: emptyList()
}

/** Papan peringkat (FR-6.3, FR-6.4). [initialClassId] null berarti kelas anak yang dipilih server. Data lama tetap tampil jika muat ulang gagal. */
class LeaderboardViewModel(
    private val repository: LeaderboardRepository,
    initialClassId: String?,
    private val classRepository: ClassRepository? = null
) : ViewModel() {

    private var currentClassId: String? = initialClassId?.takeIf { it.isNotBlank() }

    private val _state = MutableStateFlow(LeaderboardState(selectedClassId = currentClassId))
    val state: StateFlow<LeaderboardState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        load()
    }

    fun load() {
        job?.cancel()
        _state.value = _state.value.copy(loading = true, error = null)
        val period = _state.value.period
        val classId = currentClassId
        job = viewModelScope.launch {
            coroutineScope {
                val leaderboardDeferred = async { repository.leaderboard(classId, period) }
                val classesDeferred = if (classRepository != null && _state.value.classes.isEmpty()) {
                    async { fetchVisibleTeacherClasses(classRepository) }
                } else {
                    null
                }

                val result = leaderboardDeferred.await()
                val loadedClasses = classesDeferred?.await()
                val nextClasses = loadedClasses ?: _state.value.classes
                val resolvedClassId = currentClassId ?: nextClasses.firstOrNull()?.id

                _state.value = when (result) {
                    is ApiResult.Success -> _state.value.copy(
                        loading = false,
                        data = result.data,
                        classes = nextClasses,
                        selectedClassId = resolvedClassId,
                        error = null
                    )
                    else -> _state.value.copy(
                        loading = false,
                        classes = nextClasses,
                        selectedClassId = resolvedClassId,
                        error = result.errorMessageRes()
                    )
                }
            }
        }
    }

    /** Memilih kelas lain untuk Guru yang mengajar lebih dari satu kelas. */
    fun selectClass(classId: String) {
        val cleanId = classId.takeIf { it.isNotBlank() } ?: return
        if (cleanId == _state.value.selectedClassId && _state.value.data != null) return
        currentClassId = cleanId
        _state.value = _state.value.copy(selectedClassId = cleanId, data = null)
        load()
    }

    /** Mensinkronkan pilihan kelas saat Guru kembali dari layar lain. */
    fun syncClass(classId: String?) {
        val cleanId = classId?.takeIf { it.isNotBlank() } ?: return
        if (cleanId != currentClassId) {
            selectClass(cleanId)
        }
    }

    /**
     * Ganti rentang poin. Peringkat rentang sebelumnya dibuang supaya tidak tampil di bawah tab yang salah
     * saat rentang baru gagal dimuat.
     */
    fun selectPeriod(period: LeaderboardPeriod) {
        if (period == _state.value.period) return
        _state.value = _state.value.copy(period = period, data = null)
        load()
    }

    fun errorShown() {
        _state.value = _state.value.copy(error = null)
    }

    class Factory(
        private val repository: LeaderboardRepository,
        private val classId: String?,
        private val classRepository: ClassRepository? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            LeaderboardViewModel(repository, classId, classRepository) as T
    }
}
