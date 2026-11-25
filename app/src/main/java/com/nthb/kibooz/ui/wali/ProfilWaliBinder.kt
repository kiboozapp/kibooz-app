package com.nthb.kibooz.ui.wali

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nthb.kibooz.R
import com.nthb.kibooz.appContainer
import com.nthb.kibooz.databinding.ActivityProfilWaliBinding
import kotlinx.coroutines.launch

/**
 * Mengisi layar profil Wali dari [WaliProfileViewModel]. Dipisah dari [ProfilWaliActivity] supaya Activity itu
 * hanya berubah sedikit. Nama Wali dan anak yang dipilih berasal dari server, dan tombol di bawah nama membuka
 * profil anak itu, atau form tambah anak jika Wali belum punya anak.
 */
fun AppCompatActivity.bindWaliProfile(binding: ActivityProfilWaliBinding, viewModel: WaliProfileViewModel) {
    binding.btnProfilRetry.setOnClickListener { viewModel.load() }
    binding.connectionStatus.onRetry = { viewModel.load() }
    binding.btnLihatDetail.setOnClickListener {
        val state = viewModel.state.value
        val target = if (state.noChild) TambahAnakActivity::class.java else ProfilSiswaActivity::class.java
        startActivity(Intent(this, target).putExtra(ProfilSiswaActivity.EXTRA_STUDENT_ID, state.profile?.id))
    }

    lifecycleScope.launch {
        repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.state.collect { render(binding, it) }
        }
    }
}

private fun AppCompatActivity.render(binding: ActivityProfilWaliBinding, state: WaliProfileState) {
    val profile = state.profile
    val hasContent = profile != null || state.noChild
    val name = state.parentName
    val selfAvatarUrl = appContainer.authRepository.session.value?.user?.avatarUrl
    appContainer.avatarLoader.load(binding.ivAvatarWali, selfAvatarUrl, R.drawable.img_avatar_guru)

    binding.tvNamaWali.isVisible = name != null
    binding.tvNamaWali.text = name
    binding.tvSubtitleWali.isVisible = hasContent
    binding.tvSubtitleWali.text = if (profile != null) {
        getString(R.string.anak_wali_subjudul, profile.fullName, profile.className)
    } else {
        getString(R.string.anak_wali_subjudul_tanpa_anak)
    }

    binding.btnLihatDetail.isVisible = hasContent
    binding.btnLihatDetail.setText(if (state.noChild) R.string.anak_tambah_anak else R.string.anak_lihat_detail)

    binding.pbProfil.isVisible = state.loading && !hasContent && state.loadError == null

    val blocking = state.loadError != null && !hasContent
    if (blocking && state.outage != null) binding.connectionStatus.show(state.outage) else binding.connectionStatus.hide()
    binding.llProfilError.isVisible = blocking && state.outage == null
    state.loadError?.let { binding.tvProfilError.setText(it) }
}
