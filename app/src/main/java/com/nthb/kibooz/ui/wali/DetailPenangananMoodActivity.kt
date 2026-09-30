package com.nthb.kibooz.ui.wali

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.nthb.kibooz.databinding.ActivityDetailPenangananMoodBinding
import com.nthb.kibooz.util.InsetsUtil

class DetailPenangananMoodActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailPenangananMoodBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailPenangananMoodBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetsUtil.applySystemBarInsets(
            root = binding.root,
            headerView = binding.clTopBar,
            bottomBar = binding.clBottomCta
        )

        setupListeners()
    }

    private fun setupListeners() {
        binding.ivBack.setOnClickListener {
            finish()
        }

        binding.ivShare.setOnClickListener {
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Panduan Penanganan Emosi Kibooz: Berikan rasa aman pada anak dengan 4 langkah praktis."
                )
                type = "text/plain"
            }
            startActivity(Intent.createChooser(shareIntent, "Bagikan Panduan Emosi"))
        }

        binding.btnTerapkanPanduan.setOnClickListener {
            Toast.makeText(
                this,
                "Panduan berhasil dicatat! Status penanganan telah diteruskan ke Guru Kelas A.",
                Toast.LENGTH_LONG
            ).show()
            finish()
        }
    }
}
