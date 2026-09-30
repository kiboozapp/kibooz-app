package com.valentinusmarvel.kibooz.ui.guru

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.valentinusmarvel.kibooz.databinding.ActivityPesertaKelasBinding
import com.valentinusmarvel.kibooz.util.InsetsUtil

class PesertaKelasActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPesertaKelasBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPesertaKelasBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Insets status bar
        InsetsUtil.applySystemBarInsets(
            root = binding.clRootPeserta,
            headerView = binding.clTopBar
        )

        val namaKelas = intent.getStringExtra("NAMA_KELAS") ?: "Bahasa & Komunikasi"
        binding.tvKelasTitle.text = namaKelas

        setupListeners(namaKelas)
    }

    private fun setupListeners(namaKelas: String) {
        binding.ivBack.setOnClickListener {
            finish()
        }

        binding.ivMenu.setOnClickListener {
            Toast.makeText(this, "Kelola Anggota Kelas $namaKelas", Toast.LENGTH_SHORT).show()
        }
    }
}
