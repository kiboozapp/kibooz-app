package com.nthb.kibooz.ui.wali

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import com.nthb.kibooz.R
import com.nthb.kibooz.appContainer
import com.nthb.kibooz.data.remote.LeaderboardPeriod
import com.nthb.kibooz.data.remote.RankEntry
import com.nthb.kibooz.databinding.ActivityLeaderboardBinding
import com.nthb.kibooz.databinding.ItemRankingRowBinding
import com.nthb.kibooz.util.InsetsUtil
import kotlinx.coroutines.launch

/**
 * Papan peringkat kelas. Bawaannya poin semester yang berjalan, dan tab di atas mengganti rentangnya ke hari,
 * minggu atau bulan ini. Dibuka dari portal Wali dan dari banner ranking di dasbor Guru.
 */
class LeaderboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLeaderboardBinding
    private val viewModel: LeaderboardViewModel by viewModels {
        LeaderboardViewModel.Factory(appContainer.leaderboardRepository, intent.getStringExtra(EXTRA_CLASS_ID))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLeaderboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetsUtil.applySystemBarInsets(
            root = binding.clRootLeaderboard,
            headerView = binding.clTopBar
        )

        binding.ivBack.setOnClickListener { finish() }

        periodTabs.forEach { (period, tab) -> tab.setOnClickListener { viewModel.selectPeriod(period) } }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::render)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Muat ulang setiap kali layar kembali terlihat supaya poin anak lain yang masuk sementara itu ikut tampil.
        // Dilewati saat masih memuat, sehingga pembukaan pertama (sudah dimuat oleh init) tidak mengirim dua request.
        if (!viewModel.state.value.loading) viewModel.load()
    }

    private val periodTabs: Map<LeaderboardPeriod, TextView>
        get() = mapOf(
            LeaderboardPeriod.SEMESTER to binding.tabSemester,
            LeaderboardPeriod.TODAY to binding.tabHariIni,
            LeaderboardPeriod.WEEK to binding.tabMingguIni,
            LeaderboardPeriod.MONTH to binding.tabBulanIni
        )

    private fun render(state: LeaderboardState) {
        showPeriod(state.period)

        val data = state.data
        if (data != null) {
            showPodium(data.podium)
            showRankings(state.allRanks)
            binding.tvEmpty.isVisible = state.empty
        }
        // Tanpa data, misalnya saat rentang baru dimuat, daftar lama tidak boleh terlihat di bawah tab yang baru
        binding.llPodiumSection.isInvisible = data == null
        binding.cardBottomSheetLeaderboard.isInvisible = data == null

        state.error?.let {
            Snackbar.make(binding.root, it, Snackbar.LENGTH_INDEFINITE)
                .setAction(R.string.coba_lagi) { viewModel.load() }
                .show()
            viewModel.errorShown()
        }
    }

    private fun showPeriod(selected: LeaderboardPeriod) {
        periodTabs.forEach { (period, tab) ->
            val active = period == selected
            tab.setBackgroundResource(if (active) R.drawable.bg_leaderboard_tab_active else 0)
            tab.setTextColor(if (active) Color.WHITE else INACTIVE_TAB_COLOR)
        }
        binding.tvTitleBar.setText(
            when (selected) {
                LeaderboardPeriod.SEMESTER -> R.string.leaderboard_judul
                LeaderboardPeriod.TODAY -> R.string.leaderboard_judul_hari
                LeaderboardPeriod.WEEK -> R.string.leaderboard_judul_minggu
                LeaderboardPeriod.MONTH -> R.string.leaderboard_judul_bulan
            }
        )
    }

    /** Juara yang belum ada disembunyikan, kelas kecil bisa punya kurang dari tiga anak. */
    private fun showPodium(podium: List<RankEntry>) {
        val slots = listOf(
            Triple(binding.podiumRank1, binding.tvNameRank1, binding.tvPoinRank1),
            Triple(binding.podiumRank2, binding.tvNameRank2, binding.tvPoinRank2),
            Triple(binding.podiumRank3, binding.tvNameRank3, binding.tvPoinRank3)
        )
        slots.forEachIndexed { index, (container, name, points) ->
            val entry = podium.getOrNull(index)
            container.isVisible = entry != null
            if (entry != null) {
                name.text = entry.studentName
                points.text = getString(R.string.leaderboard_poin, entry.points)
            }
        }
        binding.ivCrown.isVisible = podium.isNotEmpty()
    }

    /**
     * Daftar semua siswa di bawah podium, termasuk tiga besar. Baris anak milik Wali disorot. Balasan server tidak
     * membawa id siswa, jadi barisnya dikenali dari nama, dan dua anak sekelas dengan nama yang persis sama ikut tersorot.
     */
    private fun showRankings(entries: List<RankEntry>) {
        val highlight = intent.getStringExtra(EXTRA_HIGHLIGHT_NAME)
        val rows = binding.llRankings
        rows.removeAllViews()
        binding.tvRankingsTitle.isVisible = entries.isNotEmpty()

        entries.forEach { entry ->
            val row = ItemRankingRowBinding.inflate(LayoutInflater.from(this), rows, false)
            val isMine = highlight != null && entry.studentName == highlight

            row.tvRank.text = entry.rank.toString()
            row.tvName.text = if (isMine) getString(R.string.leaderboard_anakmu, entry.studentName) else entry.studentName
            row.tvPoints.text = getString(R.string.leaderboard_poin, entry.points)
            if (isMine) {
                row.root.backgroundTintList = ColorStateList.valueOf(HIGHLIGHT_ROW_COLOR)
            }
            rows.addView(row.root)
        }
    }

    companion object {
        private val INACTIVE_TAB_COLOR = Color.parseColor("#C7B4F5")

        /** Soft Lilac dari palet desain, latar baris anak milik Wali. */
        private val HIGHLIGHT_ROW_COLOR = Color.parseColor("#EFEBFF")

        /** Id kelas yang ditampilkan. Tanpa extra ini server memilih kelas anak yang berlaku. */
        const val EXTRA_CLASS_ID = "CLASS_ID"

        /** Nama anak milik Wali yang barisnya disorot. Opsional, Guru tidak mengirimnya. */
        const val EXTRA_HIGHLIGHT_NAME = "HIGHLIGHT_NAME"
    }
}
