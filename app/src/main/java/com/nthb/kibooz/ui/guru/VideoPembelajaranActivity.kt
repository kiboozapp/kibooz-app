package com.nthb.kibooz.ui.guru

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.nthb.kibooz.databinding.ActivityVideoPembelajaranBinding
import com.nthb.kibooz.util.InsetsUtil

class VideoPembelajaranActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVideoPembelajaranBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoPembelajaranBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Pastikan insets atas untuk status bar dan bawah untuk FAB
        InsetsUtil.applySystemBarInsets(
            root = binding.clRootVideo,
            headerView = binding.clTopBar,
            fab = binding.fabTambahVideo
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
            Toast.makeText(this, "Opsi Video Kelas $namaKelas", Toast.LENGTH_SHORT).show()
        }

        binding.fabTambahVideo.setOnClickListener {
            Toast.makeText(this, "Buka Galeri / Rekam Video Pembelajaran Baru", Toast.LENGTH_SHORT).show()
        }

        binding.btnShareVideo1.setOnClickListener {
            shareVideo("Mengenal Huruf dan Suara")
        }

        binding.btnShareVideo2.setOnClickListener {
            shareVideo("Mengenal Nama-Nama Benda di Sekitar")
        }

        binding.btnShareVideo3.setOnClickListener {
            shareVideo("Ekspresi & Bercerita Bersama Kibby")
        }

        binding.cardVideo1.setOnClickListener {
            Toast.makeText(this, "Memutar Video: Mengenal Huruf dan Suara", Toast.LENGTH_SHORT).show()
        }

        binding.cardVideo2.setOnClickListener {
            Toast.makeText(this, "Memutar Video: Mengenal Nama-Nama Benda", Toast.LENGTH_SHORT).show()
        }

        binding.cardVideo3.setOnClickListener {
            Toast.makeText(this, "Memutar Video: Dongeng Bersama Kibby", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareVideo(title: String) {
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, "Tonton video pembelajaran Kibooz: $title")
            type = "text/plain"
        }
        startActivity(Intent.createChooser(shareIntent, "Bagikan Video Pembelajaran"))
    }
}
