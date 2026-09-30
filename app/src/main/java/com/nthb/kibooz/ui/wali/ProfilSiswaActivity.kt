package com.nthb.kibooz.ui.wali

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.nthb.kibooz.databinding.ActivityProfilSiswaBinding
import com.nthb.kibooz.ui.auth.RoleSelectionActivity
import com.nthb.kibooz.ui.camera.CameraScanSampahActivity
import com.nthb.kibooz.util.InsetsUtil

class ProfilSiswaActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfilSiswaBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfilSiswaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetsUtil.applySystemBarInsets(
            root = binding.root,
            headerView = binding.clTopHeader,
            bottomBar = binding.clBottomNavigation,
            fab = binding.fabScannerSampah
        )

        setupListeners()
    }

    private fun setupListeners() {
        binding.btnEditProfil.setOnClickListener {
            startActivity(Intent(this, EditProfilSiswaActivity::class.java))
        }

        binding.btnLogout.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Keluar Akun Wali")
                .setMessage("Apakah Anda yakin ingin keluar dari akun Kibooz Orang Tua?")
                .setPositiveButton("Ya, Keluar") { _, _ ->
                    val intent = Intent(this, RoleSelectionActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    startActivity(intent)
                    finish()
                }
                .setNegativeButton("Batal", null)
                .show()
        }

        // Bottom Navigation
        binding.tabHome.setOnClickListener {
            val intent = Intent(this, DashboardWaliActivity::class.java)
            startActivity(intent)
            finish()
        }
        binding.tabSampah.setOnClickListener {
            startActivity(Intent(this, StatistikSampahActivity::class.java))
            finish()
        }
        binding.tabLeaderboard.setOnClickListener {
            startActivity(Intent(this, LeaderboardActivity::class.java))
            finish()
        }
        binding.fabScannerSampah.setOnClickListener {
            startActivity(Intent(this, CameraScanSampahActivity::class.java))
        }
    }
}
