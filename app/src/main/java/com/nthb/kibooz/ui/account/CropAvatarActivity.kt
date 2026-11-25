package com.nthb.kibooz.ui.account

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.nthb.kibooz.R
import com.nthb.kibooz.databinding.ActivityCropAvatarBinding
import com.nthb.kibooz.util.AvatarCropMath
import com.nthb.kibooz.util.InsetsUtil
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Layar pemangkasan foto profil berbentuk lingkaran/persegi sebelum diunggah ke `POST /users/avatar`.
 * Menghasilkan file JPEG <= 2 MiB di direktori cache aplikasi melalui [EXTRA_CROPPED_PATH].
 */
class CropAvatarActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCropAvatarBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCropAvatarBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetsUtil.applySystemBarInsets(
            root = binding.root,
            headerView = binding.clTopBar,
            bottomBar = binding.clBottomActions
        )

        val sourceUri = intent.getParcelableExtra<Uri>(EXTRA_SOURCE_URI)
        if (sourceUri == null) {
            Toast.makeText(this, R.string.avatar_gagal_baca, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.ivBack.setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }

        binding.btnPutar.setOnClickListener {
            binding.cropOverlay.rotate90()
        }

        binding.btnSimpanFoto.setOnClickListener {
            exportAndReturn()
        }

        loadSourceImage(sourceUri)
    }

    private fun loadSourceImage(uri: Uri) {
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) { decodeOrientedBitmap(uri) }
            if (bitmap == null) {
                Toast.makeText(this@CropAvatarActivity, R.string.avatar_gagal_baca, Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }
            binding.cropOverlay.setBitmap(bitmap)
        }
    }

    private fun exportAndReturn() {
        binding.btnSimpanFoto.isEnabled = false
        lifecycleScope.launch {
            val outFile = File(cacheDir, "avatar_crop_${System.currentTimeMillis()}.jpg")
            val ok = withContext(Dispatchers.IO) { binding.cropOverlay.exportCroppedJpeg(outFile) }
            if (ok && outFile.exists() && outFile.length() > 0) {
                setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_CROPPED_PATH, outFile.absolutePath))
                finish()
            } else {
                binding.btnSimpanFoto.isEnabled = true
                Toast.makeText(this@CropAvatarActivity, R.string.avatar_gagal_baca, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun decodeOrientedBitmap(uri: Uri): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val sampleSize = AvatarCropMath.computeSampleSize(bounds.outWidth, bounds.outHeight)
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val decoded = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOpts) }
                ?: return null

            val orientation = contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL

            val degrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (degrees == 0f) {
                decoded
            } else {
                val matrix = Matrix().apply { postRotate(degrees) }
                Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also {
                    if (it !== decoded) decoded.recycle()
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        const val EXTRA_SOURCE_URI = "SOURCE_URI"
        const val EXTRA_CROPPED_PATH = "CROPPED_PATH"

        fun intent(context: Context, sourceUri: Uri): Intent =
            Intent(context, CropAvatarActivity::class.java).putExtra(EXTRA_SOURCE_URI, sourceUri)
    }
}
