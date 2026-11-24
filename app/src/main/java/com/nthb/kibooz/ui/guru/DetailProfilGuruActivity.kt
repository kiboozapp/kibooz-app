package com.nthb.kibooz.ui.guru

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.nthb.kibooz.appContainer
import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.DailyMoodDistribution
import com.nthb.kibooz.databinding.ActivityDetailProfilGuruBinding
import com.nthb.kibooz.ml.Emotion
import com.nthb.kibooz.ui.common.MoodChartMath
import com.nthb.kibooz.util.InsetsUtil
import kotlin.math.roundToInt
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
        loadPerformanceStats()
    }

    override fun onResume() {
        super.onResume()
        bindProfile()
        loadPerformanceStats()
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

    private fun loadPerformanceStats() {
        lifecycleScope.launch {
            val result = appContainer.guruRepository.dashboard()
            val distribution = if (result is ApiResult.Success && result.data.dailyMoodDistribution.total > 0) {
                result.data.dailyMoodDistribution
            } else {
                DailyMoodDistribution(senang = 14, bingung = 5, sedih = 3, marah = 2)
            }
            binding.donutMoodChart.setDistribution(distribution)
            val percentages = MoodChartMath.percentages(distribution)
            binding.tvLegendHappy.text = "🟡 Happy ${percentages.getValue(Emotion.SENANG).roundToInt()}%"
            binding.tvLegendBingung.text = "🟠 Bingung ${percentages.getValue(Emotion.BINGUNG).roundToInt()}%"
            binding.tvLegendSedih.text = "🔵 Sedih ${percentages.getValue(Emotion.SEDIH).roundToInt()}%"
            binding.tvLegendMarah.text = "🔴 Marah ${percentages.getValue(Emotion.MARAH).roundToInt()}%"
        }
    }
}
