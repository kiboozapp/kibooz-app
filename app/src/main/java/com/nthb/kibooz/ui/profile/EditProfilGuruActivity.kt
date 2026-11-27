package com.nthb.kibooz.ui.profile

import android.app.Activity
import android.os.Bundle
import android.view.View
import androidx.activity.compose.setContent
import androidx.activity.viewModels
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
import com.nthb.kibooz.ui.guru.GuruProfileState
import com.nthb.kibooz.ui.guru.GuruProfileViewModel
import com.nthb.kibooz.ui.location.LocationPickerHelper
import com.nthb.kibooz.ui.theme.KiboozTheme
import kotlinx.coroutines.launch

/**
 * Layar sunting profil Guru: memperbarui nama lengkap, telepon, dan alamat melalui `PUT /guru/profile` serta
 * mengganti dan memangkas foto profil via `CropAvatarActivity` lalu unggah ke `POST /users/avatar`.
 * Isi layarnya digambar oleh [EditProfilGuruScreen]. Kelas ini memegang pemilih foto dan peta, serta pesan sekali pakai.
 */
class EditProfilGuruActivity : AppCompatActivity() {

    private var lastAvatarUrl: String? = null
    private var pickedAddress by mutableStateOf<String?>(null)

    private val viewModel: GuruProfileViewModel by viewModels {
        GuruProfileViewModel.Factory(
            guruProfileRepository = appContainer.guruProfileRepository,
            accountRepository = appContainer.accountRepository,
            onSessionSync = { name, avatar ->
                if (!avatar.isNullOrBlank()) {
                    appContainer.avatarLoader.evict(lastAvatarUrl)
                }
                appContainer.authRepository.updateSessionUser(fullName = name, avatarUrl = avatar)
            }
        )
    }

    private val avatarPicker = AvatarPickerHelper(this) { croppedFile -> viewModel.uploadAvatar(croppedFile) }
    private val locationPicker = LocationPickerHelper(this) { address -> pickedAddress = address }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KiboozTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                EditProfilGuruScreen(
                    state = state,
                    avatarLoader = appContainer.avatarLoader,
                    pickedAddress = pickedAddress,
                    onAddressConsumed = { pickedAddress = null },
                    onBack = ::finish,
                    onPickAvatar = {
                        if (!viewModel.state.value.busy) avatarPicker.launchPicker()
                    },
                    onPickMap = { currentAddress ->
                        if (!viewModel.state.value.busy) locationPicker.launch(currentAddress)
                    },
                    onSave = viewModel::saveProfile
                )
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::handleEvents)
            }
        }
    }

    /** Menampilkan pesan sekali pakai dan mengabari layar pemanggil. */
    private fun handleEvents(state: GuruProfileState) {
        val profile = state.profile
        if (profile != null && profile.avatarUrl != lastAvatarUrl) {
            lastAvatarUrl = profile.avatarUrl
        }

        val root = findViewById<View>(android.R.id.content)
        state.message?.let { resId ->
            Snackbar.make(root, resId, Snackbar.LENGTH_SHORT).show()
            viewModel.messageShown()
        }
        state.loadError?.let { resId ->
            if (profile == null) {
                Snackbar.make(root, resId, Snackbar.LENGTH_LONG)
                    .setAction(R.string.coba_lagi) { viewModel.load() }
                    .show()
            }
        }

        if (state.changed) {
            setResult(Activity.RESULT_OK)
        }
    }
}
