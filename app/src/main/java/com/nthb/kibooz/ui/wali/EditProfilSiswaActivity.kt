package com.nthb.kibooz.ui.wali

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import com.nthb.kibooz.R
import com.nthb.kibooz.appContainer
import com.nthb.kibooz.data.remote.ChildProfile
import com.nthb.kibooz.databinding.ActivityEditProfilSiswaBinding
import com.nthb.kibooz.ui.account.AvatarPickerHelper
import com.nthb.kibooz.ui.location.LocationPickerHelper
import com.nthb.kibooz.util.InsetsUtil
import kotlinx.coroutines.launch

/**
 * Edit data anak (nama dan NISN), data Wali, serta foto profil anak dan Wali. Keduanya punya tombol simpan
 * sendiri, jadi satu yang gagal tidak menahan yang lain. Layar sebelumnya memuat ulang setelah ada yang tersimpan.
 */
class EditProfilSiswaActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditProfilSiswaBinding
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

    /** Isian diisi dari server satu kali, supaya ketikan Wali tidak tertimpa saat data lain disimpan. */
    private var childFilled = false
    private var parentFilled = false
    private var lastChildAvatarUrl: String? = null
    private var lastParentAvatarUrl: String? = null
    private var pendingUploadForChild = true

    private lateinit var avatarPicker: AvatarPickerHelper
    private lateinit var locationPicker: LocationPickerHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditProfilSiswaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetsUtil.applySystemBarInsets(
            root = binding.root,
            headerView = binding.clTopBar
        )

        avatarPicker = AvatarPickerHelper(this) { croppedFile ->
            if (pendingUploadForChild) {
                appContainer.avatarLoader.evict(lastChildAvatarUrl)
            }
            viewModel.uploadAvatar(croppedFile, forChild = pendingUploadForChild)
        }
        locationPicker = LocationPickerHelper(this) { pickedAddress ->
            binding.etAlamat.setText(pickedAddress)
            binding.etAlamat.setSelection(pickedAddress.length)
            binding.tilAlamat.error = null
        }

        setupListeners()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::render)
            }
        }
    }

    private fun setupListeners() {
        binding.ivBack.setOnClickListener { finish() }
        binding.btnRetry.setOnClickListener { viewModel.load() }
        binding.connectionStatus.onRetry = { viewModel.load() }

        val pickChildPhoto: () -> Unit = {
            if (!viewModel.state.value.saving) {
                pendingUploadForChild = true
                avatarPicker.launchPicker()
            }
        }
        val pickParentPhoto: () -> Unit = {
            if (!viewModel.state.value.saving) {
                pendingUploadForChild = false
                avatarPicker.launchPicker()
            }
        }
        binding.cardAvatarAnak.setOnClickListener { pickChildPhoto() }
        binding.btnUbahFotoAnak.setOnClickListener { pickChildPhoto() }
        binding.cardAvatarWali.setOnClickListener { pickParentPhoto() }
        binding.btnUbahFotoWali.setOnClickListener { pickParentPhoto() }

        binding.btnSimpanAnak.setOnClickListener {
            val name = binding.etNamaSiswa.text.toString()
            val nisn = binding.etNisn.text.toString()

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

        binding.btnPilihPeta.setOnClickListener {
            if (!viewModel.state.value.saving) {
                locationPicker.launch(binding.etAlamat.text?.toString())
            }
        }

        binding.btnSimpanWali.setOnClickListener {
            viewModel.saveParent(
                binding.etNamaWali.text.toString(),
                binding.etPhone.text.toString(),
                binding.etWhatsapp.text.toString(),
                binding.etAlamat.text.toString()
            )
        }
    }

    private fun render(state: EditProfilState) {
        val profile = state.profile

        binding.pbLoading.isVisible = state.loading && profile == null
        binding.llForm.isVisible = profile != null

        val blocking = state.loadError != null && profile == null
        if (blocking && state.outage != null) binding.connectionStatus.show(state.outage) else binding.connectionStatus.hide()
        // Anak yang sudah tidak ada tidak bisa dicoba lagi, jadi hanya pesannya yang tampil
        binding.llError.isVisible = blocking && state.outage == null
        state.loadError?.let { binding.tvError.setText(it) }
        binding.btnRetry.isVisible = !state.gone

        if (profile != null) showProfile(profile, state)

        binding.btnSimpanAnak.isEnabled = !state.saving
        binding.btnSimpanWali.isEnabled = !state.saving
        binding.btnUbahFotoAnak.isEnabled = !state.saving
        binding.btnUbahFotoWali.isEnabled = !state.saving
        binding.btnPilihPeta.isEnabled = !state.saving

        val fields: Map<EditField, TextInputLayout> = mapOf(
            EditField.CHILD_NAME to binding.tilNamaAnak,
            EditField.NISN to binding.tilNisn,
            EditField.PARENT_NAME to binding.tilNamaWali,
            EditField.PHONE to binding.tilPhone,
            EditField.WHATSAPP to binding.tilWhatsapp,
            EditField.ADDRESS to binding.tilAlamat
        )
        fields.forEach { (field, layout) -> layout.error = state.fieldErrors[field]?.let { getString(it) } }

        // Anak yang sudah tidak ada tidak bisa disimpan lagi, jadi layar ditutup. Pesannya memakai Toast supaya tetap tampil
        val closing = state.gone && profile != null
        state.message?.let {
            if (closing) Toast.makeText(this, it, Toast.LENGTH_LONG).show() else Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
            viewModel.messageShown()
        }

        // Anak yang sudah tidak ada juga membuat layar sebelumnya memuat ulang, karena dihapus dari perangkat lain
        if (state.changed || state.gone) setResult(Activity.RESULT_OK)
        if (closing) finish()
    }

    private fun showProfile(profile: ChildProfile, state: EditProfilState) {
        if (!childFilled) {
            childFilled = true
            binding.etNamaSiswa.setText(profile.fullName)
            binding.etNisn.setText(profile.nisn)
        }
        if (!parentFilled) {
            parentFilled = true
            val guardian = profile.guardian
            binding.etNamaWali.setText(guardian.fullName)
            binding.etEmail.setText(guardian.email)
            binding.etPhone.setText(guardian.phoneNumber.orEmpty())
            binding.etWhatsapp.setText(guardian.whatsappNumber.orEmpty())
            binding.etAlamat.setText(guardian.address.orEmpty())
        }

        if (profile.avatarUrl != lastChildAvatarUrl || lastChildAvatarUrl == null) {
            lastChildAvatarUrl = profile.avatarUrl
            appContainer.avatarLoader.load(binding.ivAvatarAnak, profile.avatarUrl, R.drawable.img_avatar_siswa)
        }
        val currentParentAvatar = state.parentAvatarUrl ?: appContainer.authRepository.session.value?.user?.avatarUrl
        if (currentParentAvatar != lastParentAvatarUrl || lastParentAvatarUrl == null) {
            lastParentAvatarUrl = currentParentAvatar
            appContainer.avatarLoader.load(binding.ivAvatarWali, currentParentAvatar, R.drawable.img_avatar_guru)
        }

        binding.tvNisnPrompt.isVisible = profile.nisnNeedsUpdate
        binding.tilNisn.helperText = if (profile.nisnVerified) getString(R.string.anak_hint_nisn_terverifikasi) else null
        // Pesan galat NISN menggantikan bantuan selama ada, dan bantuan kembali setelah galat hilang
        if (state.fieldErrors.containsKey(EditField.NISN)) binding.tilNisn.helperText = null
    }
}
