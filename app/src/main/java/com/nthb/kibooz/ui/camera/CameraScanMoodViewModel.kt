package com.nthb.kibooz.ui.camera

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nthb.kibooz.R
import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.ClassSummary
import com.nthb.kibooz.data.remote.errorMessageRes
import com.nthb.kibooz.data.repository.ClassRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CameraScanState(
    val loading: Boolean = true,
    /** Kelas yang dipindai. Null selama kelas belum diketahui atau belum dipilih. */
    val classId: String? = null,
    /** Tidak kosong jika Guru mengajar beberapa kelas dan harus memilih satu. */
    val classChoices: List<ClassSummary> = emptyList(),
    val students: List<Student> = emptyList(),
    /** Galat terakhir saat memuat kelas atau siswa. Tetap ada sampai [CameraScanMoodViewModel.retry] berhasil. */
    @StringRes val error: Int? = null
)

/**
 * Menentukan kelas yang dipindai lalu memuat siswanya dari server untuk pemilih siswa di kamera. Kelas dari
 * peluncur dipakai langsung. Tanpa itu daftar kelas Guru diminta: satu kelas dipilih otomatis, beberapa kelas
 * membuat Guru memilih, dan tanpa kelas layar memberi tahu.
 */
class CameraScanMoodViewModel(private val repository: ClassRepository) : ViewModel() {

    private val _state = MutableStateFlow(CameraScanState())
    val state: StateFlow<CameraScanState> = _state.asStateFlow()

    private var started = false
    private var job: Job? = null

    /** Hanya pemanggilan pertama yang berlaku, jadi aman dipanggil dari `onCreate` setiap kali layar dibuat ulang. */
    fun start(initialClassId: String?) {
        if (started) return
        started = true

        if (initialClassId.isNullOrBlank()) loadClasses() else loadStudents(initialClassId)
    }

    fun selectClass(classId: String) {
        _state.value = CameraScanState(loading = true, classId = classId)
        loadStudents(classId)
    }

    /** Mengulang langkah yang gagal: daftar kelas jika kelas belum diketahui, selain itu daftar siswa. */
    fun retry() {
        val classId = _state.value.classId
        _state.value = _state.value.copy(loading = true, error = null)

        if (classId == null) loadClasses() else loadStudents(classId)
    }

    private fun loadClasses() {
        job?.cancel()
        job = viewModelScope.launch {
            when (val result = repository.teacherClasses()) {
                is ApiResult.Success -> when (result.data.size) {
                    0 -> _state.value = CameraScanState(loading = false, error = R.string.kelas_kosong)
                    1 -> selectClass(result.data.single().id)
                    else -> _state.value = CameraScanState(loading = false, classChoices = result.data)
                }
                is ApiResult.Failure -> _state.value = CameraScanState(loading = false, error = result.errorMessageRes())
                is ApiResult.NetworkError -> _state.value = CameraScanState(loading = false, error = result.errorMessageRes())
            }
        }
    }

    private fun loadStudents(classId: String) {
        job?.cancel()
        job = viewModelScope.launch {
            when (val result = repository.allStudents(classId)) {
                is ApiResult.Success -> _state.value = CameraScanState(
                    loading = false,
                    classId = classId,
                    students = result.data.map { Student(it.id, it.fullName) }
                )
                is ApiResult.Failure -> _state.value =
                    CameraScanState(loading = false, classId = classId, error = result.errorMessageRes())
                is ApiResult.NetworkError -> _state.value =
                    CameraScanState(loading = false, classId = classId, error = result.errorMessageRes())
            }
        }
    }

    class Factory(private val repository: ClassRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = CameraScanMoodViewModel(repository) as T
    }
}
