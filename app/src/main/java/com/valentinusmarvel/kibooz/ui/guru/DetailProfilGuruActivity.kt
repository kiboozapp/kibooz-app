package com.valentinusmarvel.kibooz.ui.guru

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.valentinusmarvel.kibooz.databinding.ActivityDetailProfilGuruBinding
import com.valentinusmarvel.kibooz.util.InsetsUtil

class DetailProfilGuruActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailProfilGuruBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailProfilGuruBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetsUtil.applySystemBarInsets(binding.root, headerView = binding.clTopBar)

        binding.ivBack.setOnClickListener {
            finish()
        }

        binding.ivActionEdit.setOnClickListener {
            startActivity(Intent(this, EditProfilGuruActivity::class.java))
        }

        binding.btnEditProfilGuru.setOnClickListener {
            startActivity(Intent(this, EditProfilGuruActivity::class.java))
        }
    }
}
