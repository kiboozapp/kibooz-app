package com.nthb.kibooz.ui.auth

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import com.nthb.kibooz.data.session.Role
import com.nthb.kibooz.ui.theme.KiboozTheme

class RoleSelectionActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { KiboozTheme { RoleSelectionScreen(onRoleSelected = ::openLogin) } }
    }

    private fun openLogin(role: Role) {
        startActivity(Intent(this, LoginActivity::class.java).putExtra(LoginActivity.EXTRA_SELECTED_ROLE, role.name))
    }
}
