package com.nthb.kibooz.ui.guru

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.ClassStudent
import com.nthb.kibooz.data.repository.AuthRepository
import com.nthb.kibooz.data.repository.ClassRepository
import com.nthb.kibooz.data.repository.LeaderboardRepository
import com.nthb.kibooz.data.session.Role
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PesertaUiState(
    val isLoading: Boolean = false,
    val className: String = "",
    val teacherName: String = "Siti Rahayu, S.Pd.",
    val teacherRole: String = "Wali Kelas A & Pendidik Utama",
    val students: List<ClassStudent> = emptyList(),
    val errorMessage: String? = null,
    /** Kelas yang daftarnya sedang ditampilkan, null selama belum ada daftar yang berhasil dimuat. */
    val classId: String? = null,
    /** True jika server menjawab bahwa Guru sudah tidak punya akses ke kelas ini. */
    val accessLost: Boolean = false
)

class PesertaKelasViewModel(
    private val classRepository: ClassRepository,
    private val leaderboardRepository: LeaderboardRepository? = null,
    private val authRepository: AuthRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(PesertaUiState(isLoading = true))
    val uiState: StateFlow<PesertaUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    /** Anak sudah keluar dari kelas, jadi barisnya hilang tanpa menunggu muat ulang. */
    fun onStudentRemoved(studentId: String) {
        _uiState.update { state -> state.copy(students = state.students.filterNot { it.id == studentId }) }
    }

    fun loadPeserta(initialClassId: String? = null, defaultClassName: String = "Kelas A") {
        // Muat yang lebih lama bisa membawa daftar basi, jadi hanya yang terbaru yang dipakai
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            var resolvedClassId = initialClassId
            var resolvedClassName = defaultClassName
            val teacherName = _uiState.value.teacherName

            if (resolvedClassId.isNullOrBlank()) {
                val childrenResult = classRepository.children()
                if (childrenResult is ApiResult.Success) {
                    val firstChild = childrenResult.data.firstOrNull()
                    if (firstChild != null) {
                        resolvedClassId = firstChild.classId
                        resolvedClassName = firstChild.className
                    }
                }
            }

            if (resolvedClassId.isNullOrBlank()) {
                val teacherClassesResult = classRepository.teacherClasses()
                if (teacherClassesResult is ApiResult.Success) {
                    val firstClass = teacherClassesResult.data.firstOrNull()
                    if (firstClass != null) {
                        resolvedClassId = firstClass.id
                        resolvedClassName = firstClass.name
                    }
                }
            }

            if (resolvedClassId.isNullOrBlank()) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    className = resolvedClassName,
                    teacherName = teacherName,
                    errorMessage = "Kelas tidak ditemukan"
                )
                return@launch
            }

            val isWaliRole = authRepository?.session?.value?.user?.role == Role.WALI
            if (isWaliRole && leaderboardRepository != null) {
                when (val lbResult = leaderboardRepository.leaderboard(resolvedClassId)) {
                    is ApiResult.Success -> {
                        val allEntries = lbResult.data.podium + lbResult.data.rankings
                        val mappedStudents = allEntries.map { entry ->
                            ClassStudent(
                                id = entry.studentName,
                                fullName = entry.studentName,
                                nisn = "-",
                                avatarUrl = entry.avatarUrl,
                                currentPoints = entry.points,
                                seasonPoints = entry.points,
                                classRank = entry.rank,
                                todayMood = null
                            )
                        }
                        _uiState.value = PesertaUiState(
                            isLoading = false,
                            className = resolvedClassName,
                            teacherName = teacherName,
                            students = mappedStudents,
                            errorMessage = null
                        )
                        return@launch
                    }
                    is ApiResult.Failure -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            className = resolvedClassName,
                            teacherName = teacherName,
                            errorMessage = lbResult.message
                        )
                        return@launch
                    }
                    is ApiResult.NetworkError -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            className = resolvedClassName,
                            teacherName = teacherName,
                            errorMessage = "Koneksi internet bermasalah"
                        )
                        return@launch
                    }
                }
            }

            when (val studentsResult = classRepository.allStudents(resolvedClassId)) {
                is ApiResult.Success -> {
                    _uiState.value = PesertaUiState(
                        isLoading = false,
                        className = resolvedClassName,
                        teacherName = teacherName,
                        students = studentsResult.data,
                        errorMessage = null,
                        classId = resolvedClassId
                    )
                }
                is ApiResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        className = resolvedClassName,
                        teacherName = teacherName,
                        errorMessage = studentsResult.message,
                        // Rute `/classes/{classId}` menjawab 403 untuk guru yang sudah tidak mengajar di kelas itu
                        accessLost = !isWaliRole && studentsResult.errorCode in LOST_ACCESS_CODES
                    )
                }
                is ApiResult.NetworkError -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        className = resolvedClassName,
                        teacherName = teacherName,
                        errorMessage = "Koneksi internet bermasalah"
                    )
                }
            }
        }
    }

    private companion object {
        val LOST_ACCESS_CODES = setOf("AUTH_FORBIDDEN", "CLASS_NOT_FOUND")
    }

    class Factory(
        private val classRepository: ClassRepository,
        private val leaderboardRepository: LeaderboardRepository? = null,
        private val authRepository: AuthRepository? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PesertaKelasViewModel(classRepository, leaderboardRepository, authRepository) as T
    }
}
