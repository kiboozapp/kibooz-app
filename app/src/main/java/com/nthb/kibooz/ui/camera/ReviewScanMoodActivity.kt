package com.nthb.kibooz.ui.camera

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.nthb.kibooz.R
import com.nthb.kibooz.appContainer
import com.nthb.kibooz.databinding.ActivityReviewScanMoodBinding
import com.nthb.kibooz.model.ScannedStudentMood
import com.nthb.kibooz.ui.guru.DashboardGuruActivity
import com.nthb.kibooz.util.InsetsUtil
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

class ReviewScanMoodActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReviewScanMoodBinding
    private val studentList = mutableListOf<ScannedStudentMood>()
    private lateinit var adapter: ReviewScanMoodAdapter

    private val viewModel: ReviewMoodViewModel by viewModels { ReviewMoodViewModel.Factory(appContainer.moodRepository) }
    private val classId: String? get() = intent.getStringExtra(EXTRA_CLASS_ID)

    private var saving = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReviewScanMoodBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Apply edge-to-edge system insets
        InsetsUtil.applySystemBarInsets(
            root = binding.clRootReview,
            headerView = binding.clTopBar,
            bottomBar = binding.clBottomBar
        )

        setupDateHeader()
        loadScannedData()
        setupRecyclerView()
        setupListeners()
        updateSummaryStats()
        observeSaveState()
    }

    private fun setupDateHeader() {
        val dateFormat = SimpleDateFormat("EEEE, d MMMM yyyy", Locale("id", "ID"))
        binding.tvSubHeaderInfo.text = dateFormat.format(Date())
    }

    private fun loadScannedData() {
        // Hanya hasil dari kamera yang ditampilkan. Tanpa hasil, layar menampilkan keadaan kosong
        @Suppress("DEPRECATION")
        val passedList = intent.getSerializableExtra(EXTRA_STUDENTS) as? ArrayList<ScannedStudentMood>

        if (!passedList.isNullOrEmpty()) {
            studentList.addAll(passedList)
        }
    }

    private fun setupRecyclerView() {
        adapter = ReviewScanMoodAdapter(
            students = studentList,
            onMoodChanged = { _, _ ->
                updateSummaryStats()
            },
            onConfirmed = { _ ->
                updateSummaryStats()
            },
            onDeleteClicked = { student, position ->
                showDeleteConfirmationDialog(student, position)
            }
        )

        binding.rvScannedStudents.layoutManager = LinearLayoutManager(this)
        binding.rvScannedStudents.adapter = adapter
    }

    private fun setupListeners() {
        binding.ivBack.setOnClickListener {
            finish()
        }

        binding.btnScanUlang.setOnClickListener {
            // Kembali ke kamera untuk memindai ulang
            finish()
        }

        // Siswa yang terlewat harus dipilih dari daftar kelas yang sebenarnya (id dari server), bukan dari
        // nama yang diketik. Pemilih itu menyusul bersama pengambilan daftar siswa di layar kamera
        binding.btnTambahSiswaManual.visibility = View.GONE
        binding.ivAddStudent.visibility = View.GONE

        binding.btnConfirmAll.setOnClickListener {
            confirmAndSaveAll()
        }
    }

    private fun updateSummaryStats() {
        val total = studentList.size
        val unconfirmed = studentList.count { it.needsConfirmation() }
        binding.tvTotalDetected.text = "$total Siswa Siap Disimpan"
        binding.btnConfirmAll.text = when {
            saving -> getString(R.string.review_menyimpan)
            unconfirmed > 0 -> getString(R.string.review_konfirmasi_dulu, unconfirmed)
            else -> getString(R.string.review_simpan, total)
        }

        if (total == 0) {
            binding.rvScannedStudents.visibility = View.GONE
            binding.llEmptyState.visibility = View.VISIBLE
            binding.btnConfirmAll.isEnabled = false
            binding.tvCountHappy.text = "😊 0 Senang"
            binding.tvCountKalem.text = "😌 0 Kalem"
            binding.tvCountBingung.text = "🤔 0 Ragu"
            binding.tvCountMarah.text = "😠 0 Kesal"
            return
        }

        binding.rvScannedStudents.visibility = View.VISIBLE
        binding.llEmptyState.visibility = View.GONE
        // BR-04: hasil di bawah 75% harus dikonfirmasi atau diganti dulu
        binding.btnConfirmAll.isEnabled = !saving && unconfirmed == 0

        val happyCount = studentList.count { it.currentMood == "SENANG" }
        val kalemCount = studentList.count { it.currentMood == "KALEM" }
        val bingungCount = studentList.count { it.currentMood == "BINGUNG" }
        val marahCount = studentList.count { it.currentMood == "MARAH" }

        binding.tvCountHappy.text = "😊 $happyCount Senang"
        binding.tvCountKalem.text = "😌 $kalemCount Kalem"
        binding.tvCountBingung.text = "🤔 $bingungCount Ragu"
        binding.tvCountMarah.text = "😠 $marahCount Kesal"
    }

    private fun showDeleteConfirmationDialog(student: ScannedStudentMood, position: Int) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Hapus dari Daftar Scan?")
            .setMessage("Data emosi ${student.studentName} akan dihapus dari sesi review ini.")
            .setPositiveButton("Hapus") { _, _ ->
                studentList.removeAt(position)
                adapter.notifyItemRemoved(position)
                updateSummaryStats()
                Toast.makeText(this, "${student.studentName} dihapus dari daftar", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun confirmAndSaveAll() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Simpan Data Presensi Mood?")
            .setMessage("Seluruh data mood ${studentList.size} siswa akan disimpan ke statistik kelas dan dibagikan ke wali murid.")
            .setPositiveButton("Ya, Simpan") { _, _ -> viewModel.save(classId, studentList) }
            .setNegativeButton("Periksa Lagi", null)
            .show()
    }

    private fun observeSaveState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    saving = state.saving
                    updateSummaryStats()

                    state.savedCount?.let { count -> onSaved(count) }
                    state.error?.let { error -> showError(error, state.limitReachedFor) }
                }
            }
        }
    }

    private fun onSaved(count: Int) {
        Toast.makeText(this, getString(R.string.review_berhasil, count), Toast.LENGTH_LONG).show()

        // Navigasi tuntas kembali ke Dashboard Guru
        val intent = Intent(this, DashboardGuruActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
        finish()
    }

    private fun showError(errorRes: Int, limitReachedFor: List<String>) {
        val names = studentList.filter { it.id in limitReachedFor }.joinToString { it.studentName }
        val message = if (names.isNotEmpty()) {
            getString(R.string.review_batas_siswa, names)
        } else {
            getString(errorRes)
        }

        Snackbar.make(binding.root, message, Snackbar.LENGTH_INDEFINITE)
            .setAnchorView(binding.clBottomBar)
            .setAction(R.string.review_coba_lagi) { viewModel.save(classId, studentList) }
            .show()
        viewModel.errorShown()
    }

    companion object {
        const val EXTRA_STUDENTS = "EXTRA_STUDENTS"
        const val EXTRA_CLASS_ID = "EXTRA_CLASS_ID"
    }
}
