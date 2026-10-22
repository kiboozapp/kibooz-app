package com.nthb.kibooz.ui.camera

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nthb.kibooz.R
import com.nthb.kibooz.databinding.ActivityReviewScanMoodBinding
import com.nthb.kibooz.model.ScannedStudentMood
import com.nthb.kibooz.ui.guru.DashboardGuruActivity
import com.nthb.kibooz.util.InsetsUtil
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class ReviewScanMoodActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReviewScanMoodBinding
    private val studentList = mutableListOf<ScannedStudentMood>()
    private lateinit var adapter: ReviewScanMoodAdapter

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
    }

    private fun setupDateHeader() {
        val dateFormat = SimpleDateFormat("EEEE, d MMMM yyyy", Locale("id", "ID"))
        val formattedDate = dateFormat.format(Date())
        binding.tvSubHeaderInfo.text = "Kelas A • $formattedDate"
    }

    private fun loadScannedData() {
        // Ambil data dari Intent jika tersedia, atau gunakan data deteksi kamera multi-wajah
        @Suppress("DEPRECATION")
        val passedList = intent.getSerializableExtra(EXTRA_STUDENTS) as? ArrayList<ScannedStudentMood>

        if (!passedList.isNullOrEmpty()) {
            studentList.addAll(passedList)
        } else {
            // Simulasi hasil pemindaian multi-wajah CameraX (Amelia, Kenzo, Farhan)
            studentList.add(
                ScannedStudentMood(
                    id = UUID.randomUUID().toString(),
                    studentName = "Amelia Siti Zahra",
                    avatarRes = R.drawable.img_avatar_siswa,
                    currentMood = "SENANG",
                    confidenceScore = 0.94f,
                    teacherNotes = "Datang tepat waktu dan ceria"
                )
            )
            studentList.add(
                ScannedStudentMood(
                    id = UUID.randomUUID().toString(),
                    studentName = "Kenzo Putra",
                    avatarRes = R.drawable.img_avatar_siswa,
                    currentMood = "KALEM",
                    confidenceScore = 0.89f,
                    teacherNotes = ""
                )
            )
            studentList.add(
                ScannedStudentMood(
                    id = UUID.randomUUID().toString(),
                    studentName = "Farhan Al-Fatih",
                    avatarRes = R.drawable.img_avatar_siswa,
                    currentMood = "BINGUNG",
                    confidenceScore = 0.71f, // Ambang batas <75% memicu badge 'Perlu Dicek' (BR-04)
                    teacherNotes = "Tampak ragu saat masuk gerbang sekolah"
                )
            )
        }
    }

    private fun setupRecyclerView() {
        adapter = ReviewScanMoodAdapter(
            students = studentList,
            onMoodChanged = { _, _ ->
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

        binding.btnTambahSiswaManual.setOnClickListener {
            showAddStudentDialog()
        }

        binding.ivAddStudent.setOnClickListener {
            showAddStudentDialog()
        }

        binding.btnConfirmAll.setOnClickListener {
            confirmAndSaveAll()
        }
    }

    private fun updateSummaryStats() {
        val total = studentList.size
        binding.tvTotalDetected.text = "$total Siswa Siap Disimpan"
        binding.btnConfirmAll.text = "Simpan Data ($total Siswa)"

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
        binding.btnConfirmAll.isEnabled = true

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

    private fun showAddStudentDialog() {
        val input = EditText(this).apply {
            hint = "Nama siswa (contoh: Zhafira Bilqis)"
            setPadding(40, 30, 40, 30)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Tambah Siswa Manual")
            .setMessage("Masukkan nama siswa yang terlewat dari pemindaian kamera:")
            .setView(input)
            .setPositiveButton("Tambahkan") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val newStudent = ScannedStudentMood(
                        id = UUID.randomUUID().toString(),
                        studentName = name,
                        avatarRes = R.drawable.img_avatar_siswa,
                        currentMood = "SENANG",
                        confidenceScore = 1.0f,
                        teacherNotes = "Ditambahkan manual oleh guru"
                    )
                    studentList.add(newStudent)
                    adapter.notifyItemInserted(studentList.size - 1)
                    updateSummaryStats()
                    binding.rvScannedStudents.smoothScrollToPosition(studentList.size - 1)
                    Toast.makeText(this, "$name berhasil ditambahkan", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Nama siswa tidak boleh kosong", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun confirmAndSaveAll() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Simpan Data Presensi Mood?")
            .setMessage("Seluruh data mood ${studentList.size} siswa akan disimpan ke statistik kelas dan dibagikan ke wali murid.")
            .setPositiveButton("Ya, Simpan") { _, _ ->
                Toast.makeText(
                    this,
                    "Berhasil menyimpan data mood ${studentList.size} siswa ke statistik kelas!",
                    Toast.LENGTH_LONG
                ).show()

                // Navigasi tuntas kembali ke Dashboard Guru
                val intent = Intent(this, DashboardGuruActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(intent)
                finish()
            }
            .setNegativeButton("Periksa Lagi", null)
            .show()
    }

    companion object {
        const val EXTRA_STUDENTS = "EXTRA_STUDENTS"
    }
}
