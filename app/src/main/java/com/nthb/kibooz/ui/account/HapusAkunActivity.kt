package com.nthb.kibooz.ui.account

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nthb.kibooz.R
import com.nthb.kibooz.appContainer
import com.nthb.kibooz.sessionManager
import com.nthb.kibooz.ui.auth.RoleSelectionActivity
import com.nthb.kibooz.ui.theme.KiboozTheme
import kotlinx.coroutines.launch

/**
 * Hapus akun, dibuka dari layar profil Guru dan Wali. Meminta kata sandi, untuk Guru juga email guru pengganti,
 * lalu dialog konfirmasi sebelum permintaan dikirim. Setelah akun terhapus, pengguna dibawa ke pilihan peran
 * dengan tumpukan layar kosong.
 */
class HapusAkunActivity : AppCompatActivity() {

    private var dialog: AlertDialog? = null

    /** Email guru pengganti. Disimpan di sini karena dialog konfirmasi menampilkannya. */
    private var successor by mutableStateOf("")

    private val viewModel: DeleteAccountViewModel by viewModels {
        DeleteAccountViewModel.Factory(
            appContainer.accountRepository,
            intent.getBooleanExtra(EXTRA_IS_TEACHER, false),
            sessionManager::signOut
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        successor = savedInstanceState?.getString(STATE_SUCCESSOR).orEmpty()

        setContent {
            KiboozTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                HapusAkunScreen(
                    isTeacher = viewModel.isTeacher,
                    state = state,
                    successor = successor,
                    onSuccessorChange = { successor = it },
                    onBack = ::finish,
                    onDelete = { password -> viewModel.requestDelete(password, successor) },
                    // Kata sandi dan pengganti sudah diperiksa dan dikonfirmasi, jadi coba lagi langsung mengirim ulang
                    onRetry = viewModel::confirm
                )
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::handleEvents)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_SUCCESSOR, successor)
    }

    override fun onDestroy() {
        dialog?.dismiss()
        dialog = null
        super.onDestroy()
    }

    /** Menampilkan pesan, dialog dan perpindahan layar. Isi layarnya digambar oleh [HapusAkunScreen]. */
    private fun handleEvents(state: DeleteAccountState) {
        // Saat server tidak terjangkau, kartu gangguan sudah menjelaskan dan menawarkan coba lagi
        state.error?.let {
            if (state.outage == null) Toast.makeText(this, it, Toast.LENGTH_LONG).show()
            viewModel.errorShown()
        }

        if (state.confirming) showConfirmation() else dismissConfirmation()
        state.finishedMessage?.let(::openRoleSelection)
    }

    private fun showConfirmation() {
        if (dialog?.isShowing == true) return

        val successor = successor.trim()
        val message = buildString {
            append(getString(if (viewModel.isTeacher) R.string.hapus_akun_dialog_pesan_guru else R.string.hapus_akun_dialog_pesan_wali))
            if (viewModel.isTeacher) {
                append("\n\n")
                append(
                    if (successor.isEmpty()) getString(R.string.hapus_akun_dialog_tanpa_pengganti)
                    else getString(R.string.hapus_akun_dialog_dengan_pengganti, successor)
                )
            }
        }

        dialog = AlertDialog.Builder(this)
            .setTitle(R.string.hapus_akun_dialog_judul)
            .setMessage(message)
            .setPositiveButton(R.string.hapus_akun_dialog_ya) { _, _ -> viewModel.confirm() }
            .setNegativeButton(R.string.hapus_akun_dialog_batal) { _, _ -> viewModel.cancelConfirm() }
            .setOnCancelListener { viewModel.cancelConfirm() }
            .show()
    }

    private fun dismissConfirmation() {
        dialog?.dismiss()
        dialog = null
    }

    private fun openRoleSelection(message: Int) {
        // Toast milik aplikasi, jadi tetap tampil setelah layar ini ditutup
        Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
        startActivity(
            Intent(this, RoleSelectionActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
        finish()
    }

    companion object {
        private const val EXTRA_IS_TEACHER = "IS_TEACHER"
        private const val STATE_SUCCESSOR = "SUCCESSOR_EMAIL"

        /** [isTeacher] true untuk Guru, yang juga boleh mengisi email guru pengganti. */
        fun intent(context: Context, isTeacher: Boolean): Intent =
            Intent(context, HapusAkunActivity::class.java).putExtra(EXTRA_IS_TEACHER, isTeacher)
    }
}
