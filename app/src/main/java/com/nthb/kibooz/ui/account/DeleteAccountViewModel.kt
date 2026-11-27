package com.nthb.kibooz.ui.account

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nthb.kibooz.R
import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.OutageKind
import com.nthb.kibooz.data.remote.errorMessageRes
import com.nthb.kibooz.data.remote.outageKind
import com.nthb.kibooz.data.remote.retryWhenRateLimited
import com.nthb.kibooz.data.repository.AccountRepository
import com.nthb.kibooz.util.FormRules
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DeleteAccountState(
    /** True selama permintaan berjalan, termasuk saat menunggu 429. Tombol terkunci supaya tidak terkirim dua kali. */
    val loading: Boolean = false,
    @StringRes val passwordError: Int? = null,
    @StringRes val successorError: Int? = null,
    /** Server menjawab SUCCESSOR_REQUIRED, jadi isian guru pengganti ditandai wajib. */
    val successorRequired: Boolean = false,
    /** Isian lolos pemeriksaan dan layar menampilkan dialog konfirmasi. */
    val confirming: Boolean = false,
    /** Sisa detik sebelum permintaan dikirim ulang otomatis setelah 429. */
    val retryInSeconds: Int? = null,
    /** Galat umum. Panggil [DeleteAccountViewModel.errorShown] setelah ditampilkan. */
    @StringRes val error: Int? = null,
    /** Terisi jika server tidak terjangkau. Layar menampilkan kartu gangguan dengan tombol coba lagi. */
    val outage: OutageKind? = null,
    /** Terisi jika akun sudah hilang dan sesi sudah dihapus. Isinya pesan yang ditampilkan di layar pilihan peran. */
    @StringRes val finishedMessage: Int? = null
)

/**
 * Hapus akun (permintaan Google Play, jalur hapus akun di dalam aplikasi). Isian diperiksa dulu, lalu layar
 * meminta konfirmasi, baru permintaan dikirim. [isTeacher] menentukan apakah isian guru pengganti dipakai.
 * [signOut] menghapus sesi lokal setelah akun terhapus. Server sudah mencabut semua sesi, jadi permintaan
 * logout di belakangnya boleh gagal.
 */
class DeleteAccountViewModel(
    private val repository: AccountRepository,
    val isTeacher: Boolean,
    private val signOut: () -> Unit,
    private val pause: suspend (millis: Long) -> Unit = { delay(it) }
) : ViewModel() {

    private val _state = MutableStateFlow(DeleteAccountState())
    val state: StateFlow<DeleteAccountState> = _state.asStateFlow()

    // Disimpan di luar state supaya kata sandi tidak ikut terbawa ke layar. Dipakai setelah dialog dikonfirmasi.
    private var pendingPassword: String? = null
    private var pendingSuccessor: String? = null

    /** Memeriksa isian lalu meminta layar menampilkan dialog konfirmasi. Belum ada yang dikirim ke server. */
    fun requestDelete(password: String, successorEmail: String) {
        if (_state.value.loading || _state.value.finishedMessage != null) return

        val passwordError = FormRules.loginPassword(password)
        val successor = successorEmail.trim().takeIf { isTeacher && it.isNotEmpty() }
        val successorError = when {
            !isTeacher -> null
            successor == null -> if (_state.value.successorRequired) R.string.hapus_akun_pengganti_wajib else null
            else -> FormRules.email(successor)
        }

        if (passwordError != null || successorError != null) {
            _state.value = _state.value.copy(
                passwordError = passwordError,
                successorError = successorError,
                confirming = false,
                error = null,
                outage = null
            )
            return
        }

        pendingPassword = password
        pendingSuccessor = successor
        _state.value = _state.value.copy(
            passwordError = null,
            successorError = null,
            confirming = true,
            error = null,
            outage = null
        )
    }

    /** Pengguna menutup dialog tanpa menghapus. */
    fun cancelConfirm() {
        _state.value = _state.value.copy(confirming = false)
    }

    /** Pengguna menekan "Ya" di dialog. */
    fun confirm() {
        val password = pendingPassword ?: return
        val successor = pendingSuccessor
        if (_state.value.loading || _state.value.finishedMessage != null) return

        _state.value = _state.value.copy(
            loading = true,
            confirming = false,
            passwordError = null,
            successorError = null,
            error = null,
            outage = null,
            retryInSeconds = null
        )

        viewModelScope.launch {
            val result = retryWhenRateLimited(
                onWait = { left -> _state.value = _state.value.copy(retryInSeconds = left.takeIf { it > 0 }) },
                pause = pause
            ) { repository.deleteAccount(password, successor) }

            _state.value = finish(result)
        }
    }

    fun errorShown() {
        _state.value = _state.value.copy(error = null)
    }

    private fun finish(result: ApiResult<Unit>): DeleteAccountState {
        val idle = _state.value.copy(loading = false, retryInSeconds = null)

        return when (result) {
            is ApiResult.Success -> {
                pendingPassword = null
                signOut()
                idle.copy(finishedMessage = R.string.hapus_akun_berhasil)
            }
            is ApiResult.NetworkError -> idle.copy(error = result.errorMessageRes(), outage = result.outageKind())
            is ApiResult.Failure -> when (result.errorCode) {
                "AUTH_PASSWORD_INCORRECT" -> idle.copy(passwordError = R.string.error_password_incorrect)
                "SUCCESSOR_REQUIRED" ->
                    idle.copy(successorRequired = true, successorError = R.string.hapus_akun_pengganti_wajib)
                "TEACHER_NOT_FOUND" -> idle.copy(successorError = R.string.hapus_akun_pengganti_tidak_ditemukan)
                "PROFILE_NOT_FOUND" -> {
                    // Akunnya sudah tidak ada di server, jadi sesi di ponsel tinggal dibersihkan
                    pendingPassword = null
                    signOut()
                    idle.copy(finishedMessage = R.string.hapus_akun_sudah_tidak_ada)
                }
                "VALIDATION_ERROR" ->
                    if (result.details?.containsKey("successorEmail") == true) {
                        idle.copy(successorError = R.string.hapus_akun_pengganti_tidak_valid)
                    } else {
                        idle.copy(error = result.errorMessageRes())
                    }
                else -> idle.copy(error = result.errorMessageRes(), outage = result.outageKind())
            }
        }
    }

    class Factory(
        private val repository: AccountRepository,
        private val isTeacher: Boolean,
        private val signOut: () -> Unit
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            DeleteAccountViewModel(repository, isTeacher, signOut) as T
    }
}
