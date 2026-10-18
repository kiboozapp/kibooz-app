package com.nthb.kibooz.ui.camera

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.nthb.kibooz.R
import com.nthb.kibooz.databinding.ActivityCameraScanMoodBinding
import com.nthb.kibooz.util.InsetsUtil

class CameraScanMoodActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCameraScanMoodBinding
    private lateinit var cameraPermission: CameraPermissionHelper
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

        cameraPermission = CameraPermissionHelper(
            this,
            binding.layoutIzinKamera,
            R.string.izin_kamera_mood
        ) { granted ->
            binding.btnCaptureMood.isEnabled = granted
            binding.btnCaptureMood.alpha = if (granted) 1f else 0.4f
        }
        cameraPermission.start()
    }

    override fun onResume() {
        super.onResume()
        cameraPermission.onResume()
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
