package com.nthb.kibooz.ui.camera

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.nthb.kibooz.R
import com.nthb.kibooz.databinding.ActivityCameraScanSampahBinding
import com.nthb.kibooz.util.InsetsUtil

class CameraScanSampahActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCameraScanSampahBinding
    private lateinit var cameraPermission: CameraPermissionHelper
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

        cameraPermission = CameraPermissionHelper(
            this,
            binding.layoutIzinKamera,
            R.string.izin_kamera_sampah
        ) { granted ->
            binding.btnClaimPoin.isEnabled = granted
            binding.btnClaimPoin.alpha = if (granted) 1f else 0.4f
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

        binding.btnClaimPoin.setOnClickListener {
            // Belum ada pemindaian dan belum ada klaim ke server, jadi tidak ada poin yang boleh diumumkan
            Toast.makeText(this, R.string.sampah_klaim_belum_tersedia, Toast.LENGTH_LONG).show()
        }
    }
}
