package com.valentinusmarvel.kibooz.ui.wali

import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.valentinusmarvel.kibooz.R
import com.valentinusmarvel.kibooz.databinding.ActivityLeaderboardBinding
import com.valentinusmarvel.kibooz.util.InsetsUtil

class LeaderboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLeaderboardBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLeaderboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetsUtil.applySystemBarInsets(
            root = binding.clRootLeaderboard,
            headerView = binding.clTopBar
        )

        setupListeners()
    }

    private fun setupListeners() {
        binding.ivBack.setOnClickListener {
            finish()
        }

        binding.tabHariIni.setOnClickListener { selectTab(0) }
        binding.tabMingguIni.setOnClickListener { selectTab(1) }
        binding.tabBulanIni.setOnClickListener { selectTab(2) }
    }

    private fun selectTab(index: Int) {
        val activeBg = R.drawable.bg_leaderboard_tab_active
        val white = Color.WHITE
        val purpleMuted = Color.parseColor("#C7B4F5")

        // Tab 0: Hari ini
        binding.tabHariIni.setBackgroundResource(if (index == 0) activeBg else 0)
        binding.tabHariIni.setTextColor(if (index == 0) white else purpleMuted)

        // Tab 1: Minggu ini
        binding.tabMingguIni.setBackgroundResource(if (index == 1) activeBg else 0)
        binding.tabMingguIni.setTextColor(if (index == 1) white else purpleMuted)

        // Tab 2: Bulan ini
        binding.tabBulanIni.setBackgroundResource(if (index == 2) activeBg else 0)
        binding.tabBulanIni.setTextColor(if (index == 2) white else purpleMuted)

        // Update scores dynamically
        when (index) {
            0 -> {
                binding.tvPoinRank1.text = "🔥 45 Poin"
                binding.tvPoinRank2.text = "🔥 40 Poin"
                binding.tvPoinRank3.text = "🔥 35 Poin"
            }
            1 -> {
                binding.tvPoinRank1.text = "🔥 310 Poin"
                binding.tvPoinRank2.text = "🔥 295 Poin"
                binding.tvPoinRank3.text = "🔥 270 Poin"
            }
            2 -> {
                binding.tvPoinRank1.text = "🔥 899 Poin"
                binding.tvPoinRank2.text = "🔥 850 Poin"
                binding.tvPoinRank3.text = "🔥 829 Poin"
            }
        }
    }
}
