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
import com.nthb.kibooz.data.repository.ChatRepository
import com.nthb.kibooz.data.repository.ClassManagementRepository
import com.nthb.kibooz.data.repository.ClassRepository
import com.nthb.kibooz.data.repository.LeaderboardRepository
import com.nthb.kibooz.data.session.Role
import com.nthb.kibooz.data.session.SessionUser
import com.nthb.kibooz.util.ClassTeacherSummary
import com.nthb.kibooz.util.ClassTeachers
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
    val teacherName: String = ClassTeachers.DEFAULT_TEACHER_LABEL,
    val teacherAvatarUrl: String? = null,
    val teacherRole: String = ClassTeachers.DEFAULT_ROLE_LABEL,
    val teacherCount: Int = 1,
    val students: List<ClassStudent> = emptyList(),
    val busyStudentId: String? = null,
    val actionMessage: PesertaMessage? = null,
    val errorMessage: String? = null
)

class PesertaKelasViewModel(
    private val classRepository: ClassRepository,
    private val leaderboardRepository: LeaderboardRepository? = null,
    private val authRepository: AuthRepository? = null,
    private val classManagementRepository: ClassManagementRepository? = null,
    private val chatRepository: ChatRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(PesertaUiState(isLoading = true))
    val uiState: StateFlow<PesertaUiState> = _uiState.asStateFlow()

    private data class ResolvedClassContext(
        val classId: String,
        val className: String,
        val archived: Boolean
    )

    fun loadPeserta(initialClassId: String? = null, defaultClassName: String = "") {
        viewModelScope.launch {
            loadPesertaContent(initialClassId, defaultClassName)
        }
    }

    private suspend fun loadPesertaContent(initialClassId: String?, defaultClassName: String) {
        val user = currentSessionUser()
        val isWaliRole = user?.role == Role.WALI
        val teacherRole = isTeacherPortal(user, isWaliRole)
        val initialTeachers = ClassTeachers.mergeWithSessionFallback(emptyList(), user)

        emitLoadingState(teacherRole, initialTeachers, defaultClassName)

        val resolved = resolveClassContext(initialClassId, defaultClassName, teacherRole)
        if (resolved == null) {
            emitNotFoundState(teacherRole, initialTeachers, defaultClassName)
            return
        }

        val resolvedTeachers = resolveClassTeachers(resolved.classId, teacherRole)
        val useLeaderboard = isWaliRole && leaderboardRepository != null
        val rosterResult = loadRosterStudents(resolved.classId, useLeaderboard)
        val effectiveIsTeacher = if (useLeaderboard) false else teacherRole

        emitLoadedState(resolved, effectiveIsTeacher, resolvedTeachers, rosterResult)
    }

    private fun currentSessionUser(): SessionUser? = authRepository?.session?.value?.user

    private fun isTeacherPortal(user: SessionUser?, isWaliRole: Boolean): Boolean =
        user?.role == Role.GURU || (!isWaliRole && classManagementRepository != null)

    private fun emitLoadingState(
        teacherRole: Boolean,
        initialTeachers: List<ClassTeacherSummary>,
        defaultClassName: String
    ) {
        _uiState.value = _uiState.value.copy(
            isLoading = true,
            isTeacher = teacherRole,
            teacherName = ClassTeachers.formatHeadline(initialTeachers),
            teacherAvatarUrl = initialTeachers.firstOrNull()?.avatarUrl,
            teacherRole = ClassTeachers.formatSubtitle(initialTeachers, defaultClassName),
            teacherCount = maxOf(1, initialTeachers.size),
            errorMessage = null
        )
    }

    private fun emitNotFoundState(
        teacherRole: Boolean,
        initialTeachers: List<ClassTeacherSummary>,
        defaultClassName: String
    ) {
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            classId = null,
            className = defaultClassName,
            classArchived = false,
            isTeacher = teacherRole,
            teacherName = ClassTeachers.formatHeadline(initialTeachers),
            teacherAvatarUrl = initialTeachers.firstOrNull()?.avatarUrl,
            teacherRole = ClassTeachers.formatSubtitle(initialTeachers, defaultClassName),
            teacherCount = maxOf(1, initialTeachers.size),
            errorMessage = "Kelas tidak ditemukan"
        )
    }

    private fun emitLoadedState(
        resolved: ResolvedClassContext,
        effectiveIsTeacher: Boolean,
        resolvedTeachers: List<ClassTeacherSummary>,
        rosterResult: ApiResult<List<ClassStudent>>
    ) {
        val teacherName = ClassTeachers.formatHeadline(resolvedTeachers)
        val teacherAvatarUrl = resolvedTeachers.firstOrNull()?.avatarUrl
        val teacherSubtitle = ClassTeachers.formatSubtitle(resolvedTeachers, resolved.className)
        val teacherCount = maxOf(1, resolvedTeachers.size)

        val base = _uiState.value.copy(
            isLoading = false,
            classId = resolved.classId,
            className = resolved.className,
            classArchived = resolved.archived,
            isTeacher = effectiveIsTeacher,
            teacherName = teacherName,
            teacherAvatarUrl = teacherAvatarUrl,
            teacherRole = teacherSubtitle,
            teacherCount = teacherCount
        )
        _uiState.value = when (rosterResult) {
            is ApiResult.Success -> base.copy(students = rosterResult.data, errorMessage = null)
            is ApiResult.Failure -> base.copy(errorMessage = rosterResult.message)
            is ApiResult.NetworkError -> base.copy(errorMessage = "Koneksi internet bermasalah")
        }
    }

    private suspend fun resolveClassContext(
        initialClassId: String?,
        defaultClassName: String,
        teacherRole: Boolean
    ): ResolvedClassContext? {
        var resolvedId = initialClassId
        var resolvedName = defaultClassName
        var resolvedArchived = false

        if (resolvedId.isNullOrBlank()) {
            val childMatch = (classRepository.children() as? ApiResult.Success)?.data?.firstOrNull()
            if (childMatch != null) {
                resolvedId = childMatch.classId
                resolvedName = childMatch.className
                resolvedArchived = childMatch.classArchivedAt != null
            }
        }
        if (resolvedId.isNullOrBlank()) {
            val classMatch = (classRepository.teacherClasses() as? ApiResult.Success)?.data?.firstOrNull()
            if (classMatch != null) {
                resolvedId = classMatch.id
                resolvedName = classMatch.name
                resolvedArchived = classMatch.archived
            }
        }
        val finalId = resolvedId?.takeIf { it.isNotBlank() } ?: return null
        if (resolvedName.isBlank() && authRepository != null) {
            return lookupMissingClassMeta(finalId, teacherRole)
        }
        return ResolvedClassContext(finalId, resolvedName, resolvedArchived)
    }

    private suspend fun lookupMissingClassMeta(classId: String, teacherRole: Boolean): ResolvedClassContext {
        if (teacherRole) {
            val match = (classRepository.teacherClasses() as? ApiResult.Success)?.data?.firstOrNull { it.id == classId }
            if (match != null) return ResolvedClassContext(classId, match.name, match.archived)
        } else {
            val match = (classRepository.children() as? ApiResult.Success)?.data?.firstOrNull { it.classId == classId }
            if (match != null) return ResolvedClassContext(classId, match.className, match.classArchivedAt != null)
        }
        return ResolvedClassContext(classId, "", false)
    }

    private suspend fun loadRosterStudents(classId: String, useLeaderboard: Boolean): ApiResult<List<ClassStudent>> {
        val repo = leaderboardRepository
        if (!useLeaderboard || repo == null) return classRepository.allStudents(classId)
        return when (val lbResult = repo.leaderboard(classId)) {
            is ApiResult.Success -> ApiResult.Success(
                (lbResult.data.podium + lbResult.data.rankings).map { entry ->
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
            )
            is ApiResult.Failure -> lbResult
            is ApiResult.NetworkError -> lbResult
        }
    }

    private suspend fun resolveClassTeachers(classId: String, teacherRole: Boolean): List<ClassTeacherSummary> {
        val sessionUser = authRepository?.session?.value?.user
        val fetched = when {
            teacherRole && authRepository != null && classManagementRepository != null -> {
                when (val res = classManagementRepository.classTeachers(classId)) {
                    is ApiResult.Success -> ClassTeachers.fromClassTeachers(res.data)
                    else -> emptyList()
                }
            }
            !teacherRole && chatRepository != null -> {
                when (val res = chatRepository.allContacts()) {
                    is ApiResult.Success -> ClassTeachers.fromChatContacts(res.data, classId)
                    else -> emptyList()
                }
            }
            else -> emptyList()
        }
        return ClassTeachers.mergeWithSessionFallback(fetched, sessionUser)
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
        private val classManagementRepository: ClassManagementRepository? = null,
        private val chatRepository: ChatRepository? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PesertaKelasViewModel(
                classRepository,
                leaderboardRepository,
                authRepository,
                classManagementRepository,
                chatRepository
            ) as T
    }
}
