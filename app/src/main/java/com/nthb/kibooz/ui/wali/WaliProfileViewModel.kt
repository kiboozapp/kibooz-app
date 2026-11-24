package com.nthb.kibooz.ui.wali

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nthb.kibooz.R
import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.ChildProfile
import com.nthb.kibooz.data.remote.ChildSummary
import com.nthb.kibooz.data.remote.OutageKind
import com.nthb.kibooz.data.remote.outageKind
import com.nthb.kibooz.data.repository.WaliProfileRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class WaliProfileState(
    val loading: Boolean = true,
    /** Semua anak Wali, yang paling lama terdaftar lebih dulu. */
    val children: List<ChildSummary> = emptyList(),
    /** Anak yang dipilih beserta data Wali. Null selama belum termuat. */
    val profile: ChildProfile? = null,
    /** True jika Wali belum punya anak, misalnya setelah menghapus semuanya. */
    val noChild: Boolean = false,
    /** Galat muat. Bertahan sampai pemuatan berikutnya supaya layar bisa menampilkan kartu coba lagi. */
    @StringRes val loadError: Int? = null,
    val outage: OutageKind? = null,
    val deleting: Boolean = false,
    /** Pesan sekali tampil, misalnya hasil hapus. Panggil [WaliProfileViewModel.messageShown] setelah ditampilkan. */
    @StringRes val message: Int? = null,
    /** Nama dari sesi, dipakai selama data Wali belum ada atau Wali belum punya anak. */
    val sessionName: String? = null
) {
    /** Nama Wali dari server jika ada, kalau tidak dari sesi. */
    val parentName: String? get() = profile?.guardian?.fullName ?: sessionName

    /** Menambah anak hanya ditawarkan di bawah batas 10, karena server menolak anak ke-11. */
    val canAddChild: Boolean get() = children.size < MAX_CHILDREN

    /** Pemilih anak hanya berguna jika ada lebih dari satu anak. */
    val canPickChild: Boolean get() = children.size > 1
}

/**
 * Profil Wali dan anak yang dipilih, dipakai layar profil Wali dan layar profil siswa. [initialStudentId] null atau
 * yang sudah tidak ada berarti anak yang paling lama terdaftar. Data lama tetap tampil jika muat ulang gagal.
 * [sessionName] hanya mengisi nama untuk Wali yang belum punya anak, karena server tidak punya rute baca profil Wali.
 */
class WaliProfileViewModel(
    private val repository: WaliProfileRepository,
    initialStudentId: String?,
    private val sessionName: suspend () -> String? = { null }
) : ViewModel() {

    private val _state = MutableStateFlow(WaliProfileState())
    val state: StateFlow<WaliProfileState> = _state.asStateFlow()

    private var selectedId: String? = initialStudentId
    private var job: Job? = null

    init {
        load()
    }

    /** Memuat ulang daftar anak dan anak yang sedang dipilih. */
    fun load() {
        job?.cancel()
        _state.value = _state.value.copy(loading = true, loadError = null, outage = null)
        job = viewModelScope.launch {
            val name = _state.value.sessionName ?: sessionName()
            _state.value = _state.value.copy(sessionName = name)

            val children = when (val result = repository.children()) {
                is ApiResult.Success -> result.data
                else -> {
                    _state.value = failed(result)
                    return@launch
                }
            }

            if (children.isEmpty()) {
                selectedId = null
                _state.value = _state.value.copy(loading = false, children = emptyList(), profile = null, noChild = true)
                return@launch
            }

            val id = selectedId?.takeIf { wanted -> children.any { it.id == wanted } } ?: children.first().id
            selectedId = id
            _state.value = when (val result = repository.child(id)) {
                is ApiResult.Success ->
                    _state.value.copy(loading = false, children = children, profile = result.data, noChild = false)
                else -> failed(result).copy(children = children, noChild = false)
            }
        }
    }

    private fun failed(result: ApiResult<*>): WaliProfileState {
        val current = _state.value
        // Data lama hanya dipegang jika masih anak yang sama, supaya data anak lain tidak tampil di bawah pilihan baru
        val keep = current.profile?.takeIf { it.id == selectedId }

        return current.copy(
            loading = false,
            profile = keep,
            loadError = result.childErrorRes(),
            outage = result.outageKind()
        )
    }

    fun selectChild(childId: String) {
        if (childId == selectedId) return
        selectedId = childId
        _state.value = _state.value.copy(profile = null)
        load()
    }

    /** Menghapus anak yang sedang dipilih. Pemanggil sudah meminta persetujuan Wali. */
    fun deleteSelectedChild() {
        val id = _state.value.profile?.id ?: return
        if (_state.value.deleting) return

        _state.value = _state.value.copy(deleting = true)
        viewModelScope.launch {
            val result = repository.deleteChild(id)
            val gone = result is ApiResult.Success || (result as? ApiResult.Failure)?.errorCode == "STUDENT_NOT_FOUND"

            if (gone) {
                // Anak yang tersisa dimuat ulang, dan yang paling lama terdaftar menjadi pilihan baru
                selectedId = null
                val message = if (result is ApiResult.Success) R.string.anak_dihapus else result.childErrorRes()
                _state.value = _state.value.copy(deleting = false, message = message, profile = null)
                load()
            } else {
                _state.value = _state.value.copy(deleting = false, message = result.childErrorRes())
            }
        }
    }

    fun messageShown() {
        _state.value = _state.value.copy(message = null)
    }

    class Factory(
        private val repository: WaliProfileRepository,
        private val studentId: String?,
        private val sessionName: suspend () -> String? = { null }
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            WaliProfileViewModel(repository, studentId, sessionName) as T
    }
}
