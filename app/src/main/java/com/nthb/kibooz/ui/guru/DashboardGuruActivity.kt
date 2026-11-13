package com.nthb.kibooz.ui.guru

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nthb.kibooz.R
import com.nthb.kibooz.appContainer
import com.nthb.kibooz.databinding.ActivityDashboardGuruBinding
import com.nthb.kibooz.ml.Emotion
import com.nthb.kibooz.ui.camera.CameraScanMoodActivity
import com.nthb.kibooz.ui.common.displayName
import com.nthb.kibooz.ui.common.emoji
import com.nthb.kibooz.util.InsetsUtil
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

class DashboardGuruActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardGuruBinding
    private val viewModel: GuruViewModel by viewModels { GuruViewModel.Factory(appContainer.guruRepository) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardGuruBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Menerapkan Status Bar Inset pada Header dan Navigation Bar Inset pada Bottom Nav & FAB
        InsetsUtil.applySystemBarInsets(
            root = binding.root,
            headerView = binding.clTopHeader,
            bottomBar = binding.clBottomNavigation,
            fab = binding.fabScanner
        )

        setupListeners()

        binding.tvGreeting.setText(greetingFor(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)))
        binding.tvDate.text = SimpleDateFormat(DATE_PATTERN, Locale("id", "ID")).format(Date())

        // Donat memakai warna Sedih di segmen ketiga dan kosong sampai data tiba
        binding.donutMoodChart.setThirdSegmentColor(ContextCompat.getColor(this, R.color.mood_sedih))
        binding.donutMoodChart.updateData(0f, 0f, 0f, 0f)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::render)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Dimuat setiap dasbor tampil lagi, jadi mood yang baru dicatat lewat pemindai langsung terhitung
        viewModel.load()
    }

    private fun setupListeners() {
        binding.btnRetry.setOnClickListener { viewModel.load() }

        // Navigasi ke Statistik Mood
        binding.cvMoodKelas.setOnClickListener {
            startActivity(Intent(this, StatistikMoodActivity::class.java))
        }
        binding.tabStatistik.setOnClickListener {
            startActivity(Intent(this, StatistikMoodActivity::class.java))
        }

        // Navigasi ke Ruang Kelas
        binding.tabKelas.setOnClickListener {
            startActivity(Intent(this, RuangKelasGuruActivity::class.java))
        }

        // Navigasi ke Profil Guru
        binding.tabProfil.setOnClickListener {
            startActivity(Intent(this, ProfilGuruActivity::class.java))
        }
        binding.cvAvatar.setOnClickListener {
            startActivity(Intent(this, ProfilGuruActivity::class.java))
        }

        // Navigasi ke Kamera Scan Face / Mood Siswa
        binding.fabScanner.setOnClickListener {
            startActivity(Intent(this, CameraScanMoodActivity::class.java))
        }
        binding.btnScanUlang.setOnClickListener {
            startActivity(Intent(this, CameraScanMoodActivity::class.java))
        }

        // Banner Ranking Kelas
        binding.cvRankingKelas.setOnClickListener {
            Toast.makeText(this, "Membuka statistik peringkat kelas", Toast.LENGTH_SHORT).show()
        }

        // Notification & History
        binding.ivNotification.setOnClickListener {
            Toast.makeText(this, "Tidak ada notifikasi baru", Toast.LENGTH_SHORT).show()
        }
        binding.ivHistory.setOnClickListener {
            Toast.makeText(this, "Riwayat absensi mood terakhir diperbarui hari ini", Toast.LENGTH_SHORT).show()
        }
    }

    private fun render(state: GuruDashboardState) {
        binding.llStatus.isVisible = state.loading || state.error != null
        binding.pbLoading.isVisible = state.loading
        binding.tvError.isVisible = state.error != null
        binding.tvError.text = state.error?.let { getString(it) }
        binding.btnRetry.isVisible = state.error != null

        state.teacherName?.let { binding.tvTeacherName.text = it }
        state.summary?.let(::bindSummary)
    }

    private fun bindSummary(summary: DashboardSummary) {
        binding.tvTotalSiswa.text = getString(R.string.dashboard_total_siswa, summary.totalStudents)
        binding.tvKehadiran.text = summary.attendancePercent
            ?.let { getString(R.string.dashboard_kehadiran, it) }
            ?: getString(R.string.dashboard_tanpa_data)
        binding.tvMoodDominan.text = summary.dominantMood
            ?.let { "${it.emoji()} ${it.displayName()}" }
            ?: getString(R.string.dashboard_mood_belum_ada)

        // Urutan segmen donat tetap: Senang, Bingung, Sedih, Marah
        binding.donutMoodChart.updateData(
            summary.moodPercent(Emotion.SENANG),
            summary.moodPercent(Emotion.BINGUNG),
            summary.moodPercent(Emotion.SEDIH),
            summary.moodPercent(Emotion.MARAH)
        )
        bindLegend(binding.tvLegendSenang, Emotion.SENANG, summary)
        bindLegend(binding.tvLegendBingung, Emotion.BINGUNG, summary)
        bindLegend(binding.tvLegendSedih, Emotion.SEDIH, summary)
        bindLegend(binding.tvLegendMarah, Emotion.MARAH, summary)

        binding.tvMoodTercatat.text = if (summary.totalStudents == 0) {
            getString(R.string.siswa_kelas_kosong)
        } else {
            getString(R.string.dashboard_mood_tercatat, summary.recordedMoods, summary.totalStudents)
        }
    }

    private fun bindLegend(label: TextView, emotion: Emotion, summary: DashboardSummary) {
        label.text = getString(R.string.dashboard_legenda, emotion.displayName(), summary.moodCounts[emotion] ?: 0)
    }

    private companion object {
        const val DATE_PATTERN = "EEEE, d MMMM yyyy"
    }
}
