package com.nthb.kibooz.ui.wali

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.nthb.kibooz.appContainer
import com.nthb.kibooz.databinding.ActivityProfilWaliBinding
import com.nthb.kibooz.sessionManager
import com.nthb.kibooz.ui.account.UbahKataSandiActivity
import com.nthb.kibooz.ui.camera.CameraScanSampahActivity
import com.nthb.kibooz.util.InsetsUtil

class ProfilWaliActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfilWaliBinding
    private val viewModel: WaliProfileViewModel by viewModels {
        WaliProfileViewModel.Factory(appContainer.waliProfileRepository, intent.getStringExtra(ProfilSiswaActivity.EXTRA_STUDENT_ID)) {
            appContainer.authRepository.restore()?.user?.fullName
        }
    }

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
        bindWaliProfile(binding, viewModel)
    }

    override fun onRestart() {
        super.onRestart()
        // Kembali dari profil siswa membawa nama atau anak yang mungkin berubah
        viewModel.load()
    }

    private fun setupListeners() {
        // Menu 1: Ubah Kata Sandi
        binding.menuUbahSandi.setOnClickListener {
            startActivity(Intent(this, UbahKataSandiActivity::class.java))
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
                    sessionManager.signOut()
                }
                .setNegativeButton("Batal", null)
                .show()
        }

        // Bottom Navigation Tabs
        binding.tabHome.setOnClickListener {
            // Kembali ke dasbor yang sudah ada, bukan menumpuk dasbor baru
            startActivity(
                Intent(this, DashboardWaliActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
            )
            finish()
        }

        binding.tabSampah.setOnClickListener {
            startActivity(
                Intent(this, StatistikSampahActivity::class.java)
                    .putExtra(StatistikSampahActivity.EXTRA_STUDENT_ID, viewModel.state.value.profile?.id)
            )
            finish()
        }

        binding.tabLeaderboard.setOnClickListener {
            startActivity(
                Intent(this, LeaderboardActivity::class.java)
                    .putExtra(LeaderboardActivity.EXTRA_CLASS_ID, viewModel.state.value.profile?.classId)
            )
            finish()
        }

        binding.fabScannerSampah.setOnClickListener {
            startActivity(
                Intent(this, CameraScanSampahActivity::class.java)
                    .putExtra(CameraScanSampahActivity.EXTRA_STUDENT_ID, viewModel.state.value.profile?.id)
            )
        }
    }
}
