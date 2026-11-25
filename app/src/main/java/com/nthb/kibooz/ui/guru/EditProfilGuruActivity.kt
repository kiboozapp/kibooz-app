package com.nthb.kibooz.ui.guru

import android.app.Activity
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import com.nthb.kibooz.R
import com.nthb.kibooz.appContainer
import com.nthb.kibooz.databinding.ActivityEditProfilGuruBinding
import com.nthb.kibooz.ui.account.AvatarPickerHelper
import com.nthb.kibooz.util.InsetsUtil
import kotlinx.coroutines.launch

/**
 * Layar sunting profil Guru: memperbarui nama lengkap, telepon, dan alamat melalui `PUT /guru/profile` serta
 * mengganti dan memangkas foto profil via `CropAvatarActivity` lalu unggah ke `POST /users/avatar`.
 */
class EditProfilGuruActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditProfilGuruBinding
    private var filledOnce = false
    private var lastAvatarUrl: String? = null

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

    private lateinit var avatarPicker: AvatarPickerHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditProfilGuruBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetsUtil.applySystemBarInsets(
            root = binding.root,
            headerView = binding.clTopBar
        )

        avatarPicker = AvatarPickerHelper(this) { croppedFile ->
            viewModel.uploadAvatar(croppedFile)
        }

        setupListeners()
        observeState()
    }

    private fun setupListeners() {
        binding.ivBack.setOnClickListener { finish() }

        val pickAvatar: () -> Unit = {
            if (!viewModel.state.value.busy) {
                avatarPicker.launchPicker()
            }
        }
        binding.cardAvatar.setOnClickListener { pickAvatar() }
        binding.ivChangePhoto.setOnClickListener { pickAvatar() }
        binding.tvUbahFoto.setOnClickListener { pickAvatar() }

        binding.btnSimpan.setOnClickListener {
            viewModel.saveProfile(
                fullName = binding.etNamaGuru.text.toString(),
                phone = binding.etPhone.text.toString(),
                address = binding.etAlamat.text.toString()
            )
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    val profile = state.profile
                    if (profile != null) {
                        if (!filledOnce) {
                            filledOnce = true
                            binding.etNamaGuru.setText(profile.fullName)
                            binding.etNip.setText(profile.nip?.takeIf { it.isNotBlank() } ?: "-")
                            binding.etEmail.setText(profile.email)
                            binding.etPhone.setText(profile.phoneNumber.orEmpty())
                            binding.etAlamat.setText(profile.address.orEmpty())
                        }
                        if (profile.avatarUrl != lastAvatarUrl) {
                            lastAvatarUrl = profile.avatarUrl
                            appContainer.avatarLoader.load(binding.ivAvatar, profile.avatarUrl, R.drawable.img_avatar_guru)
                        }
                    }

                    binding.btnSimpan.isEnabled = !state.busy && profile != null
                    binding.etNamaGuru.error = state.fieldErrors[GuruEditField.FULL_NAME]?.let(::getString)
                    binding.etPhone.error = state.fieldErrors[GuruEditField.PHONE]?.let(::getString)
                    binding.etAlamat.error = state.fieldErrors[GuruEditField.ADDRESS]?.let(::getString)

                    state.message?.let { resId ->
                        Snackbar.make(binding.root, resId, Snackbar.LENGTH_SHORT).show()
                        viewModel.messageShown()
                    }
                    state.loadError?.let { resId ->
                        if (profile == null) {
                            Snackbar.make(binding.root, resId, Snackbar.LENGTH_LONG)
                                .setAction(R.string.coba_lagi) { viewModel.load() }
                                .show()
                        }
                    }

                    if (state.changed) {
                        setResult(Activity.RESULT_OK)
                    }
                }
            }
        }
    }
}
