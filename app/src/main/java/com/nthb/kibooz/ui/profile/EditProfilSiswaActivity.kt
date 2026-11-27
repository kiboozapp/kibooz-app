package com.nthb.kibooz.ui.profile

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import com.nthb.kibooz.R
import com.nthb.kibooz.appContainer
import com.nthb.kibooz.ui.account.AvatarPickerHelper
import com.nthb.kibooz.ui.location.LocationPickerHelper
import com.nthb.kibooz.ui.theme.KiboozTheme
import com.nthb.kibooz.ui.wali.EditProfilState
import com.nthb.kibooz.ui.wali.EditProfilViewModel
import kotlinx.coroutines.launch

/**
 * Edit data anak (nama dan NISN), data Wali, serta foto profil anak dan Wali. Keduanya punya tombol simpan
 * sendiri, jadi satu yang gagal tidak menahan yang lain. Layar sebelumnya memuat ulang setelah ada yang tersimpan.
 * Isi layarnya digambar oleh [EditProfilSiswaScreen]. Kelas ini memegang pemilih foto dan peta, dialog, dan pesan sekali pakai.
 */
class EditProfilSiswaActivity : AppCompatActivity() {

    private val viewModel: EditProfilViewModel by viewModels {
        EditProfilViewModel.Factory(
            repository = appContainer.waliProfileRepository,
            studentId = intent.getStringExtra(ProfilSiswaActivity.EXTRA_STUDENT_ID).orEmpty(),
            accountRepository = appContainer.accountRepository,
            onSelfAvatarUpdated = { newUrl ->
                appContainer.avatarLoader.evict(lastParentAvatarUrl)
                appContainer.authRepository.updateSessionUser(avatarUrl = newUrl)
            },
            onSelfNameUpdated = { newName ->
                appContainer.authRepository.updateSessionUser(fullName = newName)
            }
        )
    }

    private var lastChildAvatarUrl: String? = null
    private var lastParentAvatarUrl: String? = null
    private var pendingUploadForChild = true
    private var pickedAddress by mutableStateOf<String?>(null)

    private val avatarPicker = AvatarPickerHelper(this) { croppedFile ->
        if (pendingUploadForChild) {
            appContainer.avatarLoader.evict(lastChildAvatarUrl)
        }
        viewModel.uploadAvatar(croppedFile, forChild = pendingUploadForChild)
    }
    private val locationPicker = LocationPickerHelper(this) { address -> pickedAddress = address }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KiboozTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                EditProfilSiswaScreen(
                    state = state,
                    parentAvatarUrl = parentAvatarUrl(state),
                    avatarLoader = appContainer.avatarLoader,
                    pickedAddress = pickedAddress,
                    onAddressConsumed = { pickedAddress = null },
                    onBack = ::finish,
                    onRetry = viewModel::load,
                    onPickChildPhoto = { pickPhoto(forChild = true) },
                    onPickParentPhoto = { pickPhoto(forChild = false) },
                    onPickMap = { currentAddress ->
                        if (!viewModel.state.value.saving) locationPicker.launch(currentAddress)
                    },
                    onSaveChild = ::saveChild,
                    onSaveParent = viewModel::saveParent
                )
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::handleEvents)
            }
        }
    }

    /** Foto Wali dari server bila sudah ada, kalau belum dari sesi. */
    private fun parentAvatarUrl(state: EditProfilState): String? =
        state.parentAvatarUrl ?: appContainer.authRepository.session.value?.user?.avatarUrl

    private fun pickPhoto(forChild: Boolean) {
        if (viewModel.state.value.saving) return
        pendingUploadForChild = forChild
        avatarPicker.launchPicker()
    }

    /** NISN yang sudah diverifikasi guru kehilangan verifikasinya bila diganti, jadi Wali diminta memastikan. */
    private fun saveChild(name: String, nisn: String) {
        if (viewModel.nisnWillLoseVerification(nisn)) {
            AlertDialog.Builder(this)
                .setTitle(R.string.anak_nisn_ganti_judul)
                .setMessage(R.string.anak_nisn_ganti_pesan)
                .setPositiveButton(R.string.anak_nisn_ganti_ya) { _, _ -> viewModel.saveChild(name, nisn) }
                .setNegativeButton(R.string.batal, null)
                .show()
        } else {
            viewModel.saveChild(name, nisn)
        }
    }

    private fun handleEvents(state: EditProfilState) {
        val profile = state.profile
        if (profile != null) {
            lastChildAvatarUrl = profile.avatarUrl
            lastParentAvatarUrl = parentAvatarUrl(state)
        }

        // Anak yang sudah tidak ada tidak bisa disimpan lagi, jadi layar ditutup. Pesannya memakai Toast supaya tetap tampil
        val closing = state.gone && profile != null
        state.message?.let {
            if (closing) {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
            } else {
                Snackbar.make(findViewById<View>(android.R.id.content), it, Snackbar.LENGTH_LONG).show()
            }
            viewModel.messageShown()
        }

        // Anak yang sudah tidak ada juga membuat layar sebelumnya memuat ulang, karena dihapus dari perangkat lain
        if (state.changed || state.gone) setResult(Activity.RESULT_OK)
        if (closing) finish()
    }
}
