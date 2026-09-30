package com.valentinusmarvel.kibooz.ui.guru

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.valentinusmarvel.kibooz.databinding.ActivityDashboardGuruBinding
import com.valentinusmarvel.kibooz.ui.camera.CameraScanMoodActivity
import com.valentinusmarvel.kibooz.util.InsetsUtil

class DashboardGuruActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardGuruBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardGuruBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Menerapkan Status Bar Inset pada Header dan Navigation Bar Inset pada Bottom Nav & FAB
        InsetsUtil.applySystemBarInsets(
            root = binding.root,
            headerView = binding.clTopHeader,
            bottomBar = binding.clBottomNavigation,
            fab = binding.fabScanner
        )

        setupListeners()
    }

    private fun setupListeners() {
        // Navigasi ke Statistik Mood
        binding.cvMoodKelas.setOnClickListener {
            startActivity(Intent(this, StatistikMoodActivity::class.java))
        }
        binding.tabStatistik.setOnClickListener {
            startActivity(Intent(this, StatistikMoodActivity::class.java))
        }

        // Navigasi ke Ruang Kelas
        binding.tabKelas.setOnClickListener {
            startActivity(Intent(this, RuangKelasGuruActivity::class.java))
        }

        // Navigasi ke Profil Guru
        binding.tabProfil.setOnClickListener {
            startActivity(Intent(this, ProfilGuruActivity::class.java))
        }
        binding.cvAvatar.setOnClickListener {
            startActivity(Intent(this, ProfilGuruActivity::class.java))
        }

        // Navigasi ke Kamera Scan Face / Mood Siswa
        binding.fabScanner.setOnClickListener {
            startActivity(Intent(this, CameraScanMoodActivity::class.java))
        }
        binding.btnScanUlang.setOnClickListener {
            startActivity(Intent(this, CameraScanMoodActivity::class.java))
        }

        // Banner Ranking Kelas
        binding.cvRankingKelas.setOnClickListener {
            Toast.makeText(this, "Membuka statistik peringkat kelas", Toast.LENGTH_SHORT).show()
        }

        // Notification & History
        binding.ivNotification.setOnClickListener {
            Toast.makeText(this, "Tidak ada notifikasi baru", Toast.LENGTH_SHORT).show()
        }
        binding.ivHistory.setOnClickListener {
            Toast.makeText(this, "Riwayat absensi mood terakhir diperbarui hari ini", Toast.LENGTH_SHORT).show()
        }
    }
}
