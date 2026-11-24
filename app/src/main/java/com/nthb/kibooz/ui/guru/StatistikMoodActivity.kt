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
import com.nthb.kibooz.util.InsetsUtil
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

class StatistikMoodActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStatistikMoodBinding
    private val viewModel: MoodStatsViewModel by viewModels {
        MoodStatsViewModel.Factory(appContainer.guruRepository, appContainer.classRepository)
    }

    private var students: List<ClassStudent> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStatistikMoodBinding.inflate(layoutInflater)
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
            startActivity(Intent(this, CameraScanMoodActivity::class.java))
        }
        binding.btnScanUlang.setOnClickListener {
            startActivity(Intent(this, CameraScanMoodActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        // Selesai scan lalu kembali ke sini harus menampilkan hasil yang baru disimpan
        if (!viewModel.state.value.loading) viewModel.load()
    }

    private fun render(state: MoodStatsState) {
        state.today?.let { today ->
            val percentages = MoodChartMath.percentages(today)
            binding.donutMoodChart.setDistribution(today)
            binding.tvLegendSenang.text = legend("🟡", Emotion.SENANG, percentages)
            binding.tvLegendBingung.text = legend("🟠", Emotion.BINGUNG, percentages)
            binding.tvLegendSedih.text = legend("🔵", Emotion.SEDIH, percentages)
            binding.tvLegendMarah.text = legend("🔴", Emotion.MARAH, percentages)
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
}
