package com.nthb.kibooz.ui.camera

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.nthb.kibooz.databinding.ActivityCameraScanSampahBinding
import com.nthb.kibooz.util.InsetsUtil

class CameraScanSampahActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCameraScanSampahBinding
    private var isFlashOn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraScanSampahBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetsUtil.applySystemBarInsets(
            root = binding.root,
            headerView = binding.clTopOverlay,
            bottomBar = binding.clBottomControls
        )

        setupListeners()
    }

    private fun setupListeners() {
        binding.ivBack.setOnClickListener {
            finish()
        }

        binding.ivFlash.setOnClickListener {
            isFlashOn = !isFlashOn
            val msg = if (isFlashOn) "Flash Kamera Dinyalakan" else "Flash Kamera Dimatikan"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        binding.btnClaimPoin.setOnClickListener {
            Toast.makeText(
                this,
                "Luar biasa! +5 Poin kebaikan berhasil diklaim untuk Amelia!",
                Toast.LENGTH_LONG
            ).show()
            finish()
        }
    }
}
