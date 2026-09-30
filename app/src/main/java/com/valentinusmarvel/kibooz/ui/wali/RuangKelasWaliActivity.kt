package com.valentinusmarvel.kibooz.ui.wali

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.valentinusmarvel.kibooz.databinding.ActivityRuangKelasWaliBinding
import com.valentinusmarvel.kibooz.ui.camera.CameraScanSampahActivity
import com.valentinusmarvel.kibooz.ui.guru.DetailKelasActivity
import com.valentinusmarvel.kibooz.util.InsetsUtil

class RuangKelasWaliActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRuangKelasWaliBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRuangKelasWaliBinding.inflate(layoutInflater)
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

        // Tombol Gabung Kelas (Screenshot 154028)
        binding.btnGabungKelas.setOnClickListener {
            showGabungKelasDialog()
        }

        // 6 Subject Cards (Screenshot 154028)
        binding.cardMapelSeni.setOnClickListener {
            openDetailKelas("SENI-A1", "Seni & Kreativitas")
        }
        binding.cardMapelBahasa.setOnClickListener {
            openDetailKelas("BHS-A1", "Bahasa & Komunikasi")
        }
        binding.cardMapelLogika.setOnClickListener {
            openDetailKelas("LOG-A1", "Berpikir & Logika")
        }
        binding.cardMapelMotorik.setOnClickListener {
            openDetailKelas("MOT-A1", "Belajar Fisik Motorik")
        }
        binding.cardMapelAgama.setOnClickListener {
            openDetailKelas("AGM-A1", "Nilai Agama & Moral")
        }
        binding.cardMapelSosial.setOnClickListener {
            openDetailKelas("SOS-A1", "Sosial & Emotional")
        }

        // Bottom Navigation Wali
        binding.tabHome.setOnClickListener {
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
        binding.tabProfil.setOnClickListener {
            startActivity(Intent(this, ProfilWaliActivity::class.java))
            finish()
        }
        binding.fabScannerSampah.setOnClickListener {
            startActivity(Intent(this, CameraScanSampahActivity::class.java))
        }
    }

    private fun showGabungKelasDialog() {
        val input = EditText(this).apply {
            hint = "Contoh: SENI-A1"
            setSingleLine()
            setPadding(48, 24, 48, 24)
        }
        val container = FrameLayout(this).apply {
            setPadding(32, 16, 32, 0)
            addView(input)
        }

        AlertDialog.Builder(this)
            .setTitle("Gabung Kelas Si Kecil")
            .setMessage("Masukkan kode kelas 6 karakter yang diberikan oleh wali kelas:")
            .setView(container)
            .setPositiveButton("Gabung") { _, _ ->
                val kode = input.text.toString().trim()
                if (kode.isNotEmpty()) {
                    Toast.makeText(this, "Berhasil terhubung ke kelas $kode!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Kode kelas tidak boleh kosong", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun openDetailKelas(kode: String, nama: String) {
        val intent = Intent(this, DetailKelasActivity::class.java).apply {
            putExtra("KODE_KELAS", kode)
            putExtra("NAMA_KELAS", nama)
        }
        startActivity(intent)
    }
}
