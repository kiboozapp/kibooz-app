package com.nthb.kibooz.ui.guru

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.nthb.kibooz.appContainer
import com.nthb.kibooz.databinding.ActivityDetailProfilGuruBinding
import com.nthb.kibooz.util.InsetsUtil
import kotlinx.coroutines.launch

class DetailProfilGuruActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailProfilGuruBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailProfilGuruBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetsUtil.applySystemBarInsets(binding.root, headerView = binding.clTopBar)

        binding.ivBack.setOnClickListener {
            finish()
        }

        binding.ivActionEdit.setOnClickListener {
            startActivity(Intent(this, EditProfilGuruActivity::class.java))
        }

        binding.btnEditProfilGuru.setOnClickListener {
            startActivity(Intent(this, EditProfilGuruActivity::class.java))
        }

        bindProfile()
    }

    override fun onResume() {
        super.onResume()
        bindProfile()
    }

    private fun bindProfile() {
        val sessionUser = appContainer.authRepository.session.value?.user
        if (sessionUser != null) {
            if (!sessionUser.fullName.isNullOrBlank()) binding.tvNamaGuru.text = sessionUser.fullName
            if (!sessionUser.email.isNullOrBlank()) binding.tvEmailValue.text = sessionUser.email
        }

        lifecycleScope.launch {
            if (sessionUser == null) {
                val stored = appContainer.sessionStore.readSession()?.user
                if (stored != null) {
                    if (!stored.fullName.isNullOrBlank()) binding.tvNamaGuru.text = stored.fullName
                    if (!stored.email.isNullOrBlank()) binding.tvEmailValue.text = stored.email
                }
            }
        }
    }
}
