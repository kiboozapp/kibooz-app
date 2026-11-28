package com.nthb.kibooz.ui.wali

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import com.nthb.kibooz.R
import com.nthb.kibooz.appContainer
import com.nthb.kibooz.data.remote.TrashStats
import com.nthb.kibooz.databinding.ActivityStatistikSampahBinding
import com.nthb.kibooz.ml.TrashCategory
import com.nthb.kibooz.ui.camera.CameraScanSampahActivity
import com.nthb.kibooz.ui.profile.ProfilSiswaActivity
import com.nthb.kibooz.util.InsetsUtil
import kotlinx.coroutines.launch

/** Statistik pilah sampah seorang anak untuk semester yang berjalan. API tidak punya filter harian atau mingguan. */
class StatistikSampahActivity : AppCompatActivity() {

    private val binding by lazy { ActivityStatistikSampahBinding.inflate(layoutInflater) }
    private val viewModel: TrashStatsViewModel by viewModels {
        TrashStatsViewModel.Factory(appContainer.trashRepository, intent.getStringExtra(EXTRA_STUDENT_ID))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        InsetsUtil.applySystemBarInsets(
            root = binding.root,
            headerView = binding.clTopBar,
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
        // Kembali dari pemindai membawa pindaian dan poin yang baru
        if (!viewModel.state.value.loading) viewModel.load()
    }

    private fun setupListeners() {
        binding.ivBack.setOnClickListener {
            finish()
        }

        // Bottom Navigation
        binding.tabHome.setOnClickListener {
            finish()
        }
        binding.tabLeaderboard.setOnClickListener {
            startActivity(Intent(this, LeaderboardActivity::class.java))
            finish()
        }
        binding.tabProfil.setOnClickListener {
            startActivity(Intent(this, ProfilSiswaActivity::class.java))
            finish()
        }
        binding.fabScannerSampah.setOnClickListener {
            startActivity(
                Intent(this, CameraScanSampahActivity::class.java)
                    .putExtra(CameraScanSampahActivity.EXTRA_STUDENT_ID, intent.getStringExtra(EXTRA_STUDENT_ID))
            )
        }
    }

    private fun render(state: TrashStatsState) {
        state.stats?.let(::showStats)

        if (state.noChild) {
            Snackbar.make(binding.root, R.string.error_no_child, Snackbar.LENGTH_INDEFINITE).show()
        }

        state.error?.let {
            Snackbar.make(binding.root, it, Snackbar.LENGTH_INDEFINITE)
                .setAction(R.string.coba_lagi) { viewModel.load() }
                .show()
            viewModel.errorShown()
        }
    }

    private fun showStats(stats: TrashStats) {
        val quota = stats.scanQuota()
        binding.cardSisaScan.isVisible = true
        binding.tvSisaScan.text = getString(quota.messageRes, quota.remaining, quota.dailyLimit)

        // Persentase hanya di diagram, legenda cukup jumlahnya
        val organik = stats.scansOf(TrashCategory.ORGANIK)
        val anorganik = stats.scansOf(TrashCategory.ANORGANIK)
        val b3 = stats.scansOf(TrashCategory.B3)
        binding.pieSampah.setCounts(organik, anorganik, b3)
        binding.tvOrganik.text = getString(R.string.sampah_item, organik)
        binding.tvAnorganik.text = getString(R.string.sampah_item, anorganik)
        binding.tvB3.text = getString(R.string.sampah_item, b3)

        binding.tvBannerTitle.text = getString(R.string.sampah_banner_judul, stats.seasonPoints)
        binding.tvBannerBody.text = if (stats.classRank > 0) {
            getString(
                R.string.sampah_banner_isi,
                stats.student.fullName, stats.totalScans, stats.totalPoints, stats.classRank
            )
        } else {
            getString(
                R.string.sampah_banner_isi_tanpa_peringkat,
                stats.student.fullName, stats.totalScans, stats.totalPoints
            )
        }
    }

    companion object {
        /** Anak yang ditampilkan. Tanpa extra ini server memakai anak yang paling lama terdaftar. */
        const val EXTRA_STUDENT_ID = "STUDENT_ID"
    }
}
