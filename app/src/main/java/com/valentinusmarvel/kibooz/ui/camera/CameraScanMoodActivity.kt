package com.valentinusmarvel.kibooz.ui.camera

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.valentinusmarvel.kibooz.databinding.ActivityCameraScanMoodBinding
import com.valentinusmarvel.kibooz.util.InsetsUtil

class CameraScanMoodActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCameraScanMoodBinding
    private var isFlashOn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraScanMoodBinding.inflate(layoutInflater)
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

        binding.btnCaptureMood.setOnClickListener {
            Toast.makeText(
                this,
                "Deteksi AI Berhasil! Data mood 2 siswa tercatat ke statistik kelas.",
                Toast.LENGTH_LONG
            ).show()
            finish()
        }
    }
}
