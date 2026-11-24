package com.nthb.kibooz.ui.wali

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nthb.kibooz.R
import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.ChildSummary
import com.nthb.kibooz.data.remote.OutageKind
import com.nthb.kibooz.data.remote.outageKind
import com.nthb.kibooz.data.repository.PendingAddChild
import com.nthb.kibooz.data.repository.WaliProfileRepository
import com.nthb.kibooz.util.ClassCode
import com.nthb.kibooz.util.FormRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AddChildField { CLASS_CODE, CHILD_NAME, NISN }

data class TambahAnakState(
    val loading: Boolean = false,
    /** Galat per isian, kuncinya isian yang salah. */
    val fieldErrors: Map<AddChildField, Int> = emptyMap(),
    /** Galat umum. Panggil [TambahAnakViewModel.errorShown] setelah ditampilkan. */
    @StringRes val error: Int? = null,
    /** Terisi jika server tidak terjangkau. Layar menampilkan kartu gangguan dan Wali yang menekan coba lagi. */
    val outage: OutageKind? = null,
    /** Terisi setelah anak terdaftar. Layar ditutup dan layar sebelumnya memuat ulang. */
    val added: ChildSummary? = null
)

/**
 * Tambah anak dengan kode kelas. Percobaan ulang dengan isian yang sama memakai `Idempotency-Key` yang sama, jadi
 * jawaban yang hilang di jalan tidak mendaftarkan anak dua kali. Isian yang berubah membuat kunci baru.
 */
class TambahAnakViewModel(private val repository: WaliProfileRepository) : ViewModel() {

    private val _state = MutableStateFlow(TambahAnakState())
    val state: StateFlow<TambahAnakState> = _state.asStateFlow()

    private var pending: PendingAddChild? = null

    fun submit(classCode: String, childName: String, nisn: String) {
        if (_state.value.loading) return

        val code = ClassCode.normalize(classCode)
        val errors = linkedMapOf<AddChildField, Int>()
        if (code == null) errors[AddChildField.CLASS_CODE] = R.string.form_kode_kelas_tidak_valid
        FormRules.fullName(childName)?.let { errors[AddChildField.CHILD_NAME] = it }
        FormRules.nisn(nisn)?.let { errors[AddChildField.NISN] = it }
        if (code == null || errors.isNotEmpty()) {
            _state.value = TambahAnakState(fieldErrors = errors)
            return
        }

        val add = pending?.takeIf { it.classCode == code && it.fullName == childName.trim() && it.nisn == nisn.trim() }
            ?: PendingAddChild(code, nisn.trim(), childName.trim())
        pending = add

        _state.value = TambahAnakState(loading = true)
        viewModelScope.launch {
            _state.value = when (val result = repository.addChild(add)) {
                is ApiResult.Success -> {
                    pending = null
                    TambahAnakState(added = result.data)
                }
                is ApiResult.Failure -> failed(result)
                is ApiResult.NetworkError -> TambahAnakState(error = result.childErrorRes(), outage = result.outageKind())
            }
        }
    }

    /** Galat yang jelas milik satu isian ditempelkan di isian itu, sisanya jadi pesan umum. */
    private fun failed(failure: ApiResult.Failure): TambahAnakState {
        val message = failure.childErrorRes()

        return when (failure.errorCode) {
            "NISN_ALREADY_REGISTERED" -> TambahAnakState(fieldErrors = mapOf(AddChildField.NISN to message))
            "CLASS_CODE_NOT_FOUND", "CLASS_JOINING_CLOSED", "CLASS_FULL", "CLASS_NO_ACTIVE_TEACHER" ->
                TambahAnakState(fieldErrors = mapOf(AddChildField.CLASS_CODE to message))
            "VALIDATION_ERROR" -> validationFailed(failure)
            "IDEMPOTENCY_KEY_REUSED" -> {
                // Kuncinya sudah dipakai isian lain, jadi percobaan berikutnya memakai kunci baru
                pending = null
                TambahAnakState(error = message)
            }
            else -> TambahAnakState(error = message, outage = failure.outageKind())
        }
    }

    private fun validationFailed(failure: ApiResult.Failure): TambahAnakState {
        val fields = failure.details.orEmpty().keys.mapNotNull {
            when (it) {
                "classCode" -> AddChildField.CLASS_CODE
                "student.fullName" -> AddChildField.CHILD_NAME
                "student.nisn" -> AddChildField.NISN
                else -> null
            }
        }
        if (fields.isEmpty()) return TambahAnakState(error = R.string.error_validation)

        return TambahAnakState(
            fieldErrors = fields.associateWith {
                when (it) {
                    AddChildField.CLASS_CODE -> R.string.form_kode_kelas_tidak_valid
                    AddChildField.CHILD_NAME -> R.string.form_nama_terlalu_pendek
                    AddChildField.NISN -> R.string.form_nisn_tidak_valid
                }
            }
        )
    }

    fun errorShown() {
        _state.value = _state.value.copy(error = null)
    }

    class Factory(private val repository: WaliProfileRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = TambahAnakViewModel(repository) as T
    }
}
