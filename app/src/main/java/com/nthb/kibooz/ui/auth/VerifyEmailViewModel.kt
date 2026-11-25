package com.nthb.kibooz.ui.auth

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nthb.kibooz.R
import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.OutageKind
import com.nthb.kibooz.data.remote.VerificationDto
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

/** Dari mana layar kode dibuka. Menentukan catatan di atas isian dan apakah tombol kirim ulang langsung aktif. */
enum class VerifySource {
    /** Pendaftaran berhasil dan email berisi kode sudah dikirim, jadi kirim ulang menunggu hitung mundur. */
    REGISTERED,

    /** Pendaftaran berhasil tetapi server berkata email belum terkirim, jadi kirim ulang langsung aktif. */
    REGISTERED_MAIL_NOT_SENT,

    /** Login ditolak 403 `EMAIL_NOT_VERIFIED`. Kodenya mungkin sudah hilang, jadi kirim ulang langsung aktif. */
    LOGIN
}

data class VerifyEmailState(
    val email: String,
    @StringRes val notice: Int,
    /** True selama kode sedang diperiksa. Tombol terkunci supaya tidak terkirim dua kali. */
    val loading: Boolean = false,
    /** True selama permintaan kode baru berjalan. */
    val resending: Boolean = false,
    /** Sisa detik sebelum kode baru boleh diminta. Nol berarti tombol kirim ulang aktif. */
    val resendSecondsLeft: Int = 0,
    @StringRes val codeError: Int? = null,
    /** Galat di isian kata sandi, termasuk kata sandi yang ditolak server. */
    @StringRes val passwordError: Int? = null,
    /** Galat umum. Panggil [VerifyEmailViewModel.errorShown] setelah ditampilkan. */
    @StringRes val error: Int? = null,
    /** Terisi jika server tidak terjangkau. Layar menampilkan kartu gangguan, bukan [error]. */
    val outage: OutageKind? = null,
    /** Terisi setelah kode diterima. Server sudah memberi sesi, jadi pengguna masuk seperti login. */
    val signedIn: SessionUser? = null,
    /** Server berkata emailnya sudah terverifikasi. Layar memberi tahu pengguna dan membawanya ke login. */
    val alreadyVerified: Boolean = false
) {
    val busy: Boolean get() = loading || resending
}

/**
 * Layar kode verifikasi email untuk akun baru. Kode dan kirim ulang sama-sama membawa kata sandi akun, jadi layar
 * punya isian kata sandi. Kata sandi hanya hidup di isian dan di memori ViewModel ini (untuk coba lagi setelah gangguan),
 * tidak masuk state, Intent, state tersimpan, log atau penyimpanan. [deviceToken] dari balasan register hanya dibawa
 * di memori. Hitung mundur kirim ulang disimpan sebagai tenggat waktu di [cooldown], jadi tetap benar setelah layar
 * diputar. Layar memanggil [tick] tiap detik untuk menyegarkannya.
 */
