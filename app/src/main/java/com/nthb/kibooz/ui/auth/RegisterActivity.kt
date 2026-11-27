package com.nthb.kibooz.ui.auth

import android.content.Intent
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
import com.nthb.kibooz.appContainer
import com.nthb.kibooz.data.session.Role
import com.nthb.kibooz.data.session.SessionUser
import com.nthb.kibooz.ui.guru.DashboardGuruActivity
import com.nthb.kibooz.ui.theme.KiboozTheme
import com.nthb.kibooz.ui.wali.DashboardWaliActivity
import kotlinx.coroutines.launch

/** Pendaftaran Guru (dengan kelas pertamanya) atau Wali Murid (dengan kode kelas dan data anak). */
class RegisterActivity : AppCompatActivity() {

    private val viewModel: RegisterViewModel by viewModels { RegisterViewModel.Factory(appContainer.authRepository) }
    private var role: Role = Role.WALI

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Hanya GURU dan WALI yang bisa mendaftar, nilai lain jatuh ke WALI
        role = if (intent.getStringExtra(LoginActivity.EXTRA_SELECTED_ROLE) == Role.GURU.name) Role.GURU else Role.WALI
        val google = intent.getStringExtra(EXTRA_GOOGLE_ID_TOKEN)?.takeIf { it.isNotBlank() }?.let { idToken ->
            GooglePrefill(
                idToken = idToken,
                email = intent.getStringExtra(EXTRA_GOOGLE_EMAIL).orEmpty(),
                fullName = intent.getStringExtra(EXTRA_GOOGLE_FULL_NAME).orEmpty()
            )
        }

        setContent {
            KiboozTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                RegisterScreen(
                    role = role,
                    state = state,
                    google = google,
                    onRegister = viewModel::register,
                    onLogin = ::finish
                )
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::handleEvents)
            }
        }
    }

    /** Menjalankan kejadian sekali pakai. Isi layarnya digambar oleh [RegisterScreen]. */
    private fun handleEvents(state: RegisterUiState) {
        // Saat server tidak terjangkau, kartu gangguan sudah menjelaskan dan menawarkan coba lagi
        state.error?.let {
            if (state.outage == null) Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
            viewModel.errorShown()
        }
        state.signedIn?.let(::openPortal)
        state.verification?.let(::openVerification)
    }

    private fun openVerification(pending: PendingVerification) {
        // Layar daftar ditutup supaya tombol kembali dari layar kode mengantar ke login, bukan ke formulir yang sudah terpakai
        val source = if (pending.sent) VerifySource.REGISTERED else VerifySource.REGISTERED_MAIL_NOT_SENT
        startActivity(VerifikasiEmailActivity.intent(this, pending.email, source, role, pending.resendAfterSeconds, pending.deviceToken))
        finish()
    }

    private fun openPortal(user: SessionUser) {
        // Sama dengan login: dasbor menjadi layar paling bawah, tombol kembali tidak mengantar ke formulir lagi
        val target = if (user.role == Role.GURU) DashboardGuruActivity::class.java else DashboardWaliActivity::class.java
        startActivity(
            Intent(this, target).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK }
        )
        finish()
    }

    companion object {
        const val EXTRA_GOOGLE_ID_TOKEN = "GOOGLE_ID_TOKEN"
        const val EXTRA_GOOGLE_EMAIL = "GOOGLE_EMAIL"
        const val EXTRA_GOOGLE_FULL_NAME = "GOOGLE_FULL_NAME"
    }
}
