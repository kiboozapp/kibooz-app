package com.nthb.kibooz.ui.wali

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.nthb.kibooz.databinding.ActivityDashboardWaliBinding
import com.nthb.kibooz.ui.camera.CameraScanSampahActivity
import com.nthb.kibooz.util.InsetsUtil

class DashboardWaliActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardWaliBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardWaliBinding.inflate(layoutInflater)
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
        // Navigasi ke Leaderboard / Ranking
        binding.cardMetricRanking.setOnClickListener {
            startActivity(Intent(this, LeaderboardActivity::class.java))
        }
        binding.cardBannerRanking.setOnClickListener {
            startActivity(Intent(this, LeaderboardActivity::class.java))
        }
        binding.tabLeaderboard.setOnClickListener {
            startActivity(Intent(this, LeaderboardActivity::class.java))
        }

        // Navigasi ke Statistik Sampah
        binding.cardMetricSampah.setOnClickListener {
            startActivity(Intent(this, StatistikSampahActivity::class.java))
        }
        binding.cardSampah.setOnClickListener {
            startActivity(Intent(this, StatistikSampahActivity::class.java))
        }
        binding.btnStatistikSampah.setOnClickListener {
            startActivity(Intent(this, StatistikSampahActivity::class.java))
        }
        binding.tabSampah.setOnClickListener {
            startActivity(Intent(this, StatistikSampahActivity::class.java))
        }

        // Navigasi ke Panduan & Penanganan Mood
        binding.cardMoodHariIni.setOnClickListener {
            startActivity(Intent(this, DetailPenangananMoodActivity::class.java))
        }
        binding.btnBukaPenanganan.setOnClickListener {
            startActivity(Intent(this, DetailPenangananMoodActivity::class.java))
        }
        binding.cardPanduanAman.setOnClickListener {
            startActivity(Intent(this, DetailPenangananMoodActivity::class.java))
        }

        // Riwayat Aktivitas Si Kecil
        binding.ivHistory.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Riwayat Aktivitas Si Kecil")
                .setMessage("1. 11:30 WIB - Amelia menyelesaikan kelas Bahasa & Komunikasi dengan ceria.\n2. 09:40 WIB - Amelia memilah sampah organik (daun kering) di taman sekolah (+5 Poin).\n3. 09:15 WIB - Amelia memilah botol plastik (+5 Poin).\n4. 07:30 WIB - Deteksi mood awal: Bingung/Ragu, diberikan pendampingan oleh guru.")
                .setPositiveButton("Tutup", null)
                .show()
        }

        // Navigasi ke Ruang Kelas Si Kecil (Screenshot 154028)
        binding.cardRuangKelasWali.setOnClickListener {
            startActivity(Intent(this, RuangKelasWaliActivity::class.java))
        }

        // Navigasi ke Profil Wali (Settings-First seperti Guru)
        binding.llStudentProfile.setOnClickListener {
            startActivity(Intent(this, ProfilWaliActivity::class.java))
        }
        binding.tabProfil.setOnClickListener {
            startActivity(Intent(this, ProfilWaliActivity::class.java))
        }

        // Navigasi ke Scanner AI Sampah
        binding.fabScannerSampah.setOnClickListener {
            startActivity(Intent(this, CameraScanSampahActivity::class.java))
        }

        // Notifikasi
        binding.ivNotification.setOnClickListener {
            Toast.makeText(this, "Tidak ada notifikasi baru untuk wali murid", Toast.LENGTH_SHORT).show()
        }
    }
}
