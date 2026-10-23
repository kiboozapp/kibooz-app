package com.nthb.kibooz.ui.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.nthb.kibooz.R
import com.nthb.kibooz.databinding.ViewCameraPermissionDeniedBinding

/**
 * Meminta izin kamera dan menampilkan panel penolakan selama izin belum diberikan.
 *
 * Buat instance di onCreate setelah setContentView, lalu panggil [start]. Panggil [onResume] dari
 * onResume Activity agar panel hilang saat pengguna kembali dari Pengaturan dengan izin aktif.
 */
class CameraPermissionHelper(
    private val activity: ComponentActivity,
    private val panel: ViewCameraPermissionDeniedBinding,
    @StringRes private val reason: Int,
    private val onGrantedChanged: (Boolean) -> Unit
) {

    private enum class State { GRANTED, DENIED, BLOCKED }

    private var state = State.DENIED

    private val launcher = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { render(currentState()) }

    init {
        panel.btnIzinAksi.setOnClickListener {
            if (state == State.BLOCKED) openSettings() else launcher.launch(Manifest.permission.CAMERA)
        }
    }

    fun start() {
        if (isGranted()) render(State.GRANTED) else launcher.launch(Manifest.permission.CAMERA)
    }

    fun onResume() {
        if (isGranted() && state != State.GRANTED) render(State.GRANTED)
    }

    private fun isGranted() = ContextCompat.checkSelfPermission(
        activity,
        Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    // Setelah permintaan ditolak, tidak ada dialog rasional berarti sistem tidak akan bertanya lagi
    private fun currentState() = when {
        isGranted() -> State.GRANTED
        activity.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> State.DENIED
        else -> State.BLOCKED
    }

    private fun render(newState: State) {
        state = newState
        panel.root.isVisible = newState != State.GRANTED
        panel.tvIzinPesan.setText(if (newState == State.BLOCKED) R.string.izin_kamera_ditolak else reason)
        panel.btnIzinAksi.setText(
            if (newState == State.BLOCKED) R.string.izin_kamera_pengaturan else R.string.izin_kamera_beri
        )
        onGrantedChanged(newState == State.GRANTED)
    }

    private fun openSettings() {
        activity.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", activity.packageName, null))
        )
    }
}
