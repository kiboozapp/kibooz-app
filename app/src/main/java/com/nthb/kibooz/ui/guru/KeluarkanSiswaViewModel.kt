package com.nthb.kibooz.ui.guru

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nthb.kibooz.R
import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.errorMessageRes
import com.nthb.kibooz.data.repository.ClassMemberRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class KeluarkanSiswaState(
    /** Anak yang sedang dikeluarkan. Tombol keluarkan terkunci sampai server menjawab. */
    val busyStudentId: String? = null,
    /** Server menjawab bahwa kelas sudah diarsipkan, jadi tombol keluarkan disembunyikan. */
    val archived: Boolean = false,
    /** Sisa detik tombol dikunci setelah 429. */
    val cooldownSeconds: Int? = null,
    /** Anak yang sudah tidak ada di kelas dan harus hilang dari daftar. Reset dengan [KeluarkanSiswaViewModel.rosterHandled]. */
    val removedStudentId: String? = null,
    /** True jika daftar peserta harus dimuat ulang. Reset dengan [KeluarkanSiswaViewModel.rosterHandled]. */
    val reload: Boolean = false,
    val notice: KelolaNotice? = null,
    /** Terisi jika layar kelas harus ditutup. */
    val end: ClassScreenEnd? = null
) {
    val locked: Boolean get() = busyStudentId != null || cooldownSeconds != null || end != null
}

/**
 * Mengeluarkan anak dari kelas oleh Guru (`DELETE /guru/classes/{classId}/students/{studentId}`). Daftar pesertanya
 * dipegang [PesertaKelasViewModel], jadi di sini hanya diberitahukan kapan daftar itu harus berubah.
 */
class KeluarkanSiswaViewModel(
    private val repository: ClassMemberRepository,
    private val signOut: () -> Unit,
    pause: suspend (millis: Long) -> Unit = { delay(it) }
) : ViewModel() {

    private val _state = MutableStateFlow(KeluarkanSiswaState())
    val state: StateFlow<KeluarkanSiswaState> = _state.asStateFlow()

    private val cooldown = RateLimitCooldown(viewModelScope, pause) { left ->
        _state.update { it.copy(cooldownSeconds = left) }
    }

    fun remove(classId: String, studentId: String, studentName: String) {
        val snapshot = _state.value
        if (snapshot.locked || snapshot.archived || classId.isBlank()) return

        _state.update { it.copy(busyStudentId = studentId) }
        viewModelScope.launch {
            when (val result = repository.removeStudent(classId, studentId)) {
                is ApiResult.Success -> _state.update {
                    it.copy(
                        busyStudentId = null,
                        removedStudentId = studentId,
                        reload = true,
                        notice = KelolaNotice(R.string.peserta_hasil_dikeluarkan, listOf(studentName))
                    )
                }
                else -> onFailure(studentId, result)
            }
        }
    }

    private fun onFailure(studentId: String, result: ApiResult<*>) {
        if (result is ApiResult.Failure) {
            val end = classScreenEndFor(result.errorCode)
            if (end != null) {
                if (end == ClassScreenEnd.ACCOUNT_GONE) signOut()
                _state.update { it.copy(busyStudentId = null, end = end) }
                return
            }
            if (result.isRateLimited()) {
                val seconds = result.cooldownSeconds()
                cooldown.start(seconds)
                _state.update { it.copy(busyStudentId = null, notice = rateLimitNotice(seconds)) }
                return
            }
        }

        val errorCode = (result as? ApiResult.Failure)?.errorCode
        val gone = errorCode == "STUDENT_NOT_FOUND"
        val message = if (gone) {
            R.string.peserta_sudah_tidak_ada
        } else {
            result.errorMessageRes() ?: R.string.error_unknown
        }
        // Hasil yang tidak diketahui (jaringan atau server) juga memuat ulang, karena anaknya bisa sudah keluar
        _state.update {
            it.copy(
                busyStudentId = null,
                archived = it.archived || errorCode == "CLASS_ARCHIVED",
                removedStudentId = if (gone) studentId else it.removedStudentId,
                reload = true,
                notice = KelolaNotice(message)
            )
        }
    }

    fun rosterHandled() {
        _state.update { it.copy(removedStudentId = null, reload = false) }
    }

    fun noticeShown() {
        _state.update { it.copy(notice = null) }
    }

    class Factory(
        private val repository: ClassMemberRepository,
        private val signOut: () -> Unit
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            KeluarkanSiswaViewModel(repository, signOut) as T
    }
}
