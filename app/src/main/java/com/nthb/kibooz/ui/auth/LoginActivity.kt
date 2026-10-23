package com.nthb.kibooz.ui.auth

import android.content.Intent
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nthb.kibooz.R
import com.nthb.kibooz.appContainer
import com.nthb.kibooz.data.session.Role
import com.nthb.kibooz.data.session.SessionUser
import com.nthb.kibooz.databinding.ActivityLoginBinding
import com.nthb.kibooz.ui.guru.DashboardGuruActivity
import com.nthb.kibooz.ui.wali.DashboardWaliActivity
import com.nthb.kibooz.util.InsetsUtil
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private var isPasswordVisible: Boolean = false
    private var selectedRole: Role = Role.WALI
    private val viewModel: AuthViewModel by viewModels { AuthViewModel.Factory(appContainer.authRepository) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetsUtil.applyRootBottomInsets(binding.root)

        // Hanya GURU dan WALI yang punya portal, nilai lain termasuk ADMIN jatuh ke WALI
        selectedRole = if (intent.getStringExtra(EXTRA_SELECTED_ROLE) == Role.GURU.name) Role.GURU else Role.WALI

        // Password visibility toggle
        binding.ivTogglePassword.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            if (isPasswordVisible) {
                binding.etPassword.transformationMethod = HideReturnsTransformationMethod.getInstance()
            } else {
                binding.etPassword.transformationMethod = PasswordTransformationMethod.getInstance()
            }
            binding.etPassword.setSelection(binding.etPassword.text.length)
        }

        // Login button
        binding.btnLogin.setOnClickListener {
            viewModel.login(binding.etEmail.text.toString(), binding.etPassword.text.toString(), selectedRole)
        }

        // Belum ada login Google di API, jadi tombol ini tidak masuk ke mana pun
        binding.btnGoogleLogin.setOnClickListener {
            Toast.makeText(this, R.string.login_google_belum_tersedia, Toast.LENGTH_SHORT).show()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::render)
            }
        }
    }

    private fun render(state: LoginUiState) {
        binding.btnLogin.isEnabled = !state.loading
        binding.etEmail.error = state.emailError?.let { getString(it) }
        binding.etPassword.error = state.passwordError?.let { getString(it) }

        state.error?.let {
            Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
            viewModel.errorShown()
        }
        state.signedIn?.let(::openPortal)
    }

    private fun openPortal(user: SessionUser) {
        // Portal dipilih dari peran di balasan server. Dasbor menjadi layar paling bawah, jadi tombol
        // kembali tidak mengantar ke pilihan peran lagi
        val target = if (user.role == Role.GURU) DashboardGuruActivity::class.java else DashboardWaliActivity::class.java
        startActivity(
            Intent(this, target).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK }
        )
        finish()
    }

    companion object {
        /** Nilai extra adalah nama [Role] seperti yang dikirim API, yaitu GURU atau WALI. */
        const val EXTRA_SELECTED_ROLE = "SELECTED_ROLE"
    }
}
