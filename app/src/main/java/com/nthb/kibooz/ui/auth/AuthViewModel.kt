package com.nthb.kibooz.ui.auth

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nthb.kibooz.R
import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.OutageKind
import com.nthb.kibooz.data.remote.errorMessageRes
import com.nthb.kibooz.data.remote.outageKind
import com.nthb.kibooz.data.repository.AuthRepository
import com.nthb.kibooz.data.session.Role
import com.nthb.kibooz.data.session.SessionUser
import com.nthb.kibooz.util.FormRules
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LoginUiState(
    val loading: Boolean = false,
    @StringRes val emailError: Int? = null,
    @StringRes val passwordError: Int? = null,
    /** Galat umum dari server atau jaringan. Panggil [AuthViewModel.errorShown] setelah ditampilkan. */
    @StringRes val error: Int? = null,
    /** Terisi jika server tidak terjangkau. Layar menampilkan kartu gangguan, bukan [error], dan pengguna yang mencoba lagi. */
    val outage: OutageKind? = null,
    /** Terisi setelah login berhasil. Peran yang dipakai untuk membuka portal berasal dari sini, bukan dari pilihan di layar. */
    val signedIn: SessionUser? = null
)

class AuthViewModel(
    private val repository: AuthRepository,
    private val busyRetryMillis: Long = BUSY_RETRY_MILLIS
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun login(email: String, password: String, role: Role) {
        if (_state.value.loading) return

        val emailError = FormRules.email(email)
        val passwordError = FormRules.loginPassword(password)
        if (emailError != null || passwordError != null) {
            _state.value = LoginUiState(emailError = emailError, passwordError = passwordError)
            return
        }

        _state.value = LoginUiState(loading = true)
        viewModelScope.launch {
            var result = repository.login(email, password, role)
            // Server yang sibuk memeriksa kata sandi menjawab RATE_LIMITED dengan Retry-After 1. Itu bukan batas
            // pengguna, jadi tunggu sebentar dan kirim sekali lagi sebelum menampilkan galat
            if (result.isBusy()) {
                delay(busyRetryMillis)
                result = repository.login(email, password, role)
            }
            _state.value = result.toState()
        }
    }

    fun errorShown() {
        _state.value = _state.value.copy(error = null)
    }

    private suspend fun ApiResult<SessionUser>.toState(): LoginUiState = when (this) {
        is ApiResult.Success ->
            if (data.role == Role.GURU || data.role == Role.WALI) {
                LoginUiState(signedIn = data)
            } else {
                // ADMIN bisa login tetapi belum punya portal di aplikasi, jadi sesinya tidak dipertahankan
                repository.logout()
                LoginUiState(error = R.string.error_forbidden)
            }
        // Kata sandi tidak disimpan untuk dikirim ulang sendiri, jadi tidak ada hitung mundur: cukup tampilkan kartunya
        else -> LoginUiState(error = errorMessageRes(), outage = outageKind())
    }

    private fun ApiResult<*>.isBusy() =
        this is ApiResult.Failure && errorCode == "RATE_LIMITED" && retryAfterSeconds == 1L

    class Factory(private val repository: AuthRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AuthViewModel(repository) as T
    }

    private companion object {
        const val BUSY_RETRY_MILLIS = 1_000L
    }
}
