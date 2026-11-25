package com.nthb.kibooz.ui.guru

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nthb.kibooz.R
import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.ClassStudent
import com.nthb.kibooz.data.remote.errorMessageRes
import com.nthb.kibooz.data.repository.AuthRepository
import com.nthb.kibooz.data.repository.ClassManagementRepository
import com.nthb.kibooz.data.repository.ClassRepository
import com.nthb.kibooz.data.repository.LeaderboardRepository
import com.nthb.kibooz.data.session.Role
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PesertaMessage(
    @StringRes val resId: Int,
    val studentName: String? = null,
    val count: Int = 0
)

data class PesertaUiState(
    val isLoading: Boolean = false,
    val classId: String? = null,
    val className: String = "",
    val classArchived: Boolean = false,
    val isTeacher: Boolean = false,
    val teacherName: String = "Siti Rahayu, S.Pd.",
    val teacherRole: String = "Wali Kelas A & Pendidik Utama",
    val students: List<ClassStudent> = emptyList(),
    val busyStudentId: String? = null,
    val actionMessage: PesertaMessage? = null,
    val errorMessage: String? = null
)

class PesertaKelasViewModel(
    private val classRepository: ClassRepository,
    private val leaderboardRepository: LeaderboardRepository? = null,
    private val authRepository: AuthRepository? = null,
    private val classManagementRepository: ClassManagementRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(PesertaUiState(isLoading = true))
    val uiState: StateFlow<PesertaUiState> = _uiState.asStateFlow()

    fun loadPeserta(initialClassId: String? = null, defaultClassName: String = "Kelas A") {
        viewModelScope.launch {
            val isWaliRole = authRepository?.session?.value?.user?.role == Role.WALI
            val teacherRole = authRepository?.session?.value?.user?.role == Role.GURU ||
                (!isWaliRole && classManagementRepository != null)

            _uiState.value = _uiState.value.copy(
                isLoading = true,
                isTeacher = teacherRole,
                errorMessage = null
            )

            var resolvedClassId = initialClassId
            var resolvedClassName = defaultClassName
            var resolvedArchived = false
            val teacherName = _uiState.value.teacherName

            if (resolvedClassId.isNullOrBlank()) {
                val childrenResult = classRepository.children()
                if (childrenResult is ApiResult.Success) {
                    val firstChild = childrenResult.data.firstOrNull()
                    if (firstChild != null) {
                        resolvedClassId = firstChild.classId
                        resolvedClassName = firstChild.className
                        resolvedArchived = firstChild.classArchivedAt != null
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
                        resolvedArchived = firstClass.archived
                    }
                }
            }

            if (resolvedClassId.isNullOrBlank()) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    classId = null,
                    className = resolvedClassName,
                    classArchived = false,
                    isTeacher = teacherRole,
                    teacherName = teacherName,
                    errorMessage = "Kelas tidak ditemukan"
                )
                return@launch
            }

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
                            classId = resolvedClassId,
                            className = resolvedClassName,
                            classArchived = resolvedArchived,
                            isTeacher = false,
                            teacherName = teacherName,
                            students = mappedStudents,
                            errorMessage = null
                        )
                        return@launch
                    }
                    is ApiResult.Failure -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            classId = resolvedClassId,
                            className = resolvedClassName,
                            classArchived = resolvedArchived,
                            isTeacher = false,
                            teacherName = teacherName,
                            errorMessage = lbResult.message
                        )
                        return@launch
                    }
                    is ApiResult.NetworkError -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            classId = resolvedClassId,
                            className = resolvedClassName,
                            classArchived = resolvedArchived,
                            isTeacher = false,
                            teacherName = teacherName,
                            errorMessage = "Koneksi internet bermasalah"
                        )
                        return@launch
                    }
                }
            }

            when (val studentsResult = classRepository.allStudents(resolvedClassId)) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        classId = resolvedClassId,
                        className = resolvedClassName,
                        classArchived = resolvedArchived,
                        isTeacher = teacherRole,
                        teacherName = teacherName,
                        students = studentsResult.data,
                        errorMessage = null
                    )
                }
                is ApiResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        classId = resolvedClassId,
                        className = resolvedClassName,
                        classArchived = resolvedArchived,
                        isTeacher = teacherRole,
                        teacherName = teacherName,
                        errorMessage = studentsResult.message
                    )
                }
                is ApiResult.NetworkError -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        classId = resolvedClassId,
                        className = resolvedClassName,
                        classArchived = resolvedArchived,
                        isTeacher = teacherRole,
                        teacherName = teacherName,
                        errorMessage = "Koneksi internet bermasalah"
                    )
                }
            }
        }
    }

    fun verifyStudentNisn(student: ClassStudent) {
        val repo = classManagementRepository ?: return
        val classId = _uiState.value.classId?.takeIf { it.isNotBlank() } ?: return
        if (_uiState.value.busyStudentId != null) return

        if (student.nisnNeedsUpdate || student.nisn.isBlank() || student.nisn == "-") {
            _uiState.value = _uiState.value.copy(
                actionMessage = PesertaMessage(R.string.error_nisn_not_set)
            )
            return
        }
        if (_uiState.value.classArchived) {
            _uiState.value = _uiState.value.copy(
                actionMessage = PesertaMessage(R.string.error_class_archived)
            )
            return
        }

        _uiState.value = _uiState.value.copy(busyStudentId = student.id, actionMessage = null)
        viewModelScope.launch {
            when (val res = repo.verifyNisn(classId, student.id)) {
                is ApiResult.Success -> {
                    val displaced = res.data.displaced
                    var updatedList = _uiState.value.students.map { item ->
                        when {
                            item.id == student.id -> item.copy(
                                nisnVerified = res.data.nisnVerified,
                                nisnNeedsUpdate = false
                            )
                            displaced > 0 && !item.nisnVerified && item.nisn == student.nisn -> item.copy(
                                nisn = "",
                                nisnNeedsUpdate = true
                            )
                            else -> item
                        }
                    }
                    if (displaced > 0) {
                        val refreshed = classRepository.allStudents(classId)
                        if (refreshed is ApiResult.Success) {
                            updatedList = refreshed.data
                        }
                    }
                    val message = if (displaced > 0) {
                        PesertaMessage(R.string.peserta_nisn_sukses_verif_geser, student.fullName, displaced)
                    } else {
                        PesertaMessage(R.string.peserta_nisn_sukses_verif, student.fullName)
                    }
                    _uiState.value = _uiState.value.copy(
                        students = updatedList,
                        busyStudentId = null,
                        actionMessage = message
                    )
                }
                else -> handleNisnError(classId, res)
            }
        }
    }

    fun unverifyStudentNisn(student: ClassStudent) {
        val repo = classManagementRepository ?: return
        val classId = _uiState.value.classId?.takeIf { it.isNotBlank() } ?: return
        if (_uiState.value.busyStudentId != null) return
        if (_uiState.value.classArchived) {
            _uiState.value = _uiState.value.copy(
                actionMessage = PesertaMessage(R.string.error_class_archived)
            )
            return
        }

        _uiState.value = _uiState.value.copy(busyStudentId = student.id, actionMessage = null)
        viewModelScope.launch {
            when (val res = repo.unverifyNisn(classId, student.id)) {
                is ApiResult.Success -> {
                    val updatedList = _uiState.value.students.map { item ->
                        if (item.id == student.id) item.copy(nisnVerified = res.data.nisnVerified) else item
                    }
                    _uiState.value = _uiState.value.copy(
                        students = updatedList,
                        busyStudentId = null,
                        actionMessage = PesertaMessage(R.string.peserta_nisn_sukses_batal, student.fullName)
                    )
                }
                else -> handleNisnError(classId, res)
            }
        }
    }

    fun removeStudent(student: ClassStudent) {
        val repo = classManagementRepository ?: return
        val classId = _uiState.value.classId?.takeIf { it.isNotBlank() } ?: return
        if (_uiState.value.busyStudentId != null) return
        if (_uiState.value.classArchived) {
            _uiState.value = _uiState.value.copy(
                actionMessage = PesertaMessage(R.string.error_class_archived)
            )
            return
        }

        _uiState.value = _uiState.value.copy(busyStudentId = student.id, actionMessage = null)
        viewModelScope.launch {
            when (val res = repo.removeStudent(classId, student.id)) {
                is ApiResult.Success -> {
                    val remaining = _uiState.value.students.filterNot { it.id == student.id }
                    _uiState.value = _uiState.value.copy(
                        students = remaining,
                        busyStudentId = null,
                        actionMessage = PesertaMessage(R.string.peserta_keluarkan_sukses, student.fullName)
                    )
                }
                else -> handleNisnError(classId, res)
            }
        }
    }

    private suspend fun handleNisnError(classId: String, result: ApiResult<*>) {
        val code = (result as? ApiResult.Failure)?.errorCode
        val archivedNow = _uiState.value.classArchived || code == "CLASS_ARCHIVED"
        val resId = when (code) {
            "NISN_ALREADY_REGISTERED" -> R.string.peserta_nisn_error_sudah_dipakai
            else -> result.errorMessageRes() ?: R.string.error_unknown
        }
        var updatedStudents = _uiState.value.students
        if (code == "STUDENT_NOT_FOUND" || code == "NISN_NOT_SET") {
            val refreshed = classRepository.allStudents(classId)
            if (refreshed is ApiResult.Success) {
                updatedStudents = refreshed.data
            }
        }
        _uiState.value = _uiState.value.copy(
            students = updatedStudents,
            classArchived = archivedNow,
            busyStudentId = null,
            actionMessage = PesertaMessage(resId)
        )
    }

    fun actionMessageShown() {
        _uiState.value = _uiState.value.copy(actionMessage = null)
    }

    class Factory(
        private val classRepository: ClassRepository,
        private val leaderboardRepository: LeaderboardRepository? = null,
        private val authRepository: AuthRepository? = null,
        private val classManagementRepository: ClassManagementRepository? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PesertaKelasViewModel(classRepository, leaderboardRepository, authRepository, classManagementRepository) as T
    }
}
