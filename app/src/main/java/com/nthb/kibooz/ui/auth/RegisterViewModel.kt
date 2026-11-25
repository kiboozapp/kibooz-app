package com.nthb.kibooz.ui.auth

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nthb.kibooz.R
import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.GoogleRegisterRequest
import com.nthb.kibooz.data.remote.NewClass
import com.nthb.kibooz.data.remote.NewStudent
import com.nthb.kibooz.data.remote.OutageKind
import com.nthb.kibooz.data.remote.RegisterRequest
import com.nthb.kibooz.data.remote.errorMessageRes
import com.nthb.kibooz.data.remote.outageKind
import com.nthb.kibooz.data.repository.AuthRepository
import com.nthb.kibooz.data.repository.RegisterOutcome
import com.nthb.kibooz.data.session.Role
import com.nthb.kibooz.data.session.SessionUser
import com.nthb.kibooz.util.FormRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class RegisterField { FULL_NAME, EMAIL, PASSWORD, PHONE, CLASS_NAME, CLASS_CODE, CHILD_NAME, NISN }

/** Isian layar daftar. Hanya isian milik [role] yang dipakai, sisanya diabaikan. */
data class RegisterForm(
    val role: Role,
    val fullName: String = "",
    val email: String = "",
    val password: String = "",
    val phone: String = "",
    // GURU
    val nip: String = "",
    val schoolName: String = "",
    val className: String = "",
    val gradeLevel: String = "",
    val academicYear: String = "",
    // WALI
    val classCode: String = "",
    val childName: String = "",
    val nisn: String = "",
    // Terisi jika melanjutkan onboarding akun Google tanpa kata sandi
    val googleIdToken: String? = null
) {
    val isGoogleOnboarding: Boolean get() = !googleIdToken.isNullOrBlank()
}

data class RegisterUiState(
    val loading: Boolean = false,
    /** Galat per isian, kuncinya isian yang salah. */
    val fieldErrors: Map<RegisterField, Int> = emptyMap(),
    /** Galat umum dari server atau jaringan. Panggil [RegisterViewModel.errorShown] setelah ditampilkan. */
    @StringRes val error: Int? = null,
    /** Terisi jika server tidak terjangkau. Layar menampilkan kartu gangguan, bukan [error], dan pengguna yang mencoba lagi. */
    val outage: OutageKind? = null,
    /** Terisi setelah pendaftaran berhasil. Server langsung memberi sesi, jadi pengguna sudah masuk. */
    val signedIn: SessionUser? = null,
    /** Terisi jika server mewajibkan verifikasi email. Akunnya ada tetapi belum punya sesi, jadi layar membuka layar kode. */
    val verification: PendingVerification? = null
)

/** Yang dibutuhkan layar kode setelah pendaftaran. Kata sandi sengaja tidak ikut. */
data class PendingVerification(
    val email: String,
    val sent: Boolean,
    val resendAfterSeconds: Int,
    /** Dari balasan register, hanya jatah batas per perangkat. Dibawa ke layar kode lewat Intent. */
    val deviceToken: String?
)

/** Pemeriksaan isian pendaftaran sebelum dikirim. Aturannya sama dengan batas di `openapi.yaml`. */
object RegisterRules {

    private val CLASS_CODE = Regex("^[A-Za-z0-9]{4,12}$")

    fun validate(form: RegisterForm): Map<RegisterField, Int> {
        val errors = linkedMapOf<RegisterField, Int>()

        FormRules.fullName(form.fullName)?.let { errors[RegisterField.FULL_NAME] = it }
        FormRules.email(form.email)?.let { errors[RegisterField.EMAIL] = it }
        if (!form.isGoogleOnboarding) {
            FormRules.newPassword(form.password)?.let { errors[RegisterField.PASSWORD] = it }
        }
        FormRules.phone(form.phone)?.let { errors[RegisterField.PHONE] = it }

        when (form.role) {
            Role.GURU -> FormRules.className(form.className)?.let { errors[RegisterField.CLASS_NAME] = it }
            else -> {
                if (!CLASS_CODE.matches(form.classCode.trim())) errors[RegisterField.CLASS_CODE] = R.string.form_kode_kelas_tidak_valid
                FormRules.fullName(form.childName)?.let { errors[RegisterField.CHILD_NAME] = it }
                FormRules.nisn(form.nisn)?.let { errors[RegisterField.NISN] = it }
            }
        }
        return errors
    }

