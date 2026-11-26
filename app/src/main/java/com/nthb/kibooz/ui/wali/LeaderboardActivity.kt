package com.nthb.kibooz.ui.wali

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
import com.nthb.kibooz.data.session.Role
import com.nthb.kibooz.databinding.ActivityLeaderboardBinding
import com.nthb.kibooz.databinding.ItemRankingRowBinding
import com.nthb.kibooz.ui.common.animationsEnabled
import com.nthb.kibooz.ui.guru.bindClassSwitcher
import com.nthb.kibooz.util.InsetsUtil
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch
import nl.dionsegijn.konfetti.core.Angle
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.emitter.Emitter
import nl.dionsegijn.konfetti.core.models.Shape

/**
 * Papan peringkat kelas. Bawaannya poin semester yang berjalan, dan tab di atas mengganti rentangnya ke hari,
 * minggu atau bulan ini. Dibuka dari portal Wali dan dari banner ranking di dasbor Guru.
 */
class LeaderboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLeaderboardBinding
    private val viewModel: LeaderboardViewModel by viewModels {
        val user = sessionUser()
        val extraClassId = intent.getStringExtra(EXTRA_CLASS_ID)
        val guruMode = user?.role == Role.GURU
        if (guruMode && !extraClassId.isNullOrBlank() && appContainer.guruClassSelection.current(user?.id) == null) {
            appContainer.guruClassSelection.select(user?.id, extraClassId)
        }
        val initialClassId = if (guruMode) {
            extraClassId ?: appContainer.guruClassSelection.current(user?.id)
        } else {
            extraClassId
        }
        LeaderboardViewModel.Factory(
            repository = appContainer.leaderboardRepository,
            classId = initialClassId,
            classRepository = if (guruMode) appContainer.classRepository else null
        )
    }

    private lateinit var podiumAnimator: PodiumAnimator

    /** True selama podium sedang tampil, supaya animasi hanya diputar saat data berganti dari kosong ke ada. */
    private var podiumShown = false

    private fun sessionUser() = appContainer.authRepository.session.value?.user

    private val isGuru: Boolean get() = sessionUser()?.role == Role.GURU

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLeaderboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetsUtil.applySystemBarInsets(
            root = binding.clRootLeaderboard,
            headerView = binding.clTopBar
        )

        binding.ivBack.setOnClickListener { finish() }

        podiumAnimator = PodiumAnimator(
            slots = listOf(
                PodiumAnimator.Slot(binding.podiumRank1, binding.cardAvatarRank1, binding.tvPoinRank1),
                PodiumAnimator.Slot(binding.podiumRank2, binding.cardAvatarRank2, binding.tvPoinRank2),
                PodiumAnimator.Slot(binding.podiumRank3, binding.cardAvatarRank3, binding.tvPoinRank3)
            ),
            crown = binding.ivCrown
        )
        // Setelah layar diputar, data yang sama masih ada di ViewModel, jadi podium tidak dianimasikan ulang
        podiumShown = savedInstanceState != null && viewModel.state.value.data != null

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
        if (isGuru) {
            val sharedId = appContainer.guruClassSelection.current(sessionUser()?.id)
            if (sharedId != null && sharedId != viewModel.state.value.selectedClassId) {
                viewModel.syncClass(sharedId)
                return
            }
        }
        if (!viewModel.state.value.loading) viewModel.load()
    }

    override fun onDestroy() {
        podiumAnimator.cancel()
        super.onDestroy()
    }

    private val periodTabs: Map<LeaderboardPeriod, TextView>
        get() = mapOf(
            LeaderboardPeriod.SEMESTER to binding.tabSemester,
            LeaderboardPeriod.TODAY to binding.tabHariIni,
            LeaderboardPeriod.WEEK to binding.tabMingguIni,
            LeaderboardPeriod.MONTH to binding.tabBulanIni
        )

    private fun render(state: LeaderboardState) {
        if (isGuru) {
            val userId = sessionUser()?.id
            if (!state.selectedClassId.isNullOrBlank() && appContainer.guruClassSelection.current(userId) == null) {
                appContainer.guruClassSelection.select(userId, state.selectedClassId)
            }
            bindClassSwitcher(
                container = binding.cardClassSwitcher,
                labelView = binding.tvSelectedClass,
                actionView = binding.tvSwitchClassBadge,
                classes = state.classes,
                selectedClassId = state.selectedClassId
            ) { chosen ->
                appContainer.guruClassSelection.select(userId, chosen.id)
                viewModel.selectClass(chosen.id)
            }
        } else {
            binding.cardClassSwitcher.isVisible = false
        }

        showPeriod(state.period)

        val data = state.data
        if (data != null) {
            showPodium(data.podium)
            showRankings(state.allRanks)
            binding.tvEmpty.isVisible = state.empty
        }
        // Reload saat kembali ke layar ini tidak mengosongkan data, jadi tidak memutar ulang. Ganti tab atau kelas
        // mengosongkan data dulu di ViewModel, sehingga podium yang baru ikut dianimasikan.
        if (data != null && !podiumShown) {
            podiumShown = true
            animatePodium(data.podium)
        } else if (data == null && podiumShown) {
            podiumShown = false
            podiumAnimator.cancel()
            binding.konfettiView.stopGracefully()
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
            listOf(binding.podiumRank1, binding.tvNameRank1, binding.tvPoinRank1, binding.ivAvatarRank1),
            listOf(binding.podiumRank2, binding.tvNameRank2, binding.tvPoinRank2, binding.ivAvatarRank2),
            listOf(binding.podiumRank3, binding.tvNameRank3, binding.tvPoinRank3, binding.ivAvatarRank3)
        )
        slots.forEachIndexed { index, views ->
            val container = views[0]
            val name = views[1] as TextView
            val points = views[2] as TextView
            val avatar = views[3] as android.widget.ImageView
            val entry = podium.getOrNull(index)
            container.isVisible = entry != null
            if (entry != null) {
                name.text = entry.studentName
                points.text = getString(R.string.leaderboard_poin, entry.points)
                appContainer.avatarLoader.load(avatar, entry.avatarUrl, R.drawable.img_avatar_siswa)
            }
        }
        binding.ivCrown.isVisible = podium.isNotEmpty()
    }

    private fun animatePodium(podium: List<RankEntry>) {
        podiumAnimator.play(
            points = podium.map { it.points },
            formatPoints = { getString(R.string.leaderboard_poin, it) },
            onFinished = { celebrateIfChildOnPodium(podium) }
        )
    }

    /**
     * Confetti hanya untuk Wali yang anaknya masuk 3 besar. Guru tidak mengirim nama sorotan, jadi tidak pernah melihatnya.
     * Konfetti memakai timer sendiri dan tidak ikut setelan animasi perangkat, sehingga dicek manual di sini.
     */
    private fun celebrateIfChildOnPodium(podium: List<RankEntry>) {
        if (!podium.hasStudent(intent.getStringExtra(EXTRA_HIGHLIGHT_NAME)) || !animationsEnabled()) return

        val colors = listOf(
            R.color.kibooz_yellow_gold,
            R.color.kibooz_primary_purple,
            R.color.kibooz_teal_guru,
            R.color.mood_marah,
            R.color.mood_happy,
            R.color.kibooz_white
        ).map { ContextCompat.getColor(this, it) }

        // Hujan confetti dari sepanjang tepi atas layar. Jumlah diatur dengan perSecond(), bukan max(n): Konfetti
        // menghitung max(n) dengan pembagian bulat, dan jika n lebih besar dari durasi dalam ms hasilnya 0, lalu partikel
        // dibuat tanpa batas sampai aplikasi kehabisan memori.
        binding.konfettiView.start(
            Party(
                angle = Angle.BOTTOM,
                spread = 45,
                speed = 2f,
                maxSpeed = 8f,
                damping = 0.95f,
                colors = colors,
                shapes = listOf(Shape.Square, Shape.Circle),
                timeToLive = 3000L,
                position = Position.Relative(0.0, 0.0).between(Position.Relative(1.0, 0.0)),
                emitter = Emitter(duration = 2000, TimeUnit.MILLISECONDS).perSecond(100)
            )
        )
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
            appContainer.avatarLoader.load(row.ivAvatar, entry.avatarUrl, R.drawable.img_avatar_siswa)
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

/** True jika [name] ada di daftar ini. Dicocokkan dari nama seperti sorotan baris, karena balasan server tidak membawa id. */
internal fun List<RankEntry>.hasStudent(name: String?): Boolean = name != null && any { it.studentName == name }
