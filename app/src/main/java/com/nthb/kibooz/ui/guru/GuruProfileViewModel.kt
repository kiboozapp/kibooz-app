package com.nthb.kibooz.ui.guru

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nthb.kibooz.R
import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.GuruProfileDetail
import com.nthb.kibooz.data.remote.OutageKind
import com.nthb.kibooz.data.remote.errorMessageRes
import com.nthb.kibooz.data.remote.outageKind
import com.nthb.kibooz.data.repository.AccountRepository
import com.nthb.kibooz.data.repository.GuruProfileRepository
import com.nthb.kibooz.ui.wali.EditProfilRules
import com.nthb.kibooz.util.FormRules
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class GuruEditField { FULL_NAME, PHONE, ADDRESS }

data class GuruProfileState(
    val loading: Boolean = true,
    val profile: GuruProfileDetail? = null,
    @StringRes val loadError: Int? = null,
    val outage: OutageKind? = null,
    val saving: Boolean = false,
    val uploadingAvatar: Boolean = false,
    val fieldErrors: Map<GuruEditField, Int> = emptyMap(),
    @StringRes val message: Int? = null,
    val changed: Boolean = false
) {
    val busy: Boolean get() = saving || uploadingAvatar
}

/**
 * Mengelola profil Guru (`GET /guru/profile/detail`, `PUT /guru/profile`) dan unggah foto (`POST /users/avatar`).
 */
class GuruProfileViewModel(
    private val guruProfileRepository: GuruProfileRepository,
    private val accountRepository: AccountRepository? = null,
    private val onSessionSync: (suspend (fullName: String?, avatarUrl: String?) -> Unit)? = null
) : ViewModel() {

    private val _state = MutableStateFlow(GuruProfileState())
    val state: StateFlow<GuruProfileState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = _state.value.copy(loading = true, loadError = null, outage = null)
        viewModelScope.launch {
            _state.value = when (val res = guruProfileRepository.profileDetail()) {
                is ApiResult.Success -> _state.value.copy(loading = false, profile = res.data)
                else -> _state.value.copy(
                    loading = false,
                    loadError = res.errorMessageRes() ?: R.string.error_unknown,
                    outage = res.outageKind()
                )
            }
        }
    }

    fun saveProfile(fullName: String, phone: String, address: String) {
        val current = _state.value.profile ?: return
        if (_state.value.busy) return

        val errors = linkedMapOf<GuruEditField, Int>()
        EditProfilRules.parentName(fullName)?.let { errors[GuruEditField.FULL_NAME] = it }
        EditProfilRules.phone(phone)?.let { errors[GuruEditField.PHONE] = it }
        FormRules.address(address)?.let { errors[GuruEditField.ADDRESS] = it }
        if (errors.isNotEmpty()) {
            _state.value = _state.value.copy(fieldErrors = errors)
            return
        }

        val nameToSend = fullName.trim().takeIf { it != current.fullName }
        val phoneToSend = phone.trim().takeIf { it != current.phoneNumber.orEmpty() }
        val addressToSend = address.trim().takeIf { it != current.address.orEmpty() }

        if (listOf(nameToSend, phoneToSend, addressToSend).all { it == null }) {
            _state.value = _state.value.copy(fieldErrors = emptyMap(), message = R.string.anak_tidak_ada_perubahan)
            return
        }

        _state.value = _state.value.copy(saving = true, fieldErrors = emptyMap())
        viewModelScope.launch {
            when (val res = guruProfileRepository.updateProfile(nameToSend, phoneToSend, addressToSend)) {
                is ApiResult.Success -> {
                    onSessionSync?.invoke(res.data.fullName, res.data.avatarUrl)
                    _state.value = _state.value.copy(
                        saving = false,
                        profile = res.data,
                        message = R.string.guru_profil_simpan_sukses,
                        changed = true
                    )
                }
                else -> {
                    _state.value = _state.value.copy(
                        saving = false,
                        message = res.errorMessageRes() ?: R.string.error_unknown
                    )
                }
            }
        }
    }

    fun uploadAvatar(jpegFile: File) {
        val repo = accountRepository ?: return
        if (_state.value.busy) return

        _state.value = _state.value.copy(uploadingAvatar = true, message = R.string.avatar_mengunggah)
        viewModelScope.launch {
            when (val res = repo.uploadAvatar(jpegFile, studentId = null)) {
                is ApiResult.Success -> {
                    onSessionSync?.invoke(null, res.data)
                    val updatedProfile = _state.value.profile?.copy(avatarUrl = res.data)
                    _state.value = _state.value.copy(
                        uploadingAvatar = false,
                        profile = updatedProfile,
                        message = R.string.avatar_sukses,
                        changed = true
                    )
                }
                else -> {
                    _state.value = _state.value.copy(
                        uploadingAvatar = false,
                        message = res.errorMessageRes() ?: R.string.error_unknown
                    )
                }
            }
        }
    }

    fun messageShown() {
        _state.value = _state.value.copy(message = null)
    }

    class Factory(
        private val guruProfileRepository: GuruProfileRepository,
        private val accountRepository: AccountRepository? = null,
        private val onSessionSync: (suspend (String?, String?) -> Unit)? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            GuruProfileViewModel(guruProfileRepository, accountRepository, onSessionSync) as T
    }
}
