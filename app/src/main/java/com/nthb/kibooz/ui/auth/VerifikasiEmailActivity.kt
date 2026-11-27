package com.nthb.kibooz.ui.auth

import android.content.Context
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
import com.nthb.kibooz.R
import com.nthb.kibooz.appContainer
import com.nthb.kibooz.data.session.Role
import com.nthb.kibooz.data.session.SessionUser
import com.nthb.kibooz.ui.guru.DashboardGuruActivity
import com.nthb.kibooz.ui.theme.KiboozTheme
import com.nthb.kibooz.ui.wali.DashboardWaliActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Kode verifikasi email untuk akun baru, dibuka setelah pendaftaran atau setelah login ditolak 403
 * `EMAIL_NOT_VERIFIED`. Tidak ada jalan untuk melewati kodenya. Kode dan kirim ulang sama-sama butuh kata sandi akun,
 * jadi ada isian kata sandi. Isian itu tidak ikut state tersimpan, dan kata sandi tidak pernah masuk ke Intent, log atau
 * penyimpanan, jadi setelah proses dimatikan sistem pengguna mengetiknya lagi. Letaknya di package `auth` supaya
 * terbuka tanpa sesi. Email dan `deviceToken` (bukan kredensial, hanya jatah batas per perangkat) dibawa Intent.
 */
class VerifikasiEmailActivity : AppCompatActivity() {

    private var freshStart = true
    private var leaving = false

    private val viewModel: VerifyEmailViewModel by viewModels {
        val source = runCatching { VerifySource.valueOf(intent.getStringExtra(EXTRA_SOURCE).orEmpty()) }
            .getOrDefault(VerifySource.LOGIN)
        // Setelah proses dimatikan sistem hitung mundurnya mulai dari nol, dan itu hanya mengaktifkan kirim ulang
        val wait = if (freshStart) intent.getIntExtra(EXTRA_RESEND_AFTER, 0) else 0
        VerifyEmailViewModel.Factory(
            appContainer.authRepository,
            intent.getStringExtra(EXTRA_EMAIL).orEmpty(),
            source,
            wait,
            intent.getStringExtra(EXTRA_DEVICE_TOKEN)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        freshStart = savedInstanceState == null

        if (intent.getStringExtra(EXTRA_EMAIL).isNullOrBlank()) {
            finish()
            return
        }

        setContent {
            KiboozTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                VerifikasiEmailScreen(
                    state = state,
                    onBack = ::finish,
                    onCodeEdited = viewModel::codeEdited,
                    onPasswordEdited = viewModel::passwordEdited,
                    onVerify = viewModel::verify,
                    onResend = viewModel::resend,
                    onRetry = viewModel::retryAfterOutage
                )
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect(::handleEvents) }
                // Menyegarkan hitung mundur dari tenggatnya, jadi waktu di latar belakang ikut terhitung
                launch {
                    while (true) {
                        viewModel.tick()
                        delay(TICK_MILLIS)
                    }
                }
            }
        }
    }

    /** Menjalankan kejadian sekali pakai. Isi layarnya digambar oleh [VerifikasiEmailScreen]. */
    private fun handleEvents(state: VerifyEmailState) {
        // Saat server tidak terjangkau, kartu gangguan sudah menjelaskan dan menawarkan coba lagi
        state.error?.let {
            if (state.outage == null) Toast.makeText(this, it, Toast.LENGTH_LONG).show()
            viewModel.errorShown()
        }

        state.signedIn?.let(::openPortal)
        if (state.alreadyVerified) openLogin()
    }

    private fun openPortal(user: SessionUser) {
        if (leaving) return
        leaving = true

        // Sama dengan login: dasbor menjadi layar paling bawah, tombol kembali tidak mengantar ke formulir lagi
        val target = if (user.role == Role.GURU) DashboardGuruActivity::class.java else DashboardWaliActivity::class.java
        startActivity(
            Intent(this, target).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK }
        )
        finish()
    }

    private fun openLogin() {
        if (leaving) return
        leaving = true

        // Toast milik aplikasi, jadi tetap tampil setelah layar ini ditutup
        Toast.makeText(applicationContext, R.string.verifikasi_sudah_terverifikasi, Toast.LENGTH_LONG).show()
        // Layar login yang ada di bawah dipakai lagi, layar daftar dan layar ini ikut tertutup
        startActivity(
            Intent(this, LoginActivity::class.java)
                .putExtra(LoginActivity.EXTRA_SELECTED_ROLE, intent.getStringExtra(LoginActivity.EXTRA_SELECTED_ROLE))
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        finish()
    }

    companion object {
        private const val EXTRA_EMAIL = "VERIFY_EMAIL"
        private const val EXTRA_SOURCE = "VERIFY_SOURCE"
        private const val EXTRA_RESEND_AFTER = "VERIFY_RESEND_AFTER_SECONDS"
        private const val EXTRA_DEVICE_TOKEN = "VERIFY_DEVICE_TOKEN"
        private const val TICK_MILLIS = 1_000L

        /**
         * [role] dipakai agar layar login yang dibuka lagi tahu peran yang dipilih. [resendAfterSeconds] hanya
         * dipakai untuk [VerifySource.REGISTERED]. [deviceToken] dari balasan register boleh ikut (hanya jatah
         * batas per perangkat, bukan kredensial). Kata sandi tidak boleh ikut di sini.
         */
        fun intent(
            context: Context,
            email: String,
            source: VerifySource,
            role: Role,
            resendAfterSeconds: Int = 0,
            deviceToken: String? = null
        ): Intent = Intent(context, VerifikasiEmailActivity::class.java)
            .putExtra(EXTRA_EMAIL, email)
            .putExtra(EXTRA_SOURCE, source.name)
            .putExtra(EXTRA_RESEND_AFTER, resendAfterSeconds)
            .putExtra(EXTRA_DEVICE_TOKEN, deviceToken)
            .putExtra(LoginActivity.EXTRA_SELECTED_ROLE, role.name)
    }
}