class VerifyEmailViewModel(
    private val repository: AuthRepository,
    val email: String,
    source: VerifySource,
    resendAfterSeconds: Int = 0,
    private val deviceToken: String? = null,
    private val cooldown: ResendCooldown = ResendCooldown(),
    private val busyRetryMillis: Long = BUSY_RETRY_MILLIS
) : ViewModel() {

    private enum class Action { VERIFY, RESEND }

    private val _state: MutableStateFlow<VerifyEmailState>
    val state: StateFlow<VerifyEmailState>

    // Di luar state supaya tidak ikut ke layar. Dipakai lagi oleh tombol coba lagi di kartu gangguan.
    private var lastCode: String? = null
    private var lastPassword: String? = null
    private var retryAction: Action? = null

    init {
        if (source == VerifySource.REGISTERED) cooldown.start(resendAfterSeconds.toLong())

        _state = MutableStateFlow(
            VerifyEmailState(
                email = email,
                notice = when (source) {
                    VerifySource.REGISTERED -> R.string.verifikasi_info_daftar
                    VerifySource.REGISTERED_MAIL_NOT_SENT -> R.string.verifikasi_info_belum_terkirim
                    VerifySource.LOGIN -> R.string.verifikasi_info_login
                },
                resendSecondsLeft = cooldown.secondsLeft()
            )
        )
        state = _state.asStateFlow()
    }

    override fun onCleared() {
        lastPassword = null
        lastCode = null
    }

    /** Menyegarkan sisa detik dari tenggatnya. Murah dan aman dipanggil tiap detik. */
    fun tick() {
        val left = cooldown.secondsLeft()
        if (left != _state.value.resendSecondsLeft) _state.value = _state.value.copy(resendSecondsLeft = left)
    }

    fun codeEdited() {
        if (_state.value.codeError != null) _state.value = _state.value.copy(codeError = null)
    }

    fun passwordEdited() {
        if (_state.value.passwordError != null) _state.value = _state.value.copy(passwordError = null)
    }

    fun verify(code: String, password: String) {
        val current = _state.value
        if (current.busy || current.signedIn != null || current.alreadyVerified) return

        val digits = code.trim()
        val codeError = R.string.verifikasi_kode_enam_digit.takeIf { !CODE.matches(digits) }
        val passwordError = FormRules.loginPassword(password)
        if (codeError != null || passwordError != null) {
            _state.value = current.copy(codeError = codeError, passwordError = passwordError)
            return
        }

        lastCode = digits
        lastPassword = password
        retryAction = null
        _state.value = current.copy(loading = true, codeError = null, passwordError = null, error = null, outage = null)

        viewModelScope.launch {
            var result = repository.verifyEmail(email, password, digits, deviceToken)
            // Server yang sibuk memeriksa kata sandi menjawab RATE_LIMITED dengan Retry-After 1. Itu bukan batas
            // pengguna, jadi tunggu sebentar dan kirim sekali lagi sebelum menampilkan galat
            if (result.isBusy()) {
                delay(busyRetryMillis)
                result = repository.verifyEmail(email, password, digits, deviceToken)
            }
            _state.value = verified(result)
        }
    }

    /** Meminta kode baru dengan [password] akun. Hanya jalan saat hitung mundurnya selesai. */
    fun resend(password: String) {
        val current = _state.value
        if (current.busy || current.signedIn != null || current.alreadyVerified || cooldown.secondsLeft() > 0) return

        val passwordError = FormRules.loginPassword(password)
        if (passwordError != null) {
            _state.value = current.copy(passwordError = passwordError)
            return
        }

        lastPassword = password
        retryAction = null
        _state.value = current.copy(resending = true, passwordError = null, error = null, outage = null)

        viewModelScope.launch {
            var result = repository.resendVerification(email, password, deviceToken)
            if (result.isBusy()) {
                delay(busyRetryMillis)
                result = repository.resendVerification(email, password, deviceToken)
            }
            _state.value = resent(result)
        }
    }

    /** Tombol coba lagi di kartu gangguan. Mengulang aksi yang gagal dengan kode dan kata sandi yang sama. */
    fun retryAfterOutage() {
        val password = lastPassword ?: return

        when (retryAction) {
            Action.VERIFY -> lastCode?.let { verify(it, password) }
            Action.RESEND -> resend(password)
            null -> Unit
        }
    }

    fun errorShown() {
        _state.value = _state.value.copy(error = null)
    }

    private suspend fun verified(result: ApiResult<SessionUser>): VerifyEmailState {
        val idle = _state.value.copy(loading = false)

        return when (result) {
            is ApiResult.Success -> {
                lastPassword = null
                if (result.data.role == Role.GURU || result.data.role == Role.WALI) {
                    idle.copy(signedIn = result.data)
                } else {
                    // ADMIN belum punya portal di aplikasi, jadi sesinya tidak dipertahankan seperti di login
                    repository.logout()
                    idle.copy(error = R.string.error_forbidden)
                }
            }
            is ApiResult.Failure -> when (result.errorCode) {
                "VERIFICATION_CODE_INVALID" -> idle.copy(codeError = R.string.verifikasi_error_kode_salah)
                "AUTH_INVALID_CREDENTIALS" -> idle.copy(passwordError = R.string.error_password_incorrect)
                "EMAIL_ALREADY_VERIFIED" -> alreadyVerified(idle)
                else -> failed(idle, result, Action.VERIFY)
            }
            is ApiResult.NetworkError -> failed(idle, result, Action.VERIFY)
        }
    }

    private fun resent(result: ApiResult<VerificationDto>): VerifyEmailState {
        val idle = _state.value.copy(resending = false)

        return when (result) {
            is ApiResult.Success -> {
                val info = result.data
                // Kode yang tidak terkirim mengembalikan jedanya, jadi tombol kirim ulang tetap aktif
                if (info.sent) cooldown.start(info.resendAfterSeconds.toLong()) else cooldown.clear()
                idle.copy(
                    notice = if (info.sent) R.string.verifikasi_info_terkirim_ulang else R.string.verifikasi_info_belum_terkirim,
                    resendSecondsLeft = cooldown.secondsLeft(),
                    codeError = null
                )
            }
            is ApiResult.Failure -> when {
                result.errorCode == "AUTH_INVALID_CREDENTIALS" ->
                    idle.copy(passwordError = R.string.error_password_incorrect)
                result.errorCode == "EMAIL_ALREADY_VERIFIED" -> alreadyVerified(idle)
                result.errorCode == "EMAIL_SEND_FAILED" -> {
                    // Surat yang tidak diterima layanan email mengembalikan jedanya, jadi tidak ada hitung mundur
                    cooldown.clear()
                    idle.copy(error = result.errorMessageRes(), resendSecondsLeft = 0)
                }
                result.errorCode == "VERIFICATION_RESEND_TOO_SOON" || result.status == 429 -> {
                    cooldown.start(result.retryAfterSeconds ?: DEFAULT_WAIT_SECONDS)
                    idle.copy(error = result.errorMessageRes(), resendSecondsLeft = cooldown.secondsLeft())
                }
                else -> failed(idle, result, Action.RESEND)
            }
            is ApiResult.NetworkError -> failed(idle, result, Action.RESEND)
        }
    }

    private fun alreadyVerified(base: VerifyEmailState): VerifyEmailState {
        lastPassword = null
        return base.copy(alreadyVerified = true)
    }

    /** Galat umum. Jika server tidak terjangkau, kartu gangguan menawarkan [action] lagi lewat [retryAfterOutage]. */
    private fun failed(base: VerifyEmailState, result: ApiResult<*>, action: Action): VerifyEmailState {
        val outage = result.outageKind()
        retryAction = action.takeIf { outage != null }
        return base.copy(error = result.errorMessageRes(), outage = outage)
    }

    private fun ApiResult<*>.isBusy() =
        this is ApiResult.Failure && errorCode == "RATE_LIMITED" && retryAfterSeconds == 1L

    class Factory(
        private val repository: AuthRepository,
        private val email: String,
        private val source: VerifySource,
        private val resendAfterSeconds: Int,
        private val deviceToken: String?
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            VerifyEmailViewModel(repository, email, source, resendAfterSeconds, deviceToken) as T
    }

    companion object {
        const val CODE_LENGTH = 6
        private val CODE = Regex("^[0-9]{$CODE_LENGTH}$")
        private const val DEFAULT_WAIT_SECONDS = 60L
        private const val BUSY_RETRY_MILLIS = 1_000L
    }
}
