package com.nthb.kibooz.ui.guru

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.nthb.kibooz.R
import com.nthb.kibooz.databinding.ActivityEditProfilGuruBinding
import com.nthb.kibooz.util.InsetsUtil

class EditProfilGuruActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditProfilGuruBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditProfilGuruBinding.inflate(layoutInflater)
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

        binding.ivChangePhoto.setOnClickListener {
            Toast.makeText(this, "Fitur ganti foto profil guru", Toast.LENGTH_SHORT).show()
        }

        binding.btnSimpan.setOnClickListener {
            val nama = binding.etNamaGuru.text.toString().trim()
            if (nama.isEmpty()) {
                Toast.makeText(this, "Nama tidak boleh kosong", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Belum ada pemanggilan ke server, jadi perubahan tidak boleh dinyatakan tersimpan
            Toast.makeText(this, R.string.profil_ubah_belum_tersedia, Toast.LENGTH_SHORT).show()
        }
    }
}
