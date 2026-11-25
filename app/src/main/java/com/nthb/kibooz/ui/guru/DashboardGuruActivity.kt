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
import com.nthb.kibooz.data.remote.ServiceOutage
import com.nthb.kibooz.databinding.ActivityDashboardGuruBinding
import com.nthb.kibooz.ml.Emotion
import com.nthb.kibooz.ui.camera.CameraScanMoodActivity
import com.nthb.kibooz.ui.wali.LeaderboardActivity
import com.nthb.kibooz.ui.common.MoodChartMath
import com.nthb.kibooz.ui.common.PulseAnimator
import com.nthb.kibooz.ui.common.displayName
import com.nthb.kibooz.ui.common.emoji
import com.nthb.kibooz.util.InsetsUtil
import com.nthb.kibooz.util.TimeFormat
import java.util.Calendar
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class DashboardGuruActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardGuruBinding
    private val viewModel: GuruDashboardViewModel by viewModels {
        GuruDashboardViewModel.Factory(
            repository = appContainer.guruRepository,
            classRepository = appContainer.classRepository,
            initialClassId = appContainer.guruClassSelection.current(currentUserId())
        )
    }

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

        binding.tvDate.text = TimeFormat.compactHomeDate()
        binding.tvGreeting.setText(greetingRes(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)))

        binding.connectionStatus.onRetry = { viewModel.load() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect(::render) }
                launch { viewModel.state.map { it.outage }.distinctUntilChanged().collectLatest(::runOutageCountdown) }
            }
        }
    }

    /**
     * Hitung mundur sampai percobaan otomatis berikutnya saat server tidak terjangkau (FE-12). Berhenti sendiri
     * saat layar tidak terlihat, dan dimulai lagi dari awal saat layar tampil kembali.
     */
    private suspend fun runOutageCountdown(outage: ServiceOutage?) {
        if (outage == null) {
            binding.connectionStatus.hide()
            return
        }

        for (secondsLeft in outage.waitSeconds downTo 1) {
            binding.connectionStatus.show(outage.kind, secondsLeft.toInt())
            delay(1_000)
        }
        viewModel.retryAfterOutage()
    }

    private fun currentUserId(): String? = appContainer.authRepository.session.value?.user?.id

    private fun activeClassId(): String? =
        appContainer.guruClassSelection.current(currentUserId())
            ?: viewModel.state.value.selectedClassId
            ?: viewModel.state.value.dashboard?.classOverview?.classId

    override fun onResume() {
        super.onResume()
        // Kembali dari layar scan, statistik, ranking, atau profil membawa kelas terpilih dan angka terbaru
        val sharedClassId = appContainer.guruClassSelection.current(currentUserId())
        val state = viewModel.state.value
        if (sharedClassId != null && sharedClassId != state.selectedClassId) {
            viewModel.selectClass(sharedClassId)
        } else if (!state.loading && state.dashboard != null) {
            viewModel.load(sharedClassId)
        }
    }

    override fun onDestroy() {
        loadingPulse.stop()
        super.onDestroy()
    }

    private fun render(state: GuruDashboardState) {
        if (state.loading && state.dashboard == null) loadingPulse.start() else loadingPulse.stop()

        val userId = currentUserId()
        val data = state.dashboard
        val resolvedClassId = state.selectedClassId ?: data?.classOverview?.classId
        if (resolvedClassId != null && appContainer.guruClassSelection.current(userId) == null) {
            appContainer.guruClassSelection.select(userId, resolvedClassId)
        }

        bindClassSwitcher(
            container = binding.cardClassSwitcher,
            labelView = binding.tvSelectedClass,
            actionView = binding.tvSwitchClassBadge,
            classes = state.classes,
            selectedClassId = resolvedClassId,
            fallbackClassName = data?.classOverview?.className
        ) { chosen ->
            appContainer.guruClassSelection.select(userId, chosen.id)
            viewModel.selectClass(chosen.id)
        }

        if (data != null) {
            val overview = data.classOverview
            val today = data.dailyMoodDistribution
            val percentages = MoodChartMath.percentages(today)

            binding.tvTeacherName.text = data.teacher.fullName
            val avatarUrl = data.teacher.avatarUrl ?: appContainer.authRepository.session.value?.user?.avatarUrl
            appContainer.avatarLoader.load(binding.ivAvatarGuru, avatarUrl, R.drawable.img_avatar_guru)
            binding.tvTotalSiswa.text = "🎯 ${overview.totalStudents}"
            binding.tvHadirLabel.text = getString(R.string.guru_hadir_label, overview.presentStudents, overview.totalStudents)
            binding.tvHadir.text = "✅ " + (DashboardMath.attendancePercent(overview.presentStudents, overview.totalStudents)
                ?.let { getString(R.string.guru_hadir_persen, it) }
                ?: getString(R.string.guru_tanpa_persen))
            binding.tvMoodDominan.text = overview.dominantMood
                ?.let { "${it.emoji()} ${it.displayName()}" }
                ?: getString(R.string.mood_belum_dicatat)
            binding.tvKelasInfo.text = if (today.total == 0) {
                getString(R.string.guru_kelas_kosong_mood, overview.className)
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
            // Saat server tidak terjangkau, kartu gangguan sudah menjelaskan dan menawarkan coba lagi
            if (state.outage == null) {
                Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG)
                    .setAnchorView(binding.clBottomNavigation)
                    .setAction(R.string.coba_lagi) { viewModel.load() }
                    .show()
            }
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
            startActivity(
                Intent(this, StatistikMoodActivity::class.java)
                    .putExtra(StatistikMoodActivity.EXTRA_CLASS_ID, activeClassId())
            )
        }
        binding.tabStatistik.setOnClickListener {
            startActivity(
                Intent(this, StatistikMoodActivity::class.java)
                    .putExtra(StatistikMoodActivity.EXTRA_CLASS_ID, activeClassId())
            )
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
            startActivity(
                Intent(this, CameraScanMoodActivity::class.java)
                    .putExtra(CameraScanMoodActivity.EXTRA_CLASS_ID, activeClassId())
            )
        }
        binding.btnScanUlang.setOnClickListener {
            startActivity(
                Intent(this, CameraScanMoodActivity::class.java)
                    .putExtra(CameraScanMoodActivity.EXTRA_CLASS_ID, activeClassId())
            )
        }

        // Banner Ranking Kelas
        binding.cvRankingKelas.setOnClickListener {
            startActivity(
                Intent(this, LeaderboardActivity::class.java)
                    .putExtra(LeaderboardActivity.EXTRA_CLASS_ID, activeClassId())
            )
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
