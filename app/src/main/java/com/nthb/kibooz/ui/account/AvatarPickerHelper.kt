package com.nthb.kibooz.ui.account

import android.app.Activity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File

/**
 * Pengelola alur pilih foto galeri lalu potong di [CropAvatarActivity]. Satu helper dipakai ulang oleh
 * EditProfilGuruActivity, EditProfilSiswaActivity, dan ProfilWaliActivity agar tidak ada duplikasi launcher.
 */
class AvatarPickerHelper(
    private val activity: AppCompatActivity,
    private val onCroppedFile: (File) -> Unit
) {

    private val cropLauncher: ActivityResultLauncher<android.content.Intent> =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val path = result.data?.getStringExtra(CropAvatarActivity.EXTRA_CROPPED_PATH)
                val file = path?.let(::File)
                if (file != null && file.exists()) {
                    onCroppedFile(file)
                }
            }
        }

    private val galleryLauncher: ActivityResultLauncher<String> =
        activity.registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                cropLauncher.launch(CropAvatarActivity.intent(activity, uri))
            }
        }

    fun launchPicker() {
        galleryLauncher.launch("image/*")
    }
}
