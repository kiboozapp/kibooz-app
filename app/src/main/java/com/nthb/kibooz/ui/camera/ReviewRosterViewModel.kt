package com.nthb.kibooz.ui.camera

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nthb.kibooz.R
import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.errorMessageRes
import com.nthb.kibooz.data.repository.ClassRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ReviewRosterState(
    val loading: Boolean = false,
    /** Seluruh siswa kelas, termasuk yang sudah ada di daftar review. Kosong selama belum termuat. */
    val students: List<Student> = emptyList(),
    val loaded: Boolean = false,
    @StringRes val error: Int? = null
)

/**
 * Daftar kelas untuk menambah siswa yang terlewat kamera di layar review. Siswa dipilih dari daftar ini supaya
 * id-nya selalu id dari server, bukan nama yang diketik. Dimuat sekali saat layar dibuka.
 */
class ReviewRosterViewModel(
    private val repository: ClassRepository,
    private val classId: String?
) : ViewModel() {

    private val _state = MutableStateFlow(ReviewRosterState())
    val state: StateFlow<ReviewRosterState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        load()
    }

    fun load() {
        if (classId.isNullOrBlank()) {
            _state.value = ReviewRosterState(error = R.string.review_kelas_belum_dipilih)
            return
        }
        job?.cancel()
        _state.value = _state.value.copy(loading = true, error = null)
        job = viewModelScope.launch {
            _state.value = when (val result = repository.allStudents(classId)) {
                is ApiResult.Success -> ReviewRosterState(students = result.data.map { Student(it.id, it.fullName) }, loaded = true)
                else -> ReviewRosterState(error = result.errorMessageRes())
            }
        }
    }

    fun errorShown() {
        _state.value = _state.value.copy(error = null)
    }

    /** Siswa yang belum ada di daftar review, atau kosong jika semuanya sudah ada. */
    fun addable(alreadyListed: Set<String>): List<Student> = _state.value.students.filter { it.id !in alreadyListed }

    class Factory(private val repository: ClassRepository, private val classId: String?) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ReviewRosterViewModel(repository, classId) as T
    }
}