    /** Isian kosong menjadi null supaya tidak terkirim dan server memakai nilai bawaannya. */
    fun toRequest(form: RegisterForm): RegisterRequest {
        fun String.orNull() = trim().takeIf { it.isNotEmpty() }

        return if (form.role == Role.GURU) {
            RegisterRequest(
                email = form.email.trim(),
                password = form.password,
                fullName = form.fullName.trim(),
                role = Role.GURU,
                phoneNumber = form.phone.orNull(),
                nip = form.nip.orNull(),
                schoolName = form.schoolName.orNull(),
                classInfo = NewClass(form.className.trim(), form.gradeLevel.orNull(), form.academicYear.orNull())
            )
        } else {
            RegisterRequest(
                email = form.email.trim(),
                password = form.password,
                fullName = form.fullName.trim(),
                role = Role.WALI,
                phoneNumber = form.phone.orNull(),
                classCode = form.classCode.trim(),
                student = NewStudent(form.nisn.trim(), form.childName.trim())
            )
        }
    }

    fun toGoogleRequest(form: RegisterForm): GoogleRegisterRequest {
        fun String.orNull() = trim().takeIf { it.isNotEmpty() }
        val token = requireNotNull(form.googleIdToken?.trim()?.takeIf { it.isNotEmpty() })

        return if (form.role == Role.GURU) {
            GoogleRegisterRequest(
                idToken = token,
                fullName = form.fullName.trim(),
                role = Role.GURU,
                phoneNumber = form.phone.orNull(),
                nip = form.nip.orNull(),
                schoolName = form.schoolName.orNull(),
                classInfo = NewClass(form.className.trim(), form.gradeLevel.orNull(), form.academicYear.orNull())
            )
        } else {
            GoogleRegisterRequest(
                idToken = token,
                fullName = form.fullName.trim(),
                role = Role.WALI,
                phoneNumber = form.phone.orNull(),
                classCode = form.classCode.trim(),
                student = NewStudent(form.nisn.trim(), form.childName.trim())
            )
        }
    }
}

class RegisterViewModel(private val repository: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow(RegisterUiState())
    val state: StateFlow<RegisterUiState> = _state.asStateFlow()

    fun register(form: RegisterForm) {
        if (_state.value.loading) return

        val errors = RegisterRules.validate(form)
        if (errors.isNotEmpty()) {
            _state.value = RegisterUiState(fieldErrors = errors)
            return
        }

        _state.value = RegisterUiState(loading = true)
        viewModelScope.launch {
            _state.value = if (form.isGoogleOnboarding) {
                when (val result = repository.googleRegister(RegisterRules.toGoogleRequest(form), emailHint = form.email)) {
                    is ApiResult.Success -> signedInOrDenied(result.data)
                    is ApiResult.Failure -> failed(result)
                    is ApiResult.NetworkError -> RegisterUiState(error = result.errorMessageRes(), outage = result.outageKind())
                }
            } else {
                when (val result = repository.register(RegisterRules.toRequest(form))) {
                    is ApiResult.Success -> when (val outcome = result.data) {
                        is RegisterOutcome.SignedIn -> signedInOrDenied(outcome.user)
                        is RegisterOutcome.VerificationRequired -> RegisterUiState(
                            verification = PendingVerification(
                                outcome.email,
                                outcome.verification.sent,
                                outcome.verification.resendAfterSeconds,
                                outcome.deviceToken
                            )
                        )
                    }
                    is ApiResult.Failure -> failed(result)
                    is ApiResult.NetworkError -> RegisterUiState(error = result.errorMessageRes(), outage = result.outageKind())
                }
            }
        }
    }

    /** Pendaftaran hanya membuat GURU atau WALI, tetapi sesi yang tidak punya portal tidak dipertahankan. */
    private suspend fun signedInOrDenied(user: SessionUser): RegisterUiState =
        if (user.role == Role.GURU || user.role == Role.WALI) {
            RegisterUiState(signedIn = user)
        } else {
            repository.logout()
            RegisterUiState(error = R.string.error_forbidden)
        }

    /** Galat yang jelas milik satu isian ditempelkan di isian itu, sisanya jadi pesan umum. */
    private fun failed(failure: ApiResult.Failure): RegisterUiState {
        val message = failure.errorMessageRes() ?: R.string.error_unknown
        val field = when (failure.errorCode) {
            "EMAIL_ALREADY_REGISTERED" -> RegisterField.EMAIL
            "NISN_ALREADY_REGISTERED" -> RegisterField.NISN
            "CLASS_CODE_NOT_FOUND", "CLASS_JOINING_CLOSED", "CLASS_FULL", "CLASS_NO_ACTIVE_TEACHER" -> RegisterField.CLASS_CODE
            else -> null
        }
        return if (field != null) {
            RegisterUiState(fieldErrors = mapOf(field to message))
        } else {
            // Data pendaftaran tidak disimpan untuk dikirim ulang sendiri, jadi gangguan hanya menampilkan kartu
            RegisterUiState(error = message, outage = failure.outageKind())
        }
    }

    fun errorShown() {
        _state.value = _state.value.copy(error = null)
    }

    class Factory(private val repository: AuthRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = RegisterViewModel(repository) as T
    }
}
