package com.nthb.kibooz.ui.wali

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nthb.kibooz.R
import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.ChildProfile
import com.nthb.kibooz.data.remote.OutageKind
import com.nthb.kibooz.data.remote.errorMessageRes
import com.nthb.kibooz.data.remote.outageKind
import com.nthb.kibooz.data.repository.AccountRepository
import com.nthb.kibooz.data.repository.WaliProfileRepository
import com.nthb.kibooz.util.FormRules
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class EditField { CHILD_NAME, NISN, PARENT_NAME, PHONE, WHATSAPP, ADDRESS }

data class EditProfilState(
    val loading: Boolean = true,
    /** Anak yang diedit beserta data Wali. Null selama belum termuat. */
    val profile: ChildProfile? = null,
    @StringRes val loadError: Int? = null,
    val outage: OutageKind? = null,
    val savingChild: Boolean = false,
    val savingParent: Boolean = false,
    val uploadingAvatar: Boolean = false,
    val parentAvatarUrl: String? = null,
    /** Galat per isian, kuncinya isian yang salah. */
    val fieldErrors: Map<EditField, Int> = emptyMap(),
    /** Pesan sekali tampil. Panggil [EditProfilViewModel.messageShown] setelah ditampilkan. */
    @StringRes val message: Int? = null,
    /** True setelah ada yang tersimpan, supaya layar sebelumnya memuat ulang. */
    val changed: Boolean = false,
    /** True jika server menjawab bahwa anak ini sudah tidak ada. Layar ditutup dan layar sebelumnya memuat ulang. */
    val gone: Boolean = false
) {
    val saving: Boolean get() = savingChild || savingParent || uploadingAvatar
}

/** Pemeriksaan isian sebelum dikirim. Aturannya sama dengan `openapi.yaml` dan dengan pola telepon di server. */
object EditProfilRules {

    const val PARENT_NAME_MAX = 150

    /** Pola telepon server, 6 sampai 30 karakter. Teks kosong menghapus nomor, jadi kosong boleh. */
    private val PHONE = Regex("^\\+?\\(?[0-9][0-9 ()-]{4,26}[0-9]$")

    @StringRes
    fun parentName(value: String): Int? {
        val name = value.trim()
        return when {
            name.isEmpty() -> R.string.anak_form_nama_wali_kosong
            name.length > PARENT_NAME_MAX -> R.string.form_nama_terlalu_panjang
            else -> null
        }
    }

    @StringRes
    fun phone(value: String): Int? {
        val phone = value.trim()
        return if (phone.isEmpty() || PHONE.matches(phone)) null else R.string.anak_form_telepon_tidak_valid
    }

    /**
     * NISN yang boleh dikirim untuk anak yang NISN-nya sekarang [current]. NISN kosong hanya boleh jika yang sekarang
     * juga kosong (menunggu diisi), karena server tidak punya cara menghapus NISN.
     */
    @StringRes
    fun nisn(value: String, current: String): Int? {
        val nisn = value.trim()
        return if (nisn.isEmpty() && current.isEmpty()) null else FormRules.nisn(nisn)
    }
}

/**
 * Edit data anak (nama dan NISN) dan data Wali. Keduanya disimpan terpisah, masing-masing dengan satu panggilan,
 * jadi satu yang gagal tidak membatalkan yang lain. Hanya isian yang berubah yang dikirim.
 */
