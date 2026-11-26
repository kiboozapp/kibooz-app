package com.nthb.kibooz.ui.location

import android.app.Activity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

/**
 * Pengelola alur buka [PickLocationActivity] dan menerima alamat hasil pilihan peta.
 * Dipakai bersama oleh EditProfilGuruActivity dan EditProfilSiswaActivity agar tidak ada duplikasi launcher.
 */
class LocationPickerHelper(
    private val activity: AppCompatActivity,
    private val onLocationPicked: (address: String) -> Unit
) {

    private val pickerLauncher: ActivityResultLauncher<android.content.Intent> =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val pickedAddress = result.data?.getStringExtra(PickLocationActivity.EXTRA_ADDRESS)?.trim()
                if (!pickedAddress.isNullOrEmpty()) {
                    onLocationPicked(pickedAddress)
                }
            }
        }

    fun launch(initialAddress: String? = null) {
        pickerLauncher.launch(PickLocationActivity.intent(activity, initialAddress))
    }
}
