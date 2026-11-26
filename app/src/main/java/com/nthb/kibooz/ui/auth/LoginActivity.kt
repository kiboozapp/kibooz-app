package com.nthb.kibooz.ui.auth

import android.content.Intent
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nthb.kibooz.BuildConfig
import com.nthb.kibooz.R
import com.nthb.kibooz.appContainer
import com.nthb.kibooz.data.session.Role
import com.nthb.kibooz.data.session.Session
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
        binding.btnLogin.setOnClickListener { submit() }
        binding.connectionStatus.onRetry = ::submit

        binding.btnGoogleLogin.setOnClickListener { launchGoogleSignIn() }
        binding.tvDebugDemoLogin.visibility = if (BuildConfig.DEBUG) View.VISIBLE else View.GONE
        if (BuildConfig.DEBUG) {
            binding.tvDebugDemoLogin.setOnClickListener { enterDebugDemoSession() }
            binding.btnGoogleLogin.setOnLongClickListener {
                enterDebugDemoSession()
                true
            }
        }

        binding.tvToRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java).putExtra(EXTRA_SELECTED_ROLE, selectedRole.name))
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::render)
            }
        }
    }

    private fun launchGoogleSignIn() {
        if (viewModel.state.value.loading) return

        lifecycleScope.launch {
            when (val outcome = GoogleSignInClient().requestIdToken(this@LoginActivity)) {
                is GoogleCredentialOutcome.Success ->
                    viewModel.loginWithGoogle(outcome.idToken, selectedRole, outcome.emailHint)
                GoogleCredentialOutcome.Cancelled -> Unit
                GoogleCredentialOutcome.NoAccountAvailable ->
                    Toast.makeText(this@LoginActivity, R.string.login_google_no_account, Toast.LENGTH_SHORT).show()
                GoogleCredentialOutcome.NotConfigured -> showGoogleNotConfiguredDialog()
                is GoogleCredentialOutcome.Error -> {
                    val detail = outcome.detail?.takeIf { it.isNotBlank() }
                    Toast.makeText(
                        this@LoginActivity,
                        detail ?: getString(R.string.login_google_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun showGoogleNotConfiguredDialog() {
        val builder = AlertDialog.Builder(this)
            .setTitle(R.string.login_google_not_configured_title)
            .setMessage(R.string.login_google_not_configured_message)
            .setPositiveButton(android.R.string.ok, null)
        if (BuildConfig.DEBUG) {
            builder.setNeutralButton(R.string.login_google_demo_debug) { _, _ ->
                enterDebugDemoSession()
            }
        }
        builder.show()
    }

    private fun enterDebugDemoSession() {
        if (!BuildConfig.DEBUG) return

        lifecycleScope.launch {
            val bypassUser = appContainer.authRepository.bypassLogin(selectedRole)
            Toast.makeText(
                this@LoginActivity,
                "Masuk otomatis sebagai ${bypassUser.fullName}",
                Toast.LENGTH_SHORT
            ).show()
            openPortal(bypassUser)
        }
    }

    private fun submit() {
        viewModel.login(binding.etEmail.text.toString(), binding.etPassword.text.toString(), selectedRole)
    }

    private fun render(state: LoginUiState) {
        val enabled = !state.loading
        binding.btnLogin.isEnabled = enabled
        binding.btnGoogleLogin.isEnabled = enabled
        binding.btnGoogleLogin.alpha = if (enabled) 1f else 0.6f
        binding.tvDebugDemoLogin.isEnabled = enabled
        binding.etEmail.error = state.emailError?.let { getString(it) }
        binding.etPassword.error = state.passwordError?.let { getString(it) }

        // Saat server tidak terjangkau, kartu gangguan sudah menjelaskan dan menawarkan coba lagi
        state.outage?.let { binding.connectionStatus.show(it) } ?: binding.connectionStatus.hide()

        state.error?.let {
            if (state.outage == null) Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
            viewModel.errorShown()
        }
        state.googleOnboarding?.let { onboarding ->
            viewModel.googleOnboardingHandled()
            startActivity(
                Intent(this, RegisterActivity::class.java).apply {
                    putExtra(EXTRA_SELECTED_ROLE, onboarding.role.name)
                    putExtra(RegisterActivity.EXTRA_GOOGLE_ID_TOKEN, onboarding.idToken)
                    putExtra(RegisterActivity.EXTRA_GOOGLE_EMAIL, onboarding.profile.email)
                    putExtra(RegisterActivity.EXTRA_GOOGLE_FULL_NAME, onboarding.profile.fullName)
                }
            )
        }
        state.signedIn?.let(::openPortal)
        state.verificationEmail?.let(::openVerification)
    }

    private fun openVerification(email: String) {
        viewModel.verificationOpened()
        startActivity(VerifikasiEmailActivity.intent(this, email, VerifySource.LOGIN, selectedRole))
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
