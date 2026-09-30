package com.nthb.kibooz.ui.guru

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.nthb.kibooz.databinding.ActivityStatistikMoodBinding
import com.nthb.kibooz.ui.camera.CameraScanMoodActivity
import com.nthb.kibooz.util.InsetsUtil

class StatistikMoodActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStatistikMoodBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStatistikMoodBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetsUtil.applySystemBarInsets(
            root = binding.root,
            headerView = binding.clTopBar,
            bottomBar = binding.clBottomNav,
            fab = binding.fabScan
        )

        setupListeners()
    }

    private fun setupListeners() {
        binding.ivBack.setOnClickListener {
            finish()
        }

        binding.tabNavHome.setOnClickListener {
            finish()
        }

        binding.tabNavKelas.setOnClickListener {
            startActivity(Intent(this, RuangKelasGuruActivity::class.java))
            finish()
        }

        binding.tabNavProfil.setOnClickListener {
            startActivity(Intent(this, ProfilGuruActivity::class.java))
            finish()
        }

        binding.fabScan.setOnClickListener {
            startActivity(Intent(this, CameraScanMoodActivity::class.java))
        }
    }
}
