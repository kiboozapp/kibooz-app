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

    private val binding by lazy { ActivityReviewScanMoodBinding.inflate(layoutInflater) }
    private val studentList = mutableListOf<ScannedStudentMood>()
    private lateinit var adapter: ReviewScanMoodAdapter

    private val viewModel: ReviewMoodViewModel by viewModels { ReviewMoodViewModel.Factory(appContainer.moodRepository) }
    private val roster: ReviewRosterViewModel by viewModels {
        ReviewRosterViewModel.Factory(appContainer.classRepository, intent.getStringExtra(EXTRA_CLASS_ID))
    }
    private val classId: String? get() = intent.getStringExtra(EXTRA_CLASS_ID)

    private var saving = false
    private var retryInSeconds: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

        // Siswa yang terlewat dipilih dari daftar kelas yang sebenarnya (id dari server), bukan dari nama yang diketik
        binding.btnTambahSiswaManual.setOnClickListener { addMissedStudent() }
        binding.ivAddStudent.setOnClickListener { addMissedStudent() }

        binding.btnConfirmAll.setOnClickListener {
            confirmAndSaveAll()
        }
    }

    private fun addMissedStudent() {
        val state = roster.state.value
        when {
            state.error != null -> {
                Snackbar.make(binding.root, state.error, Snackbar.LENGTH_LONG)
                    .setAnchorView(binding.clBottomBar)
                    .setAction(R.string.review_coba_lagi) { roster.load() }
                    .show()
                roster.errorShown()
            }
            !state.loaded -> Toast.makeText(this, R.string.review_tambah_memuat, Toast.LENGTH_SHORT).show()
            else -> {
                val listed = studentList.map { it.id }.toSet()
                if (roster.addable(listed).isEmpty()) {
                    Toast.makeText(this, R.string.review_tambah_semua_ada, Toast.LENGTH_SHORT).show()
                    return
                }
                StudentPickerDialog.show(this, state.students, isTaken = { it.id in listed }) { student -> askMood(student) }
            }
        }
    }

    /** Guru memilih mood siswa yang ditambahkan. Tidak ada nilai bawaan supaya tidak ada mood yang tersimpan tanpa dipilih. */
    private fun askMood(student: Student) {
        val labels = REVIEW_MOOD_LABELS
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.review_tambah_pilih_mood, student.name))
            .setItems(labels.map(::reviewMoodText).toTypedArray()) { _, which ->
                studentList += manualRow(student, labels[which], R.drawable.img_avatar_siswa)
                adapter.notifyItemInserted(studentList.lastIndex)
                binding.rvScannedStudents.scrollToPosition(studentList.lastIndex)
                updateSummaryStats()
                Toast.makeText(this, getString(R.string.review_tambah_berhasil, student.name), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.konsen_batal, null)
            .show()
    }

    private fun updateSummaryStats() {
        val total = studentList.size
        val unconfirmed = studentList.count { it.needsConfirmation() }
        binding.tvTotalDetected.text = "$total Siswa Siap Disimpan"
        binding.btnConfirmAll.text = when {
            retryInSeconds != null -> getString(R.string.batas_laju_menunggu, retryInSeconds)
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
            binding.tvCountSedih.text = "😢 0 Sedih"
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
        val sedihCount = studentList.count { it.currentMood == "SEDIH" }
        val bingungCount = studentList.count { it.currentMood == "BINGUNG" }
        val marahCount = studentList.count { it.currentMood == "MARAH" }

        binding.tvCountHappy.text = "😊 $happyCount Senang"
        binding.tvCountKalem.text = "😌 $kalemCount Kalem"
        binding.tvCountSedih.text = "😢 $sedihCount Sedih"
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
                    retryInSeconds = state.retryInSeconds
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
