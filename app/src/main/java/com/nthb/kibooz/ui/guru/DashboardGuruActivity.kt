package com.nthb.kibooz.ui.guru

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import com.nthb.kibooz.R
import com.nthb.kibooz.appContainer
import com.nthb.kibooz.databinding.ActivityDashboardGuruBinding
import com.nthb.kibooz.ml.Emotion
import com.nthb.kibooz.ui.camera.CameraScanMoodActivity
import com.nthb.kibooz.ui.common.MoodChartMath
import com.nthb.kibooz.ui.common.PulseAnimator
import com.nthb.kibooz.ui.common.displayName
import com.nthb.kibooz.ui.common.emoji
import com.nthb.kibooz.util.InsetsUtil
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

class DashboardGuruActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardGuruBinding
    private val viewModel: GuruDashboardViewModel by viewModels { GuruDashboardViewModel.Factory(appContainer.guruRepository) }

    // Angka dan donat berkedip selama data pertama belum tiba. Muat ulang berikutnya tidak mengosongkan layar
    private val loadingPulse by lazy {
        PulseAnimator(listOf(binding.tvTotalSiswa, binding.tvMoodDominan, binding.tvHadir, binding.donutMoodChart))
    }

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

        binding.tvDate.text = SimpleDateFormat("EEEE, d MMMM yyyy", Locale("id", "ID")).format(Date())
        binding.tvGreeting.setText(greetingRes(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)))

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::render)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Kembali dari layar scan atau statistik membawa angka yang baru
        if (!viewModel.state.value.loading && viewModel.state.value.dashboard != null) viewModel.load()
    }

    override fun onDestroy() {
        loadingPulse.stop()
        super.onDestroy()
    }

    private fun render(state: GuruDashboardState) {
        if (state.loading && state.dashboard == null) loadingPulse.start() else loadingPulse.stop()

        val data = state.dashboard
        if (data != null) {
            val overview = data.classOverview
            val today = data.dailyMoodDistribution
            val percentages = MoodChartMath.percentages(today)

            binding.tvTeacherName.text = data.teacher.fullName
            binding.tvTotalSiswa.text = "🎯 ${overview.totalStudents}"
            binding.tvHadirLabel.text = getString(R.string.guru_hadir_label, overview.presentStudents, overview.totalStudents)
            binding.tvHadir.text = "✅ " + (DashboardMath.attendancePercent(overview.presentStudents, overview.totalStudents)
                ?.let { getString(R.string.guru_hadir_persen, it) }
                ?: getString(R.string.guru_tanpa_persen))
            binding.tvMoodDominan.text = overview.dominantMood
                ?.let { "${it.emoji()} ${it.displayName()}" }
                ?: getString(R.string.mood_belum_dicatat)
            binding.tvKelasInfo.text = if (today.total == 0) {
                getString(R.string.guru_belum_ada_mood)
            } else {
                getString(R.string.guru_kelas_info, overview.className, overview.joinCode)
            }
            binding.donutMoodChart.setDistribution(today)
            binding.tvLegendSenang.text = legend(R.string.legend_senang, percentages, Emotion.SENANG)
            binding.tvLegendBingung.text = legend(R.string.legend_bingung, percentages, Emotion.BINGUNG)
            binding.tvLegendSedih.text = legend(R.string.legend_sedih, percentages, Emotion.SEDIH)
            binding.tvLegendMarah.text = legend(R.string.legend_marah, percentages, Emotion.MARAH)
        }

        state.error?.let {
            Snackbar.make(binding.root, it, Snackbar.LENGTH_INDEFINITE)
                .setAction(R.string.coba_lagi) { viewModel.load() }
                .show()
            viewModel.errorShown()
        }
    }

    private fun legend(label: Int, percentages: Map<Emotion, Float>, emotion: Emotion) =
        "${getString(label)} (${percentages.getValue(emotion).roundToInt()}%)"

    private fun greetingRes(hour: Int) = when (hour) {
        in 4..10 -> R.string.sapaan_pagi
        in 11..14 -> R.string.sapaan_siang
        in 15..17 -> R.string.sapaan_sore
        else -> R.string.sapaan_malam
    }

    private fun setupListeners() {
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
}
