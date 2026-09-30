package com.valentinusmarvel.kibooz.ui.auth

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.valentinusmarvel.kibooz.databinding.ActivityRoleSelectionBinding
import com.valentinusmarvel.kibooz.util.InsetsUtil

class RoleSelectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRoleSelectionBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRoleSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetsUtil.applySystemBarInsets(binding.root, headerView = binding.llHeader)

        binding.cardOrangTua.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java).apply {
                putExtra("SELECTED_ROLE", "ORANG_TUA")
            }
            startActivity(intent)
        }

        binding.cardGuru.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java).apply {
                putExtra("SELECTED_ROLE", "GURU")
            }
            startActivity(intent)
        }
    }
}
