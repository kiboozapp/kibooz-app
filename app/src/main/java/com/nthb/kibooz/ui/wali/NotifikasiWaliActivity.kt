package com.nthb.kibooz.ui.wali

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.nthb.kibooz.databinding.ActivityNotifikasiWaliBinding
import com.nthb.kibooz.ui.guru.VideoPembelajaranActivity
import com.nthb.kibooz.util.InsetsUtil

class NotifikasiWaliActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNotifikasiWaliBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotifikasiWaliBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetsUtil.applySystemBarInsets(
            root = binding.root,
            headerView = binding.clTopBar
        )

        setupListeners()
    }

    private fun setupListeners() {
        binding.ivBack.setOnClickListener {
            finish()
        }

        binding.btnTandaiDibaca.setOnClickListener {
            binding.cardNotifMood.alpha = 0.75f
            binding.cardNotifSampah.alpha = 0.75f
            binding.cardNotifMateri.alpha = 0.75f
            binding.cardNotifGabung.alpha = 0.75f
            Toast.makeText(this, "Semua notifikasi telah ditandai dibaca", Toast.LENGTH_SHORT).show()
        }

        binding.cardNotifMood.setOnClickListener {
            Toast.makeText(this, "Membuka riwayat mood si kecil", Toast.LENGTH_SHORT).show()
        }

        binding.cardNotifSampah.setOnClickListener {
            startActivity(Intent(this, StatistikSampahActivity::class.java))
        }

        binding.cardNotifMateri.setOnClickListener {
            startActivity(Intent(this, VideoPembelajaranActivity::class.java))
        }

        binding.cardNotifGabung.setOnClickListener {
            startActivity(Intent(this, RuangKelasWaliActivity::class.java))
        }
    }
}
