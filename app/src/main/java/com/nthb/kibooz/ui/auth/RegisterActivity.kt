package com.nthb.kibooz.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.textfield.TextInputLayout
import com.nthb.kibooz.R
import com.nthb.kibooz.appContainer
import com.nthb.kibooz.data.session.Role
import com.nthb.kibooz.data.session.SessionUser
import com.nthb.kibooz.databinding.ActivityRegisterBinding
import com.nthb.kibooz.ui.guru.DashboardGuruActivity
import com.nthb.kibooz.ui.wali.DashboardWaliActivity
import com.nthb.kibooz.util.InsetsUtil
import kotlinx.coroutines.launch

/** Pendaftaran Guru (dengan kelas pertamanya) atau Wali Murid (dengan kode kelas dan data anak). */
class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private var role: Role = Role.WALI
    private val viewModel: RegisterViewModel by viewModels { RegisterViewModel.Factory(appContainer.authRepository) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetsUtil.applyRootBottomInsets(binding.root)

        // Hanya GURU dan WALI yang bisa mendaftar, nilai lain jatuh ke WALI
        role = if (intent.getStringExtra(LoginActivity.EXTRA_SELECTED_ROLE) == Role.GURU.name) Role.GURU else Role.WALI
        val isGuru = role == Role.GURU
        binding.groupGuru.isVisible = isGuru
        binding.groupWali.isVisible = !isGuru
        binding.tvRegisterTitle.setText(if (isGuru) R.string.daftar_judul_guru else R.string.daftar_judul_wali)
        binding.tvRegisterSubtitle.setText(if (isGuru) R.string.daftar_subjudul_guru else R.string.daftar_subjudul_wali)

        binding.btnRegister.setOnClickListener { viewModel.register(readForm()) }
        binding.connectionStatus.onRetry = { viewModel.register(readForm()) }
        binding.tvToLogin.setOnClickListener { finish() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::render)
            }
        }
    }

    private fun readForm() = RegisterForm(
        role = role,
        fullName = binding.etFullName.text.toString(),
        email = binding.etEmail.text.toString(),
        password = binding.etPassword.text.toString(),
        phone = binding.etPhone.text.toString(),
        nip = binding.etNip.text.toString(),
        schoolName = binding.etSchool.text.toString(),
        className = binding.etClassName.text.toString(),
        gradeLevel = binding.etGradeLevel.text.toString(),
        academicYear = binding.etAcademicYear.text.toString(),
        classCode = binding.etClassCode.text.toString(),
        childName = binding.etChildName.text.toString(),
        nisn = binding.etNisn.text.toString()
    )

    private fun render(state: RegisterUiState) {
        binding.btnRegister.isEnabled = !state.loading

        val fields: Map<RegisterField, TextInputLayout> = mapOf(
            RegisterField.FULL_NAME to binding.tilFullName,
            RegisterField.EMAIL to binding.tilEmail,
            RegisterField.PASSWORD to binding.tilPassword,
            RegisterField.PHONE to binding.tilPhone,
            RegisterField.CLASS_NAME to binding.tilClassName,
            RegisterField.CLASS_CODE to binding.tilClassCode,
            RegisterField.CHILD_NAME to binding.tilChildName,
            RegisterField.NISN to binding.tilNisn
        )
        fields.forEach { (field, layout) -> layout.error = state.fieldErrors[field]?.let { getString(it) } }

        // Saat server tidak terjangkau, kartu gangguan sudah menjelaskan dan menawarkan coba lagi
        state.outage?.let { binding.connectionStatus.show(it) } ?: binding.connectionStatus.hide()

        state.error?.let {
            if (state.outage == null) Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
            viewModel.errorShown()
        }
        state.signedIn?.let(::openPortal)
    }

    private fun openPortal(user: SessionUser) {
        // Sama dengan login: dasbor menjadi layar paling bawah, tombol kembali tidak mengantar ke formulir lagi
        val target = if (user.role == Role.GURU) DashboardGuruActivity::class.java else DashboardWaliActivity::class.java
        startActivity(
            Intent(this, target).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK }
        )
        finish()
    }
}
