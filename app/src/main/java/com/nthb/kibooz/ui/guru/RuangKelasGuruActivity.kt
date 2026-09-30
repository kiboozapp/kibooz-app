package com.nthb.kibooz.ui.guru

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.nthb.kibooz.databinding.ActivityRuangKelasGuruBinding
import com.nthb.kibooz.ui.camera.CameraScanMoodActivity
import com.nthb.kibooz.util.InsetsUtil

class RuangKelasGuruActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRuangKelasGuruBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRuangKelasGuruBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetsUtil.applySystemBarInsets(
            root = binding.root,
            headerView = binding.clTopBar,
            bottomBar = binding.clBottomNavigation,
            fab = binding.fabScanner
        )

        setupListeners()
    }

    private fun setupListeners() {
        binding.ivBack.setOnClickListener {
            finish()
        }

        binding.btnTambahKelas.setOnClickListener {
            Toast.makeText(this, "Fitur Tambah Kelas Guru Baru", Toast.LENGTH_SHORT).show()
        }

        // Subject Card Click Listeners
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
            openDetailKelas("SOS-A1", "Sosial & Emosional")
        }

        // Bottom Navigation
        binding.tabHome.setOnClickListener {
            finish()
        }
        binding.tabStatistik.setOnClickListener {
            startActivity(Intent(this, StatistikMoodActivity::class.java))
            finish()
        }
        binding.tabProfil.setOnClickListener {
            startActivity(Intent(this, ProfilGuruActivity::class.java))
            finish()
        }
        binding.fabScanner.setOnClickListener {
            startActivity(Intent(this, CameraScanMoodActivity::class.java))
        }
    }

    private fun openDetailKelas(kode: String, nama: String) {
        val intent = Intent(this, DetailKelasActivity::class.java).apply {
            putExtra("KODE_KELAS", kode)
            putExtra("NAMA_KELAS", nama)
        }
        startActivity(intent)
    }
}
