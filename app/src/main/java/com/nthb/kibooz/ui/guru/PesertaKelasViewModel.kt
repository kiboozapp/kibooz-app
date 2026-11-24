package com.nthb.kibooz.ui.guru

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.ClassStudent
import com.nthb.kibooz.data.repository.ClassRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PesertaUiState(
    val isLoading: Boolean = false,
    val className: String = "",
    val teacherName: String = "Siti Rahayu, S.Pd.",
    val teacherRole: String = "Wali Kelas A & Pendidik Utama",
    val students: List<ClassStudent> = emptyList(),
    val errorMessage: String? = null
)

class PesertaKelasViewModel(
    private val classRepository: ClassRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PesertaUiState(isLoading = true))
    val uiState: StateFlow<PesertaUiState> = _uiState.asStateFlow()

    fun loadPeserta(initialClassId: String? = null, defaultClassName: String = "Kelas A") {
        viewModelScope.launch {
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

            when (val studentsResult = classRepository.allStudents(resolvedClassId)) {
                is ApiResult.Success -> {
                    _uiState.value = PesertaUiState(
                        isLoading = false,
                        className = resolvedClassName,
                        teacherName = teacherName,
                        students = studentsResult.data,
                        errorMessage = null
                    )
                }
                is ApiResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        className = resolvedClassName,
                        teacherName = teacherName,
                        errorMessage = studentsResult.message
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

    class Factory(
        private val classRepository: ClassRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PesertaKelasViewModel(classRepository) as T
    }
}
