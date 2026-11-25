package com.nthb.kibooz.ui.wali

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import com.nthb.kibooz.R
import com.nthb.kibooz.appContainer
import com.nthb.kibooz.data.remote.RecommendedGuidance
import com.nthb.kibooz.databinding.ActivityDashboardWaliBinding
import com.nthb.kibooz.ui.camera.CameraScanSampahActivity
import com.nthb.kibooz.ui.common.displayName
import com.nthb.kibooz.ui.common.emoji
import com.nthb.kibooz.util.InsetsUtil
import com.nthb.kibooz.util.TimeFormat
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

class DashboardWaliActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardWaliBinding
    private val viewModel: WaliDashboardViewModel by viewModels {
        WaliDashboardViewModel.Factory(appContainer.waliRepository, appContainer.classRepository, appContainer.trashRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardWaliBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetsUtil.applySystemBarInsets(
            root = binding.root,
            headerView = binding.clTopHeader,
            bottomBar = binding.clBottomNavigation,
            fab = binding.fabScannerSampah
        )

        setupListeners()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::render)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Kembali dari scan sampah atau panduan membawa poin dan status yang baru
        val state = viewModel.state.value
        if (!state.loading && (state.dashboard != null || state.noChild)) viewModel.load()
    }

    private fun render(state: WaliDashboardState) {
        val data = state.dashboard
        if (data != null) {
            val student = data.student
            val points = data.pointsSummary

            binding.tvNamaSiswa.text = if (state.children.size > 1) "${student.fullName} ▾" else student.fullName
            binding.tvKelasNisn.text = "${student.className} | NISN: ${student.nisn}"

            val rank = if (points.classRank > 0) "#${points.classRank}" else "-"
            binding.tvRank.text = rank
            binding.tvSampahCount.text = points.sortedCount.toString()
            binding.tvTotalPoin.text = points.totalPoints.toString()
            binding.tvBannerTitle.text = getString(R.string.wali_banner_peringkat, rank)
            binding.tvBannerSub.text = getString(R.string.wali_banner_poin_semester, points.seasonPoints)

            val mood = data.todayMood
            binding.tvMoodTimeLabel.text = mood?.let { TimeFormat.localTime(it.recordedAt) }
                ?.let { getString(R.string.wali_mood_hari_ini_jam, it) } ?: getString(R.string.wali_mood_hari_ini)
            binding.tvMoodValue.text = mood?.let { "${it.moodType.emoji()} ${it.moodType.displayName()}" }
                ?: getString(R.string.mood_belum_dicatat)
            binding.tvInsightBody.text = mood?.teacherNotes?.takeIf { it.isNotBlank() }
                ?: getString(R.string.wali_belum_ada_catatan)

            val sorted = points.sortedCount
            binding.tvOrganik.text = countWithShare(points.organicCount, sorted)
            binding.tvAnorganik.text = countWithShare(points.anorganicCount, sorted)

            showGuidance(data.recommendedGuidance)
        }

        val quota = state.scanQuota
        binding.cardSisaScan.isVisible = quota != null
        if (quota != null) {
            binding.tvSisaScan.text = getString(quota.messageRes, quota.remaining, quota.dailyLimit)
        }

        if (state.noChild) {
            Snackbar.make(binding.root, R.string.error_no_child, Snackbar.LENGTH_INDEFINITE)
                .setAction(R.string.anak_tambah_anak) { startActivity(Intent(this, TambahAnakActivity::class.java)) }
                .show()
        }

        state.error?.let {
            Snackbar.make(binding.root, it, Snackbar.LENGTH_INDEFINITE)
                .setAction(R.string.coba_lagi) { viewModel.load() }
                .show()
            viewModel.errorShown()
        }
    }

    private fun countWithShare(count: Int, total: Int): String =
        if (total == 0) "$count" else "$count (${(count * 100f / total).roundToInt()}%)"

    /** Panduan hanya ada selama sudah ada mood hari ini. Tanpa itu kartunya disembunyikan, bukan ditampilkan kosong. */
    private fun showGuidance(guidance: RecommendedGuidance?) {
        val visible = guidance != null
        binding.tvPanduanSection.isVisible = visible
        binding.cardPanduanAman.isVisible = visible
        binding.btnBukaPenanganan.isVisible = visible
        if (guidance != null) {
            binding.tvGuidanceCategory.text = guidance.category
            binding.tvGuidanceTitle.text = guidance.title
        }
    }

    /**
     * Kelas anak yang sedang ditampilkan dikirim supaya Wali dengan beberapa anak melihat kelas yang benar, dan namanya
     * dikirim supaya barisnya disorot di daftar peringkat.
     */
    private fun openLeaderboard() {
        val student = viewModel.state.value.dashboard?.student
        startActivity(
            Intent(this, LeaderboardActivity::class.java)
                .putExtra(LeaderboardActivity.EXTRA_CLASS_ID, student?.classId)
                .putExtra(LeaderboardActivity.EXTRA_HIGHLIGHT_NAME, student?.fullName)
        )
    }

    /** Anak yang sedang ditampilkan, supaya statistiknya milik anak yang sama. */
    private fun openTrashStats() {
        val studentId = viewModel.state.value.dashboard?.student?.id
        startActivity(Intent(this, StatistikSampahActivity::class.java).putExtra(StatistikSampahActivity.EXTRA_STUDENT_ID, studentId))
    }

    /** Profil dibuka untuk anak yang sedang ditampilkan, supaya pilihan anak ikut terbawa. */
    private fun openProfile() {
        val studentId = viewModel.state.value.dashboard?.student?.id
        startActivity(Intent(this, ProfilWaliActivity::class.java).putExtra(ProfilSiswaActivity.EXTRA_STUDENT_ID, studentId))
    }

    private fun openGuidance() {
        val data = viewModel.state.value.dashboard
        val guidance = data?.recommendedGuidance
        if (data == null || guidance == null) {
            Toast.makeText(this, R.string.wali_belum_ada_panduan, Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(
            Intent(this, DetailPenangananMoodActivity::class.java)
                .putExtra(GuidanceExtras.STUDENT_ID, data.student.id)
                .putExtra(GuidanceExtras.GUIDANCE_ID, guidance.id)
                .putExtra(GuidanceExtras.TITLE, guidance.title)
                .putExtra(GuidanceExtras.CATEGORY, guidance.category)
        )
    }

    /** Wali dengan beberapa anak memilih anak yang ditampilkan. Dengan satu anak, ketukan membuka profil. */
    private fun onStudentHeaderClicked() {
        val children = viewModel.state.value.children
        if (children.size < 2) {
            openProfile()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.wali_pilih_anak)
            .setItems(children.map { it.fullName }.toTypedArray()) { _, which -> viewModel.selectChild(children[which].id) }
            .setNegativeButton(R.string.batal, null)
            .show()
    }

    private fun setupListeners() {
        // Navigasi ke Leaderboard / Ranking
        binding.cardMetricRanking.setOnClickListener {
            openLeaderboard()
        }
        binding.cardBannerRanking.setOnClickListener {
            openLeaderboard()
        }
        binding.tabLeaderboard.setOnClickListener {
            openLeaderboard()
        }

        // Navigasi ke Statistik Sampah
        binding.cardMetricSampah.setOnClickListener {
            openTrashStats()
        }
        binding.cardSampah.setOnClickListener {
            openTrashStats()
        }
        binding.btnStatistikSampah.setOnClickListener {
            openTrashStats()
        }
        binding.tabSampah.setOnClickListener {
            openTrashStats()
        }

        // Navigasi ke Panduan & Penanganan Mood
        binding.cardMoodHariIni.setOnClickListener { openGuidance() }
        binding.btnBukaPenanganan.setOnClickListener { openGuidance() }
        binding.cardPanduanAman.setOnClickListener { openGuidance() }

        // Riwayat Aktivitas Si Kecil
        binding.ivHistory.setOnClickListener {
            Toast.makeText(this, R.string.wali_riwayat_belum_tersedia, Toast.LENGTH_SHORT).show()
        }

        // Navigasi ke Ruang Kelas Si Kecil (Screenshot 154028)
        binding.cardRuangKelasWali.setOnClickListener {
            startActivity(Intent(this, RuangKelasWaliActivity::class.java))
        }

        // Navigasi ke Profil Wali (Settings-First seperti Guru)
        binding.llStudentProfile.setOnClickListener { onStudentHeaderClicked() }
        binding.tabProfil.setOnClickListener { openProfile() }

        // Navigasi ke Scanner AI Sampah
        binding.fabScannerSampah.setOnClickListener {
            startActivity(
                Intent(this, CameraScanSampahActivity::class.java)
                    .putExtra(CameraScanSampahActivity.EXTRA_STUDENT_ID, viewModel.state.value.dashboard?.student?.id)
            )
        }

        // Notifikasi
        binding.ivNotification.setOnClickListener {
            Toast.makeText(this, "Tidak ada notifikasi baru untuk wali murid", Toast.LENGTH_SHORT).show()
        }
    }
}
