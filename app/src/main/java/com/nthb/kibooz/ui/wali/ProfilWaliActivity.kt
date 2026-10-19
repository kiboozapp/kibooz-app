package com.nthb.kibooz.ui.wali

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.nthb.kibooz.databinding.ActivityProfilWaliBinding
import com.nthb.kibooz.ui.auth.RoleSelectionActivity
import com.nthb.kibooz.ui.camera.CameraScanSampahActivity
import com.nthb.kibooz.util.InsetsUtil

class ProfilWaliActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfilWaliBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfilWaliBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetsUtil.applySystemBarInsets(
            root = binding.clRootProfilWali,
            headerView = binding.clHeaderArea,
            bottomBar = binding.clBottomNavigation,
            fab = binding.fabScannerSampah
        )

        setupListeners()
    }

    private fun setupListeners() {
        // Tombol Lihat Detail Siswa -> Membuka Profil Lengkap Siswa
        binding.btnLihatDetail.setOnClickListener {
            startActivity(Intent(this, ProfilSiswaActivity::class.java))
        }

        // Menu 1: Ubah Kata Sandi
        binding.menuUbahSandi.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Ubah Kata Sandi")
                .setMessage("Pengubahan kata sandi belum tersedia di versi ini.")
                .setPositiveButton("Mengerti", null)
                .show()
        }

        // Menu 2: Syarat dan Ketentuan
        binding.menuSyaratKetentuan.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Syarat dan Ketentuan")
                .setMessage("Ketentuan Layanan Aplikasi Kibooz:\n1. Penggunaan akun wali murid khusus untuk memantau aktivitas emosi dan kebiasaan pilah sampah anak di sekolah.\n2. Data siswa hanya digunakan untuk keperluan pendampingan anak di sekolah.")
                .setPositiveButton("Tutup", null)
                .show()
        }

        // Menu 3: Kebijakan Privasi
        binding.menuKebijakanPrivasi.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Kebijakan Privasi")
                .setMessage("Kibooz memprioritaskan privasi data anak dan keluarga. Wajah anak dianalisis hanya di ponsel dan tidak disimpan atau dikirim. Yang dikirim hanya label emosi dan tingkat keyakinannya.")
                .setPositiveButton("Tutup", null)
                .show()
        }

        // Menu 4: Panduan
        binding.menuPanduan.setOnClickListener {
            Toast.makeText(this, "Membuka Panduan Penggunaan Aplikasi Kibooz untuk Orang Tua", Toast.LENGTH_SHORT).show()
        }

        // Menu 5: Keluar Akun Wali
        binding.menuKeluar.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Keluar Akun Wali")
                .setMessage("Apakah Anda yakin ingin keluar dari akun Kibooz Wali?")
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

        // Bottom Navigation Tabs
        binding.tabHome.setOnClickListener {
            startActivity(Intent(this, DashboardWaliActivity::class.java))
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
