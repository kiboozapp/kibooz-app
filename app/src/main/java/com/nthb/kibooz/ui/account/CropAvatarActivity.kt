package com.nthb.kibooz.ui.account

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.nthb.kibooz.R
import com.nthb.kibooz.ui.theme.KiboozTheme
import com.nthb.kibooz.util.decodeOrientedBitmap
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Layar pemangkasan foto profil berbentuk lingkaran/persegi sebelum diunggah ke `POST /users/avatar`.
 * Menghasilkan file JPEG <= 2 MiB di direktori cache aplikasi melalui [EXTRA_CROPPED_PATH].
 */
class CropAvatarActivity : AppCompatActivity() {

    private val cropView by lazy { CropOverlayView(this) }
    private var saving by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sourceUri = intent.getParcelableExtra<Uri>(EXTRA_SOURCE_URI)
        if (sourceUri == null) {
            Toast.makeText(this, R.string.avatar_gagal_baca, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            KiboozTheme {
                CropAvatarScreen(
                    cropView = cropView,
                    saving = saving,
                    onBack = {
                        setResult(Activity.RESULT_CANCELED)
                        finish()
                    },
                    onRotate = { cropView.rotate90() },
                    onSave = ::exportAndReturn
                )
            }
        }

        loadSourceImage(sourceUri)
    }

    private fun loadSourceImage(uri: Uri) {
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) { decodeOrientedBitmap { contentResolver.openInputStream(uri) } }
            if (bitmap == null) {
                Toast.makeText(this@CropAvatarActivity, R.string.avatar_gagal_baca, Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }
            cropView.setBitmap(bitmap)
        }
    }

    private fun exportAndReturn() {
        saving = true
        lifecycleScope.launch {
            val outFile = File(cacheDir, "avatar_crop_${System.currentTimeMillis()}.jpg")
            val ok = withContext(Dispatchers.IO) { cropView.exportCroppedJpeg(outFile) }
            if (ok && outFile.exists() && outFile.length() > 0) {
                setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_CROPPED_PATH, outFile.absolutePath))
                finish()
            } else {
                saving = false
                Toast.makeText(this@CropAvatarActivity, R.string.avatar_gagal_baca, Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        const val EXTRA_SOURCE_URI = "SOURCE_URI"
        const val EXTRA_CROPPED_PATH = "CROPPED_PATH"

        fun intent(context: Context, sourceUri: Uri): Intent =
            Intent(context, CropAvatarActivity::class.java).putExtra(EXTRA_SOURCE_URI, sourceUri)
    }
}