class EditProfilViewModel(
    private val repository: WaliProfileRepository,
    private val studentId: String,
    private val accountRepository: AccountRepository? = null,
    private val onSelfAvatarUpdated: (suspend (String) -> Unit)? = null,
    private val onSelfNameUpdated: (suspend (String) -> Unit)? = null
) : ViewModel() {

    private val _state = MutableStateFlow(EditProfilState())
    val state: StateFlow<EditProfilState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = _state.value.copy(loading = true, loadError = null, outage = null)
        viewModelScope.launch {
            _state.value = when (val result = repository.child(studentId)) {
                is ApiResult.Success -> _state.value.copy(loading = false, profile = result.data)
                else -> _state.value.copy(
                    loading = false,
                    loadError = result.childErrorRes(),
                    outage = result.outageKind(),
                    gone = (result as? ApiResult.Failure)?.errorCode == "STUDENT_NOT_FOUND"
                )
            }
        }
    }

    /**
     * True jika menyimpan [nisn] mengganti NISN yang sudah diverifikasi guru. Server melepas verifikasinya, jadi layar
     * meminta persetujuan Wali lebih dulu.
     */
    fun nisnWillLoseVerification(nisn: String): Boolean {
        val profile = _state.value.profile ?: return false
        val wanted = nisn.trim()
        return profile.nisnVerified && wanted.isNotEmpty() && wanted != profile.nisn
    }

    fun saveChild(name: String, nisn: String) {
        val current = _state.value.profile ?: return
        if (_state.value.saving) return

        val newName = name.trim()
        val newNisn = nisn.trim()

        val errors = linkedMapOf<EditField, Int>()
        FormRules.fullName(newName)?.let { errors[EditField.CHILD_NAME] = it }
        EditProfilRules.nisn(newNisn, current.nisn)?.let { errors[EditField.NISN] = it }
        if (errors.isNotEmpty()) {
            _state.value = _state.value.copy(fieldErrors = errors)
            return
        }

        val nameToSend = newName.takeIf { it != current.fullName }
        val nisnToSend = newNisn.takeIf { it.isNotEmpty() && it != current.nisn }
        if (nameToSend == null && nisnToSend == null) {
            _state.value = _state.value.copy(fieldErrors = emptyMap(), message = R.string.anak_tidak_ada_perubahan)
            return
        }

        _state.value = _state.value.copy(savingChild = true, fieldErrors = emptyMap())
        viewModelScope.launch {
            _state.value = when (val result = repository.updateChild(current.id, nameToSend, nisnToSend)) {
                is ApiResult.Success -> _state.value.copy(
                    savingChild = false,
                    profile = current.copy(
                        fullName = result.data.fullName,
                        nisn = result.data.nisn,
                        nisnVerified = result.data.nisnVerified,
                        nisnNeedsUpdate = result.data.nisnNeedsUpdate
                    ),
                    message = R.string.anak_data_tersimpan,
                    changed = true
                )
                else -> failedSave(result, childSave = true)
            }
        }
    }

    fun saveParent(name: String, phone: String, whatsapp: String, address: String) {
        val current = _state.value.profile ?: return
        if (_state.value.saving) return

        val errors = linkedMapOf<EditField, Int>()
        EditProfilRules.parentName(name)?.let { errors[EditField.PARENT_NAME] = it }
        EditProfilRules.phone(phone)?.let { errors[EditField.PHONE] = it }
        EditProfilRules.phone(whatsapp)?.let { errors[EditField.WHATSAPP] = it }
        FormRules.address(address)?.let { errors[EditField.ADDRESS] = it }
        if (errors.isNotEmpty()) {
            _state.value = _state.value.copy(fieldErrors = errors)
            return
        }

        val guardian = current.guardian
        val nameToSend = name.trim().takeIf { it != guardian.fullName }
        val phoneToSend = phone.trim().takeIf { it != guardian.phoneNumber.orEmpty() }
        val whatsappToSend = whatsapp.trim().takeIf { it != guardian.whatsappNumber.orEmpty() }
        val addressToSend = address.trim().takeIf { it != guardian.address.orEmpty() }
        if (listOf(nameToSend, phoneToSend, whatsappToSend, addressToSend).all { it == null }) {
            _state.value = _state.value.copy(fieldErrors = emptyMap(), message = R.string.anak_tidak_ada_perubahan)
            return
        }

        _state.value = _state.value.copy(savingParent = true, fieldErrors = emptyMap())
        viewModelScope.launch {
            val result = repository.updateProfile(nameToSend, phoneToSend, whatsappToSend, addressToSend)
            if (result is ApiResult.Success && nameToSend != null) {
                onSelfNameUpdated?.invoke(result.data.fullName)
            }
            _state.value = when (result) {
                is ApiResult.Success -> _state.value.copy(
                    savingParent = false,
                    profile = current.copy(guardian = result.data),
                    message = R.string.anak_data_wali_tersimpan,
                    changed = true
                )
                else -> failedSave(result, childSave = false)
            }
        }
    }

    /** Mengunggah foto profil Anak ([forChild] = true) atau foto profil Wali ([forChild] = false). */
    fun uploadAvatar(jpegFile: File, forChild: Boolean) {
        val current = _state.value.profile ?: return
        val repo = accountRepository ?: return
        if (_state.value.saving) return

        _state.value = _state.value.copy(uploadingAvatar = true, message = R.string.avatar_mengunggah)
        viewModelScope.launch {
            val targetStudentId = if (forChild) current.id else null
            when (val res = repo.uploadAvatar(jpegFile, targetStudentId)) {
                is ApiResult.Success -> {
                    val newUrl = res.data
                    if (forChild) {
                        _state.value = _state.value.copy(
                            uploadingAvatar = false,
                            profile = current.copy(avatarUrl = newUrl),
                            message = R.string.avatar_sukses,
                            changed = true
                        )
                    } else {
                        onSelfAvatarUpdated?.invoke(newUrl)
                        _state.value = _state.value.copy(
                            uploadingAvatar = false,
                            parentAvatarUrl = newUrl,
                            message = R.string.avatar_sukses,
                            changed = true
                        )
                    }
                }
                else -> {
                    _state.value = _state.value.copy(
                        uploadingAvatar = false,
                        message = res.errorMessageRes() ?: res.childErrorRes()
                    )
                }
            }
        }
    }

    /** Galat yang jelas milik satu isian ditempelkan di isian itu, sisanya jadi pesan umum. */
    private fun failedSave(result: ApiResult<*>, childSave: Boolean): EditProfilState {
        val base = _state.value.copy(savingChild = false, savingParent = false)
        val failure = result as? ApiResult.Failure

        return when (failure?.errorCode) {
            "NISN_ALREADY_REGISTERED" -> base.copy(fieldErrors = mapOf(EditField.NISN to result.childErrorRes()))
            "STUDENT_NOT_FOUND" -> base.copy(message = result.childErrorRes(), gone = true)
            "VALIDATION_ERROR" -> {
                val fields = failure?.details.orEmpty().keys.mapNotNull { fieldOf(it, childSave) }
                if (fields.isEmpty()) {
                    base.copy(message = R.string.error_validation)
                } else {
                    base.copy(fieldErrors = fields.associateWith(::validationMessage))
                }
            }
            else -> base.copy(message = result.childErrorRes())
        }
    }

    private fun fieldOf(key: String, childSave: Boolean): EditField? = when (key) {
        "fullName" -> if (childSave) EditField.CHILD_NAME else EditField.PARENT_NAME
        "nisn" -> EditField.NISN
        "phoneNumber" -> EditField.PHONE
        "whatsappNumber" -> EditField.WHATSAPP
        "address" -> EditField.ADDRESS
        else -> null
    }

    @StringRes
    private fun validationMessage(field: EditField): Int = when (field) {
        EditField.CHILD_NAME -> R.string.form_nama_terlalu_pendek
        EditField.NISN -> R.string.form_nisn_tidak_valid
        EditField.PARENT_NAME -> R.string.anak_form_nama_wali_kosong
        EditField.PHONE, EditField.WHATSAPP -> R.string.anak_form_telepon_tidak_valid
        EditField.ADDRESS -> R.string.form_alamat_terlalu_panjang
    }

    fun messageShown() {
        _state.value = _state.value.copy(message = null)
    }

    class Factory(
        private val repository: WaliProfileRepository,
        private val studentId: String,
        private val accountRepository: AccountRepository? = null,
        private val onSelfAvatarUpdated: (suspend (String) -> Unit)? = null,
        private val onSelfNameUpdated: (suspend (String) -> Unit)? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            EditProfilViewModel(repository, studentId, accountRepository, onSelfAvatarUpdated, onSelfNameUpdated) as T
    }
}
