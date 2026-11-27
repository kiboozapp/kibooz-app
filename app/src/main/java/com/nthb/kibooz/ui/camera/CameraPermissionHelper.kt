package com.nthb.kibooz.ui.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/** Keadaan izin kamera yang menentukan apa yang tampil di panel izin. */
enum class CameraPermissionState {
    GRANTED,

    /** Ditolak, tetapi sistem masih mau menampilkan dialog izin lagi. */
    DENIED,

    /** Ditolak dan sistem tidak akan bertanya lagi, jadi pengguna harus membuka Pengaturan. */
    BLOCKED
}

/**
 * Meminta izin kamera dan melaporkan keadaannya lewat [onStateChanged]. Layar yang memakai helper ini yang menggambar
 * panel penolakan, dan tombol di panel itu memanggil [requestOrOpenSettings].
 *
 * Buat instance di onCreate, lalu panggil [start]. Panggil [onResume] dari onResume Activity agar panel hilang saat
 * pengguna kembali dari Pengaturan dengan izin aktif.
 */
class CameraPermissionHelper(
    private val activity: ComponentActivity,
    private val onStateChanged: (CameraPermissionState) -> Unit
) {

    var state = CameraPermissionState.DENIED
        private set

    private val launcher = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { report(currentState()) }

    fun start() {
        if (isGranted()) report(CameraPermissionState.GRANTED) else launcher.launch(Manifest.permission.CAMERA)
    }

    fun onResume() {
        if (isGranted() && state != CameraPermissionState.GRANTED) report(CameraPermissionState.GRANTED)
    }

    /** Aksi tombol panel: meminta izin lagi, atau membuka Pengaturan jika sistem tidak akan bertanya lagi. */
    fun requestOrOpenSettings() {
        if (state == CameraPermissionState.BLOCKED) openSettings() else launcher.launch(Manifest.permission.CAMERA)
    }

    private fun isGranted() = ContextCompat.checkSelfPermission(
        activity,
        Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    // Setelah permintaan ditolak, tidak ada dialog rasional berarti sistem tidak akan bertanya lagi
    private fun currentState() = when {
        isGranted() -> CameraPermissionState.GRANTED
        activity.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> CameraPermissionState.DENIED
        else -> CameraPermissionState.BLOCKED
    }

    private fun report(newState: CameraPermissionState) {
        state = newState
        onStateChanged(newState)
    }

    private fun openSettings() {
        activity.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", activity.packageName, null))
        )
    }
}
