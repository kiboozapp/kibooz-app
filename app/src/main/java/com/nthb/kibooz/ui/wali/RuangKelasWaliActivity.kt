package com.nthb.kibooz.ui.wali

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.nthb.kibooz.R
import com.nthb.kibooz.appContainer
import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.databinding.ActivityRuangKelasWaliBinding
import com.nthb.kibooz.ui.camera.CameraScanSampahActivity
import com.nthb.kibooz.ui.guru.DetailKelasActivity
import com.nthb.kibooz.ui.profile.ProfilWaliActivity
import com.nthb.kibooz.util.ClassCode
import com.nthb.kibooz.util.ClassNames
import com.nthb.kibooz.util.InsetsUtil
import kotlinx.coroutines.launch

class RuangKelasWaliActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRuangKelasWaliBinding
    private var studentId: String? = null
    private var childClassName: String? = null
    private var childClassId: String? = null
    private var childJoinCode: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRuangKelasWaliBinding.inflate(layoutInflater)
        setContentView(binding.root)

        studentId = intent.getStringExtra(EXTRA_STUDENT_ID)

        InsetsUtil.applySystemBarInsets(
            root = binding.root,
            headerView = binding.clTopBar,
            bottomBar = binding.clBottomNavigation,
            fab = binding.fabScannerSampah
        )

        loadChildClassInfo()
        setupListeners()
    }

    private fun loadChildClassInfo() {
        lifecycleScope.launch {
            when (val dash = appContainer.waliRepository.dashboard()) {
                is ApiResult.Success -> {
                    val st = dash.data.student
                    studentId = st.id
                    childClassName = st.className
                    childClassId = st.classId
                    childJoinCode = st.joinCode?.takeIf { it.isNotBlank() }
                    val shortClass = ClassNames.stemOrFallback(st.className, st.className)
                    binding.tvSubtitleRuangKelas.text = "Kelas Aktif: $shortClass • 6 Tema Pembelajaran"
                    loadPendingMoveRequest(st.id)
                }
                else -> {}
            }
        }
    }

    private fun loadPendingMoveRequest(targetStudentId: String) {
        lifecycleScope.launch {
            when (val res = appContainer.moveRequestRepository.childMoveRequests(targetStudentId)) {
                is ApiResult.Success -> {
                    val pending = res.data.firstOrNull { it.status == "pending" }
                    if (pending != null) {
                        binding.cardStatusPindah.visibility = android.view.View.VISIBLE
                        binding.tvStatusPindah.text = getString(R.string.pindah_kelas_pending_info, pending.className)
                        binding.btnBatalPindah.isEnabled = true
                        binding.btnBatalPindah.setOnClickListener { confirmCancelMove(targetStudentId, pending) }
                    } else {
                        binding.cardStatusPindah.visibility = android.view.View.GONE
                    }
                }
                else -> Unit
            }
        }
    }

    private fun confirmCancelMove(targetStudentId: String, req: com.nthb.kibooz.data.remote.MoveRequest) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Batalkan Pengajuan Pindah?")
            .setMessage("Pengajuan pindah ke ${req.className} akan dibatalkan.")
            .setPositiveButton(R.string.pindah_kelas_batalkan) { _, _ ->
                cancelPendingMove(targetStudentId, req.id)
            }
            .setNegativeButton(R.string.batal, null)
            .show()
    }

    private fun cancelPendingMove(targetStudentId: String, requestId: String) {
        binding.btnBatalPindah.isEnabled = false
        lifecycleScope.launch {
            when (val res = appContainer.moveRequestRepository.cancelChildMoveRequest(targetStudentId, requestId)) {
                is ApiResult.Success -> {
                    binding.cardStatusPindah.visibility = android.view.View.GONE
                    Toast.makeText(this@RuangKelasWaliActivity, R.string.pindah_kelas_dibatalkan, Toast.LENGTH_SHORT).show()
                }
                is ApiResult.Failure -> {
                    binding.btnBatalPindah.isEnabled = true
                    if (res.errorCode == "MOVE_REQUEST_NOT_FOUND") {
                        binding.cardStatusPindah.visibility = android.view.View.GONE
                        loadChildClassInfo()
                    }
                    val msg = if (res.errorCode == "MOVE_REQUEST_NOT_FOUND") {
                        getString(R.string.error_move_request_not_found)
                    } else {
                        res.message ?: getString(R.string.error_unknown)
                    }
                    Toast.makeText(this@RuangKelasWaliActivity, msg, Toast.LENGTH_LONG).show()
                }
                is ApiResult.NetworkError -> {
                    binding.btnBatalPindah.isEnabled = true
                    Toast.makeText(this@RuangKelasWaliActivity, R.string.error_network, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupListeners() {
        binding.ivBack.setOnClickListener {
            finish()
        }

        // Tombol Gabung Kelas (Screenshot 154028)
        binding.btnGabungKelas.setOnClickListener {
            showGabungKelasDialog()
        }

        // Fitur Pencarian Nama Kelas & Modul Pembelajaran
        binding.etSearchKelas.doAfterTextChanged { text ->
            filterSubjectCards(text?.toString().orEmpty().trim().lowercase())
        }

        // 6 Subject Cards (Screenshot 154028)
        binding.cardMapelSeni.setOnClickListener {
            openDetailKelas("SENI-A1", "Seni & Kreativitas")
        }
        binding.cardMapelBahasa.setOnClickListener {
            openDetailKelas("BHS-A1", "Bahasa & Komunikasi")
        }
        binding.cardMapelLogika.setOnClickListener {
            openDetailKelas("LOG-A1", "Berpikir & Logika")
        }
        binding.cardMapelMotorik.setOnClickListener {
            openDetailKelas("MOT-A1", "Belajar Fisik Motorik")
        }
        binding.cardMapelAgama.setOnClickListener {
            openDetailKelas("AGM-A1", "Nilai Agama & Moral")
        }
        binding.cardMapelSosial.setOnClickListener {
            openDetailKelas("SOS-A1", "Sosial & Emotional")
        }

        // Bottom Navigation Wali
        binding.tabHome.setOnClickListener {
            finish()
        }
        binding.tabSampah.setOnClickListener {
            startActivity(Intent(this, StatistikSampahActivity::class.java))
            finish()
        }
        binding.tabLeaderboard.setOnClickListener {
            startActivity(Intent(this, LeaderboardActivity::class.java))
            finish()
        }
        binding.tabProfil.setOnClickListener {
            startActivity(Intent(this, ProfilWaliActivity::class.java))
            finish()
        }
        binding.fabScannerSampah.setOnClickListener {
            startActivity(Intent(this, CameraScanSampahActivity::class.java))
        }
    }

    private fun showGabungKelasDialog() {
        val input = EditText(this).apply {
            hint = "Contoh: K7M2QX4R"
            setSingleLine()
            setPadding(48, 24, 48, 24)
        }
        val container = FrameLayout(this).apply {
            setPadding(32, 16, 32, 0)
            addView(input)
        }

        AlertDialog.Builder(this)
            .setTitle("Ajukan Pindah Kelas Si Kecil")
            .setMessage("Anak hanya dapat terdaftar di 1 kelas aktif. Masukkan kode kelas tujuan dari wali kelas baru:")
            .setView(container)
            .setPositiveButton("Ajukan Pindah") { _, _ ->
                val raw = input.text.toString()
                val normalized = ClassCode.normalize(raw)
                when {
                    raw.isBlank() -> {
                        Toast.makeText(this, R.string.kelas_kode_kosong, Toast.LENGTH_SHORT).show()
                    }
                    normalized == null -> {
                        Toast.makeText(this, R.string.kelas_kode_tidak_valid, Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        submitMoveRequest(normalized)
                    }
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun submitMoveRequest(classCode: String) {
        Toast.makeText(this, R.string.kelas_gabung_proses, Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val targetStudentId = studentId ?: run {
                when (val dash = appContainer.waliRepository.dashboard()) {
                    is ApiResult.Success -> {
                        val sid = dash.data.student.id
                        studentId = sid
                        sid
                    }
                    else -> null
                }
            }

            if (targetStudentId == null) {
                Toast.makeText(this@RuangKelasWaliActivity, R.string.kelas_gabung_siswa_tidak_ditemukan, Toast.LENGTH_SHORT).show()
                return@launch
            }

            when (val result = appContainer.waliRepository.requestMove(targetStudentId, classCode)) {
                is ApiResult.Success -> {
                    loadPendingMoveRequest(targetStudentId)
                    Toast.makeText(
                        this@RuangKelasWaliActivity,
                        getString(R.string.kelas_gabung_sukses, result.data.className),
                        Toast.LENGTH_LONG
                    ).show()
                }
                is ApiResult.Failure -> {
                    if (result.errorCode == "MOVE_REQUEST_PENDING") {
                        loadPendingMoveRequest(targetStudentId)
                    }
                    val (title, message) = when (result.errorCode) {
                        "MOVE_REQUEST_PENDING" -> "Pengajuan Masih Menunggu" to getString(R.string.kelas_gabung_menunggu)
                        "CLASS_CODE_NOT_FOUND" -> "Kode Kelas Tidak Ditemukan" to getString(R.string.kelas_gabung_kode_tidak_ditemukan)
                        "STUDENT_NOT_FOUND" -> "Siswa Tidak Ditemukan" to getString(R.string.kelas_gabung_siswa_tidak_ditemukan)
                        "ALREADY_IN_CLASS" -> "Sudah Terdaftar di Kelas Ini" to getString(R.string.error_already_in_class)
                        "CLASS_JOINING_CLOSED" -> "Pendaftaran Kelas Ditutup" to getString(R.string.anak_error_kelas_tutup)
                        "CLASS_FULL" -> "Kelas Sudah Penuh" to getString(R.string.error_class_full)
                        else -> "Gagal Mengajukan Pindah" to getString(R.string.kelas_gabung_gagal, result.message ?: result.errorCode ?: "Error")
                    }
                    com.google.android.material.dialog.MaterialAlertDialogBuilder(this@RuangKelasWaliActivity)
                        .setTitle(title)
                        .setMessage(message)
                        .setPositiveButton("Mengerti", null)
                        .show()
                }
                is ApiResult.NetworkError -> {
                    Toast.makeText(this@RuangKelasWaliActivity, R.string.error_network, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun openDetailKelas(kode: String, nama: String) {
        val intent = Intent(this, DetailKelasActivity::class.java).apply {
            putExtra("KODE_KELAS", ClassCode.fromServer(childJoinCode))
            putExtra("MAPEL_CODE", kode)
            putExtra("NAMA_KELAS", nama)
            putExtra("CLASS_ID", childClassId)
            putExtra("CHILD_CLASS_NAME", childClassName)
            studentId?.let { putExtra(EXTRA_STUDENT_ID, it) }
        }
        startActivity(intent)
    }

    private fun filterSubjectCards(query: String) {
        val cards = listOf(
            binding.cardMapelSeni to "seni kreativitas menggambar warna melipat",
            binding.cardMapelBahasa to "bahasa komunikasi huruf eja membaca kata",
            binding.cardMapelLogika to "berpikir logika hitung angka matematika geometri pola",
            binding.cardMapelMotorik to "belajar fisik motorik senam lompat origami jalan",
            binding.cardMapelAgama to "nilai agama moral doa bersyukur ibadah adab",
            binding.cardMapelSosial to "sosial emotional emosi senang sedih maaf terima kasih"
        )

        cards.forEach { (card, keywords) ->
            val matches = query.isEmpty() || keywords.contains(query) || query.split(" ").all { keywords.contains(it) }
            card.visibility = if (matches) View.VISIBLE else View.GONE
        }
    }

    companion object {
        const val EXTRA_STUDENT_ID = "EXTRA_STUDENT_ID"
    }
}
