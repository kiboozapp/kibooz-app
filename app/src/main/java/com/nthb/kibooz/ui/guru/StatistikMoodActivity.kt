package com.nthb.kibooz.ui.guru

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import com.nthb.kibooz.R
import com.nthb.kibooz.appContainer
import com.nthb.kibooz.data.remote.ClassStudent
import com.nthb.kibooz.databinding.ActivityStatistikMoodBinding
import com.nthb.kibooz.databinding.ItemStudentMoodRowBinding
import com.nthb.kibooz.ml.Emotion
import com.nthb.kibooz.ui.camera.CameraScanMoodActivity
import com.nthb.kibooz.ui.common.MoodChartMath
import com.nthb.kibooz.ui.common.displayName
import com.nthb.kibooz.ui.common.emoji
import com.nthb.kibooz.ui.profile.ProfilGuruActivity
import com.nthb.kibooz.util.InsetsUtil
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

class StatistikMoodActivity : AppCompatActivity() {

    private val binding by lazy { ActivityStatistikMoodBinding.inflate(layoutInflater) }
    private val viewModel: MoodStatsViewModel by viewModels {
        val userId = currentUserId()
        val extraClassId = intent.getStringExtra(EXTRA_CLASS_ID)
        if (!extraClassId.isNullOrBlank() && appContainer.guruClassSelection.current(userId) == null) {
            appContainer.guruClassSelection.select(userId, extraClassId)
        }
        MoodStatsViewModel.Factory(
            guruRepository = appContainer.guruRepository,
            classRepository = appContainer.classRepository,
            initialClassId = extraClassId ?: appContainer.guruClassSelection.current(userId),
            loadClassOptions = true
        )
    }

    private fun currentUserId(): String? = appContainer.authRepository.session.value?.user?.id

    private fun activeClassId(): String? =
        viewModel.state.value.selectedClassId ?: appContainer.guruClassSelection.current(currentUserId())

    private var students: List<ClassStudent> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        InsetsUtil.applySystemBarInsets(
            root = binding.root,
            headerView = binding.clTopBar,
            bottomBar = binding.clBottomNav,
            fab = binding.fabScan
        )

        setupListeners()

        // Menyentuh irisan donat menampilkan emosi dan persentasenya (FR-3.4)
        binding.donutMoodChart.onSliceSelected = { emotion, percent ->
            Toast.makeText(
                this,
                getString(R.string.stat_irisan_detail, emotion.displayName(), MoodChartMath.percentText(percent)),
                Toast.LENGTH_SHORT
            ).show()
        }

