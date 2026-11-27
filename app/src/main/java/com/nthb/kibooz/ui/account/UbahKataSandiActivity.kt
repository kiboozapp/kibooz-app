package com.nthb.kibooz.ui.account

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nthb.kibooz.R
import com.nthb.kibooz.appContainer
import com.nthb.kibooz.ui.theme.KiboozTheme
import kotlinx.coroutines.launch

/**
 * Ubah kata sandi untuk Guru dan Wali (FR-7.1). Setelah berhasil sesi lokal dihapus, lalu
 * [com.nthb.kibooz.ui.auth.SessionGuard] membawa layar ke pilihan peran, jadi layar ini tidak berpindah sendiri.
 * Letaknya di package `account`, bukan `auth`, supaya wajib login seperti layar profil.
 */
class UbahKataSandiActivity : AppCompatActivity() {

    private val viewModel: ChangePasswordViewModel by viewModels { ChangePasswordViewModel.Factory(appContainer.authRepository) }
    private var changedShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KiboozTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                UbahKataSandiScreen(state = state, onBack = ::finish, onSave = viewModel::change)
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::handleEvents)
            }
        }
    }

    /** Menampilkan pesan sekali pakai. Isi layarnya digambar oleh [UbahKataSandiScreen]. */
    private fun handleEvents(state: ChangePasswordState) {
        state.error?.let {
            Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
            viewModel.errorShown()
        }

        if (state.changed && !changedShown) {
            changedShown = true
            Toast.makeText(this, R.string.sandi_diganti, Toast.LENGTH_LONG).show()
        }
    }
}
