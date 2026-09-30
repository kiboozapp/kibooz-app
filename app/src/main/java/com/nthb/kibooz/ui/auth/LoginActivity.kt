package com.nthb.kibooz.ui.auth

import android.content.Intent
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.nthb.kibooz.databinding.ActivityLoginBinding
import com.nthb.kibooz.ui.guru.DashboardGuruActivity
import com.nthb.kibooz.ui.wali.DashboardWaliActivity
import com.nthb.kibooz.util.InsetsUtil

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private var isPasswordVisible: Boolean = false
    private var selectedRole: String = "ORANG_TUA"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetsUtil.applyRootBottomInsets(binding.root)

        selectedRole = intent.getStringExtra("SELECTED_ROLE") ?: "ORANG_TUA"

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
            handleLogin()
        }

        // Google login
        binding.btnGoogleLogin.setOnClickListener {
            Toast.makeText(this, "Login dengan Google...", Toast.LENGTH_SHORT).show()
            handleLogin()
        }
    }

    private fun handleLogin() {
        if (selectedRole == "GURU") {
            val intent = Intent(this, DashboardGuruActivity::class.java)
            startActivity(intent)
            finish()
        } else {
            val intent = Intent(this, DashboardWaliActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}
