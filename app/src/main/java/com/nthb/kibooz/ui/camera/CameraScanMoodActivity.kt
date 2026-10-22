package com.nthb.kibooz.ui.camera

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.nthb.kibooz.databinding.ActivityCameraScanMoodBinding
import com.nthb.kibooz.util.InsetsUtil

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
            val intent = Intent(this, ReviewScanMoodActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}