        binding.etCariSiswa.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = showStudents(s?.toString().orEmpty())
        })

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::render)
            }
        }
    }

    private fun setupListeners() {
        binding.ivBack.setOnClickListener {
            finish()
        }

        binding.tabNavHome.setOnClickListener {
            finish()
        }

        binding.tabNavKelas.setOnClickListener {
            startActivity(Intent(this, RuangKelasGuruActivity::class.java))
            finish()
        }

        binding.tabNavProfil.setOnClickListener {
            startActivity(Intent(this, ProfilGuruActivity::class.java))
            finish()
        }

        binding.fabScan.setOnClickListener {
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
    }

    override fun onResume() {
        super.onResume()
        // Selesai scan atau berpindah kelas di layar lain menampilkan hasil untuk kelas terpilih
        val sharedClassId = appContainer.guruClassSelection.current(currentUserId())
        val state = viewModel.state.value
        if (sharedClassId != null && sharedClassId != state.selectedClassId) {
            viewModel.selectClass(sharedClassId)
        } else if (!state.loading) {
            viewModel.load(sharedClassId)
        }
    }

    private fun render(state: MoodStatsState) {
        val userId = currentUserId()
        if (!state.selectedClassId.isNullOrBlank() && appContainer.guruClassSelection.current(userId) == null) {
            appContainer.guruClassSelection.select(userId, state.selectedClassId)
        }

        bindClassSwitcher(
            container = binding.cardClassSwitcher,
            labelView = binding.tvSelectedClass,
            actionView = binding.tvSwitchClassBadge,
            classes = state.classes,
            selectedClassId = state.selectedClassId,
            fallbackClassName = state.className
        ) { chosen ->
            appContainer.guruClassSelection.select(userId, chosen.id)
            viewModel.selectClass(chosen.id)
        }

        val todayDistribution = state.today
        if (todayDistribution != null) {
            val percentages = MoodChartMath.percentages(todayDistribution)
            binding.donutMoodChart.setDistribution(todayDistribution)
            binding.tvLegendSenang.text = legend("🟡", Emotion.SENANG, percentages)
            binding.tvLegendBingung.text = legend("🟠", Emotion.BINGUNG, percentages)
            binding.tvLegendSedih.text = legend("🔵", Emotion.SEDIH, percentages)
            binding.tvLegendMarah.text = legend("🔴", Emotion.MARAH, percentages)
        } else {
            val emptyDistribution = com.nthb.kibooz.data.remote.DailyMoodDistribution(0, 0, 0, 0)
            val emptyPercentages = MoodChartMath.percentages(emptyDistribution)
            binding.donutMoodChart.setDistribution(emptyDistribution)
            binding.tvLegendSenang.text = legend("🟡", Emotion.SENANG, emptyPercentages)
            binding.tvLegendBingung.text = legend("🟠", Emotion.BINGUNG, emptyPercentages)
            binding.tvLegendSedih.text = legend("🔵", Emotion.SEDIH, emptyPercentages)
            binding.tvLegendMarah.text = legend("🔴", Emotion.MARAH, emptyPercentages)
        }
        binding.weeklyLineChart.setData(state.weekly)
        binding.monthlyBarChart.setData(state.monthly)

        students = state.students
        showStudents(binding.etCariSiswa.text?.toString().orEmpty())

        state.error?.let {
            Snackbar.make(binding.root, it, Snackbar.LENGTH_INDEFINITE)
                .setAction(R.string.coba_lagi) { viewModel.load() }
                .show()
            viewModel.errorShown()
        }
    }

    private fun legend(dot: String, emotion: Emotion, percentages: Map<Emotion, Float>) =
        "$dot ${emotion.displayName()} (${percentages.getValue(emotion).roundToInt()}%)"

    /** Mengisi tabel dari daftar siswa, disaring dengan nama yang diketik. Yang belum dicatat ditulis, bukan dikosongkan. */
    private fun showStudents(query: String) {
        val rows = binding.llStudentRows
        rows.removeAllViews()

        val shown = students.filter { it.fullName.contains(query.trim(), ignoreCase = true) }
        shown.forEachIndexed { index, student ->
            val row = ItemStudentMoodRowBinding.inflate(LayoutInflater.from(this), rows, false)
            row.tvNo.text = (index + 1).toString()
            row.tvNama.text = student.fullName
            row.tvMood.text = student.todayMood?.let { "${it.emoji()} ${it.displayName()}" }
                ?: getString(R.string.mood_belum_dicatat)
            rows.addView(row.root)
        }

        if (shown.isEmpty() && !viewModel.state.value.loading) {
            val empty = ItemStudentMoodRowBinding.inflate(LayoutInflater.from(this), rows, false)
            empty.tvNo.visibility = View.GONE
            empty.ivAvatar.visibility = View.GONE
            empty.tvMood.visibility = View.GONE
            empty.tvNama.gravity = Gravity.CENTER
            (empty.tvNama.layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.marginStart = 0
            empty.tvNama.text = getString(
                if (students.isEmpty()) R.string.stat_belum_ada_siswa else R.string.stat_siswa_tidak_ditemukan
            )
            rows.addView(empty.root)
        }
    }

    companion object {
        const val EXTRA_CLASS_ID = "CLASS_ID"
    }
}
