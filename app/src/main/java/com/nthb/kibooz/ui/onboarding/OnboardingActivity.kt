package com.nthb.kibooz.ui.onboarding

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import com.nthb.kibooz.ui.auth.RoleSelectionActivity
import com.nthb.kibooz.ui.theme.KiboozTheme

class OnboardingActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { KiboozTheme { OnboardingScreen(onFinished = ::navigateToRoleSelection) } }
    }

    private fun navigateToRoleSelection() {
        startActivity(Intent(this, RoleSelectionActivity::class.java))
        finish()
    }
}
