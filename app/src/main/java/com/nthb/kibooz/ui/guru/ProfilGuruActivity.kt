package com.nthb.kibooz.ui.guru

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.nthb.kibooz.databinding.ActivityProfilGuruBinding
import com.nthb.kibooz.sessionManager
import com.nthb.kibooz.ui.account.HapusAkunActivity
import com.nthb.kibooz.ui.camera.CameraScanMoodActivity
import com.nthb.kibooz.util.InsetsUtil

class ProfilGuruActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfilGuruBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfilGuruBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Terapkan insets lengkap: Status bar pada header atas, Navigation bar pada bottom bar & FAB
        InsetsUtil.applySystemBarInsets(
            root = binding.clRootProfilGuru,
            headerView = binding.clHeaderArea,
            bottomBar = binding.clBottomNavigation,
            fab = binding.fabScannerFace
        )

        setupListeners()
    }

    private fun setupListeners() {
        // Tombol Lihat Detail -> Membuka Detail Profil Guru
        binding.btnLihatDetail.setOnClickListener {
            startActivity(Intent(this, DetailProfilGuruActivity::class.java))
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
                .setMessage("Ketentuan Layanan Aplikasi Kibooz:\n1. Penggunaan akun guru khusus untuk kepentingan bimbingan belajar dan evaluasi emosi anak.\n2. Data siswa hanya digunakan untuk keperluan pendampingan anak di sekolah.")
                .setPositiveButton("Tutup", null)
                .show()
        }

        // Menu 3: Kebijakan Privasi
        binding.menuKebijakanPrivasi.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Kebijakan Privasi")
                .setMessage("Kibooz memprioritaskan privasi data anak dan pendidik. Wajah siswa dianalisis hanya di ponsel dan tidak disimpan atau dikirim. Yang dikirim hanya label emosi dan tingkat keyakinannya.")
                .setPositiveButton("Tutup", null)
                .show()
        }

        // Menu 4: Panduan
        binding.menuPanduan.setOnClickListener {
            Toast.makeText(this, "Membuka Panduan Penggunaan Aplikasi Kibooz Guru", Toast.LENGTH_SHORT).show()
        }

        // Menu 5: Keluar Akun
        binding.menuKeluar.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Keluar Akun Guru")
                .setMessage("Apakah Anda yakin ingin keluar dari akun Kibooz Guru?")
                .setPositiveButton("Ya, Keluar") { _, _ ->
                    sessionManager.signOut()
            }
                .setNegativeButton("Batal", null)
                .show()
        }

        // Menu 6: Hapus Akun
        binding.menuHapusAkun.setOnClickListener {
            startActivity(HapusAkunActivity.intent(this, isTeacher = true))
        }

        // Bottom Navigation Tabs
        binding.tabHome.setOnClickListener {
            // Kembali ke dasbor yang sudah ada, bukan menumpuk dasbor baru
            startActivity(
                Intent(this, DashboardGuruActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
            )
            finish()
        }

        binding.tabKelas.setOnClickListener {
            startActivity(Intent(this, RuangKelasGuruActivity::class.java))
            finish()
        }

        binding.tabStats.setOnClickListener {
            startActivity(Intent(this, StatistikMoodActivity::class.java))
            finish()
        }

        binding.fabScannerFace.setOnClickListener {
            startActivity(Intent(this, CameraScanMoodActivity::class.java))
        }
    }
}
