package com.nthb.kibooz.ui.account

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nthb.kibooz.R
import com.nthb.kibooz.appContainer
import com.nthb.kibooz.databinding.ActivityHapusAkunBinding
import com.nthb.kibooz.sessionManager
import com.nthb.kibooz.ui.auth.RoleSelectionActivity
import com.nthb.kibooz.util.InsetsUtil
import kotlinx.coroutines.launch

/**
 * Hapus akun, dibuka dari layar profil Guru dan Wali. Meminta kata sandi, untuk Guru juga email guru pengganti,
 * lalu dialog konfirmasi sebelum permintaan dikirim. Setelah akun terhapus, pengguna dibawa ke pilihan peran
 * dengan tumpukan layar kosong.
 */
class HapusAkunActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHapusAkunBinding
    private var dialog: AlertDialog? = null
    private var passwordVisible = false

    private val viewModel: DeleteAccountViewModel by viewModels {
        DeleteAccountViewModel.Factory(
            appContainer.accountRepository,
            intent.getBooleanExtra(EXTRA_IS_TEACHER, false),
            sessionManager::signOut
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHapusAkunBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetsUtil.applySystemBarInsets(root = binding.clRootHapusAkun, headerView = binding.clTopBar)

        binding.ivBack.setOnClickListener { finish() }
        binding.tvPeringatan.setText(
            if (viewModel.isTeacher) R.string.hapus_akun_peringatan_guru else R.string.hapus_akun_peringatan_wali
        )
        binding.llSuccessor.isVisible = viewModel.isTeacher

        binding.ivTogglePassword.setOnClickListener {
            passwordVisible = !passwordVisible
            binding.etPassword.transformationMethod =
                if (passwordVisible) HideReturnsTransformationMethod.getInstance() else PasswordTransformationMethod.getInstance()
            binding.etPassword.setSelection(binding.etPassword.text.length)
        }

        binding.btnHapus.setOnClickListener { submit() }
        binding.etSuccessor.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) submit()
            actionId == EditorInfo.IME_ACTION_DONE
        }
        // Kata sandi dan pengganti sudah diperiksa dan dikonfirmasi, jadi coba lagi langsung mengirim ulang
        binding.connectionStatus.onRetry = viewModel::confirm

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::render)
            }
        }
    }

    override fun onDestroy() {
        dialog?.dismiss()
        dialog = null
        super.onDestroy()
    }

    private fun submit() {
        viewModel.requestDelete(binding.etPassword.text.toString(), binding.etSuccessor.text.toString())
    }

    private fun render(state: DeleteAccountState) {
        binding.btnHapus.isEnabled = !state.loading
        binding.btnHapus.text = when {
            state.retryInSeconds != null -> getString(R.string.hapus_akun_tombol_menunggu, state.retryInSeconds)
            state.loading -> getString(R.string.hapus_akun_tombol_proses)
            else -> getString(R.string.hapus_akun_tombol)
        }

        showError(binding.tvPasswordError, state.passwordError)
        showError(binding.tvSuccessorError, state.successorError)
        binding.tvLabelSuccessor.setText(
            if (state.successorRequired) R.string.hapus_akun_label_pengganti_wajib else R.string.hapus_akun_label_pengganti_opsional
        )

        // Saat server tidak terjangkau, kartu gangguan sudah menjelaskan dan menawarkan coba lagi
        state.outage?.let { binding.connectionStatus.show(it) } ?: binding.connectionStatus.hide()

        state.error?.let {
            if (state.outage == null) Toast.makeText(this, it, Toast.LENGTH_LONG).show()
            viewModel.errorShown()
        }

        if (state.confirming) showConfirmation() else dismissConfirmation()
        state.finishedMessage?.let(::openRoleSelection)
    }

    private fun showError(view: TextView, message: Int?) {
        view.isVisible = message != null
        message?.let(view::setText)
    }

    private fun showConfirmation() {
        if (dialog?.isShowing == true) return

        val successor = binding.etSuccessor.text.toString().trim()
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

        /** [isTeacher] true untuk Guru, yang juga boleh mengisi email guru pengganti. */
        fun intent(context: Context, isTeacher: Boolean): Intent =
            Intent(context, HapusAkunActivity::class.java).putExtra(EXTRA_IS_TEACHER, isTeacher)
    }
}
