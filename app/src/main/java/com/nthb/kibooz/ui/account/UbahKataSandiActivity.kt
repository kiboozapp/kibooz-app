package com.nthb.kibooz.ui.account

import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nthb.kibooz.R
import com.nthb.kibooz.appContainer
import com.nthb.kibooz.databinding.ActivityUbahKataSandiBinding
import com.nthb.kibooz.util.InsetsUtil
import kotlinx.coroutines.launch

/**
 * Ubah kata sandi untuk Guru dan Wali (FR-7.1). Setelah berhasil sesi lokal dihapus, lalu
 * [com.nthb.kibooz.ui.auth.SessionGuard] membawa layar ke pilihan peran, jadi layar ini tidak berpindah sendiri.
 * Letaknya di package `account`, bukan `auth`, supaya wajib login seperti layar profil.
 */
class UbahKataSandiActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUbahKataSandiBinding
    private val viewModel: ChangePasswordViewModel by viewModels { ChangePasswordViewModel.Factory(appContainer.authRepository) }
    private var changedShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUbahKataSandiBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetsUtil.applySystemBarInsets(
            root = binding.root,
            headerView = binding.clTopBar
        )

        binding.ivBack.setOnClickListener { finish() }
        binding.btnSimpan.setOnClickListener {
            viewModel.change(
                current = binding.etCurrent.text.toString(),
                new = binding.etNew.text.toString(),
                confirm = binding.etConfirm.text.toString()
            )
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::render)
            }
        }
    }

    private fun render(state: ChangePasswordState) {
        binding.btnSimpan.isEnabled = !state.loading
        binding.tilCurrent.error = state.fieldErrors[PasswordField.CURRENT]?.let { getString(it) }
        binding.tilNew.error = state.fieldErrors[PasswordField.NEW]?.let { getString(it) }
        binding.tilConfirm.error = state.fieldErrors[PasswordField.CONFIRM]?.let { getString(it) }

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
