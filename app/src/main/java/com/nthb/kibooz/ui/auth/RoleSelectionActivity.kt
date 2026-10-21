package com.nthb.kibooz.ui.auth

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.nthb.kibooz.data.session.Role
import com.nthb.kibooz.databinding.ActivityRoleSelectionBinding
import com.nthb.kibooz.util.InsetsUtil

class RoleSelectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRoleSelectionBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRoleSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetsUtil.applySystemBarInsets(binding.root, headerView = binding.llHeader)

        binding.cardOrangTua.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java).apply {
                putExtra(LoginActivity.EXTRA_SELECTED_ROLE, Role.WALI.name)
            }
            startActivity(intent)
        }

        binding.cardGuru.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java).apply {
                putExtra(LoginActivity.EXTRA_SELECTED_ROLE, Role.GURU.name)
            }
            startActivity(intent)
        }
    }
}
