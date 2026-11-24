package com.nthb.kibooz.ui.account

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nthb.kibooz.R
import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.errorMessageRes
import com.nthb.kibooz.data.repository.AuthRepository
import com.nthb.kibooz.util.FormRules
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class PasswordField { CURRENT, NEW, CONFIRM }

data class ChangePasswordState(
    val loading: Boolean = false,
    /** Galat per isian, kuncinya isian yang salah. */
    val fieldErrors: Map<PasswordField, Int> = emptyMap(),
    /** Galat umum dari server atau jaringan. Panggil [ChangePasswordViewModel.errorShown] setelah ditampilkan. */
    @StringRes val error: Int? = null,
    /** Benar setelah server menerima kata sandi baru. Sesi lokal sudah dihapus, jadi pengguna masuk lagi. */
    val changed: Boolean = false
)

/** Pemeriksaan isian ganti kata sandi sebelum dikirim. Aturan kata sandi baru sama dengan pendaftaran. */
object ChangePasswordRules {

    fun validate(current: String, new: String, confirm: String): Map<PasswordField, Int> {
        val errors = linkedMapOf<PasswordField, Int>()

        FormRules.loginPassword(current)?.let { errors[PasswordField.CURRENT] = it }

        val newError = FormRules.newPassword(new) ?: R.string.form_password_sama.takeIf { new == current }
        newError?.let { errors[PasswordField.NEW] = it }

        if (confirm != new) errors[PasswordField.CONFIRM] = R.string.form_password_konfirmasi_beda

        return errors
    }
}

/**
 * Mengganti kata sandi dari layar profil (FR-7.1). Saat berhasil server mengakhiri semua sesi akun, jadi
 * [AuthRepository.changePassword] menghapus sesi lokal dan [com.nthb.kibooz.ui.auth.SessionGuard] membawa
 * layar ke pilihan peran. Kata sandi yang salah dan kata sandi baru yang ditolak ditampilkan di isiannya.
 */
class ChangePasswordViewModel(
    private val repository: AuthRepository,
    private val busyRetryMillis: Long = BUSY_RETRY_MILLIS
) : ViewModel() {

    private val _state = MutableStateFlow(ChangePasswordState())
    val state: StateFlow<ChangePasswordState> = _state.asStateFlow()

    fun change(current: String, new: String, confirm: String) {
        if (_state.value.loading) return

        val errors = ChangePasswordRules.validate(current, new, confirm)
        if (errors.isNotEmpty()) {
            _state.value = ChangePasswordState(fieldErrors = errors)
            return
        }

        _state.value = ChangePasswordState(loading = true)
        viewModelScope.launch {
            var result = repository.changePassword(current, new)
            // Server yang sibuk memeriksa kata sandi menjawab RATE_LIMITED dengan Retry-After 1. Itu bukan batas
            // pengguna, jadi tunggu sebentar dan kirim sekali lagi sebelum menampilkan galat
            if (result.isBusy()) {
                delay(busyRetryMillis)
                result = repository.changePassword(current, new)
            }
            _state.value = result.toState()
        }
    }

    fun errorShown() {
        _state.value = _state.value.copy(error = null)
    }

    private fun ApiResult<Unit>.toState(): ChangePasswordState = when (this) {
        is ApiResult.Success -> ChangePasswordState(changed = true)
        is ApiResult.Failure -> when {
            errorCode == "AUTH_PASSWORD_INCORRECT" ->
                ChangePasswordState(fieldErrors = mapOf(PasswordField.CURRENT to R.string.error_password_incorrect))
            errorCode == "VALIDATION_ERROR" && details?.containsKey("newPassword") == true ->
                ChangePasswordState(fieldErrors = mapOf(PasswordField.NEW to R.string.error_password_baru_ditolak))
            else -> ChangePasswordState(error = errorMessageRes())
        }
        is ApiResult.NetworkError -> ChangePasswordState(error = errorMessageRes())
    }

    private fun ApiResult<*>.isBusy() =
        this is ApiResult.Failure && errorCode == "RATE_LIMITED" && retryAfterSeconds == 1L

    class Factory(private val repository: AuthRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ChangePasswordViewModel(repository) as T
    }

    private companion object {
        const val BUSY_RETRY_MILLIS = 1_000L
    }
}
