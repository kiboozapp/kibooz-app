package com.valentinusmarvel.kibooz.ui.wali

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.valentinusmarvel.kibooz.R
import com.valentinusmarvel.kibooz.databinding.ActivityStatistikSampahBinding
import com.valentinusmarvel.kibooz.ui.camera.CameraScanSampahActivity
import com.valentinusmarvel.kibooz.util.InsetsUtil

class StatistikSampahActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStatistikSampahBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStatistikSampahBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetsUtil.applySystemBarInsets(
            root = binding.root,
            headerView = binding.clTopBar,
            bottomBar = binding.clBottomNavigation,
            fab = binding.fabScannerSampah
        )

        setupListeners()
    }

    private fun setupListeners() {
        binding.ivBack.setOnClickListener {
            finish()
        }

        // Toggle Period Pills
        binding.pillHariIni.setOnClickListener {
            updatePills(0)
        }
        binding.pillMingguIni.setOnClickListener {
            updatePills(1)
        }
        binding.pillBulanIni.setOnClickListener {
            updatePills(2)
        }

        // Bottom Navigation
        binding.tabHome.setOnClickListener {
            finish()
        }
        binding.tabLeaderboard.setOnClickListener {
            startActivity(Intent(this, LeaderboardActivity::class.java))
            finish()
        }
        binding.tabProfil.setOnClickListener {
            startActivity(Intent(this, ProfilSiswaActivity::class.java))
            finish()
        }
        binding.fabScannerSampah.setOnClickListener {
            startActivity(Intent(this, CameraScanSampahActivity::class.java))
        }
    }

    private fun updatePills(selectedIndex: Int) {
        val activeBg = R.drawable.bg_pill_date_active
        val inactiveBg = R.drawable.bg_pill_date_inactive
        val whiteColor = ContextCompat.getColor(this, R.color.kibooz_white)
        val mutedColor = ContextCompat.getColor(this, R.color.kibooz_text_muted)

        binding.pillHariIni.setBackgroundResource(if (selectedIndex == 0) activeBg else inactiveBg)
        binding.pillHariIni.setTextColor(if (selectedIndex == 0) whiteColor else mutedColor)

        binding.pillMingguIni.setBackgroundResource(if (selectedIndex == 1) activeBg else inactiveBg)
        binding.pillMingguIni.setTextColor(if (selectedIndex == 1) whiteColor else mutedColor)

        binding.pillBulanIni.setBackgroundResource(if (selectedIndex == 2) activeBg else inactiveBg)
        binding.pillBulanIni.setTextColor(if (selectedIndex == 2) whiteColor else mutedColor)
    }
}
