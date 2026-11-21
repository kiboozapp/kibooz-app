package com.nthb.kibooz.ui.wali

import android.os.Bundle
import android.view.LayoutInflater
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import com.nthb.kibooz.R
import com.nthb.kibooz.appContainer
import com.nthb.kibooz.data.remote.RankEntry
import com.nthb.kibooz.databinding.ActivityLeaderboardBinding
import com.nthb.kibooz.databinding.ItemRankingRowBinding
import com.nthb.kibooz.util.InsetsUtil
import kotlinx.coroutines.launch

/** Papan peringkat kelas untuk semester yang berjalan. API tidak punya filter harian atau mingguan. */
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

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::render)
            }
        }
    }

    private fun render(state: LeaderboardState) {
        val data = state.data
        if (data != null) {
            showPodium(data.podium)
            showRankings(data.rankings)
            binding.tvEmpty.isVisible = state.empty
        }

        state.error?.let {
            Snackbar.make(binding.root, it, Snackbar.LENGTH_INDEFINITE)
                .setAction(R.string.coba_lagi) { viewModel.load() }
                .show()
            viewModel.errorShown()
        }
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

    private fun showRankings(rankings: List<RankEntry>) {
        val rows = binding.llRankings
        rows.removeAllViews()
        rankings.forEach { entry ->
            val row = ItemRankingRowBinding.inflate(LayoutInflater.from(this), rows, false)
            row.tvRank.text = entry.rank.toString()
            row.tvName.text = entry.studentName
            row.tvPoints.text = getString(R.string.leaderboard_poin, entry.points)
            rows.addView(row.root)
        }
    }

    companion object {
        /** Id kelas yang ditampilkan. Tanpa extra ini server memilih kelas anak yang berlaku. */
        const val EXTRA_CLASS_ID = "CLASS_ID"
    }
}
