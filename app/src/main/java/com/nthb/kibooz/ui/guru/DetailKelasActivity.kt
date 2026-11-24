package com.nthb.kibooz.ui.guru

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.nthb.kibooz.R
import com.nthb.kibooz.appContainer
import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.databinding.ActivityDetailKelasBinding
import com.nthb.kibooz.util.InsetsUtil
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class DetailKelasActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailKelasBinding
    private var activeClassId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailKelasBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetsUtil.applySystemBarInsets(
            root = binding.root,
            headerView = binding.clTopBar,
            fab = binding.fabTambahModul
        )

        val kodeKelas = intent.getStringExtra("KODE_KELAS") ?: "SENI-A1"
        val namaKelas = intent.getStringExtra("NAMA_KELAS") ?: "Seni & Kreativitas"
        activeClassId = intent.getStringExtra("CLASS_ID")

        setupViews(kodeKelas, namaKelas)
        setupDynamicMeetingSchedule(24)
        setupListeners(kodeKelas, namaKelas)
        loadClassData(kodeKelas, namaKelas)
    }

    private fun loadClassData(kodeKelas: String, namaKelas: String) {
        lifecycleScope.launch {
            var count = 24
            if (activeClassId.isNullOrBlank()) {
                val teacherClasses = appContainer.classRepository.teacherClasses()
                if (teacherClasses is ApiResult.Success) {
                    val match = teacherClasses.data.firstOrNull {
                        it.joinCode.equals(kodeKelas, ignoreCase = true) || it.name.equals(namaKelas, ignoreCase = true)
                    } ?: teacherClasses.data.firstOrNull()
                    activeClassId = match?.id
                    if (match != null && match.totalStudents > 0) {
                        count = match.totalStudents
                    }
                }
            }

            val classId = activeClassId
            if (!classId.isNullOrBlank()) {
                val roster = appContainer.classRepository.students(classId)
                if (roster is ApiResult.Success && roster.data.students.isNotEmpty()) {
                    count = roster.data.total
                }
            }
            setupDynamicMeetingSchedule(count)
        }
    }

    private fun setupDynamicMeetingSchedule(studentCount: Int) {
        val calendar = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("d MMM yyyy", Locale("id", "ID"))

        // Pertemuan 3: Sedang Berlangsung hari ini
        val dateP3 = dateFormat.format(calendar.time)
        val countText = if (studentCount > 0) "$studentCount Siswa Terdaftar" else "24 Siswa Terdaftar"
        binding.tvStatusModul3.text = "Sedang Berlangsung • $dateP3 • $countText"

        // Pertemuan 2: Selesai 7 hari lalu
        calendar.add(Calendar.DAY_OF_YEAR, -7)
        val dateP2 = dateFormat.format(calendar.time)
        val hadirP2 = if (studentCount > 0) "${(studentCount * 0.95).toInt().coerceAtLeast(1)} Siswa Hadir" else "24 Siswa Hadir"
        binding.tvStatusModul2.text = "Selesai • $dateP2 • $hadirP2"

        // Pertemuan 1: Selesai 14 hari lalu
        calendar.add(Calendar.DAY_OF_YEAR, -7)
        val dateP1 = dateFormat.format(calendar.time)
        val hadirP1 = if (studentCount > 0) "${(studentCount * 0.92).toInt().coerceAtLeast(1)} Siswa Hadir" else "22 Siswa Hadir"
        binding.tvStatusModul1.text = "Selesai • $dateP1 • $hadirP1"

        // Pertemuan 4: Mendatang 7 hari ke depan
        calendar.time = Date()
        calendar.add(Calendar.DAY_OF_YEAR, 7)
        val dateP4 = dateFormat.format(calendar.time)
        binding.tvStatusModul4.text = "Mendatang • $dateP4"
    }

    private fun setupViews(kodeKelas: String, namaKelas: String) {
        binding.tvTitleBar.text = "Detail $namaKelas"
        binding.tvNamaKelas.text = namaKelas
        binding.tvKodeKelas.text = kodeKelas

        when {
            kodeKelas.startsWith("BHS") -> binding.ivMapelIcon.setImageResource(R.drawable.img_mapel_bahasa)
            kodeKelas.startsWith("LOG") -> binding.ivMapelIcon.setImageResource(R.drawable.img_mapel_logika)
            kodeKelas.startsWith("MOT") -> binding.ivMapelIcon.setImageResource(R.drawable.img_mapel_motorik)
            kodeKelas.startsWith("AGM") -> binding.ivMapelIcon.setImageResource(R.drawable.img_mapel_agama)
            kodeKelas.startsWith("SOS") -> binding.ivMapelIcon.setImageResource(R.drawable.img_mapel_sosial)
            else -> binding.ivMapelIcon.setImageResource(R.drawable.img_mapel_seni)
        }
    }

    private fun setupListeners(kodeKelas: String, namaKelas: String) {
        binding.ivBack.setOnClickListener {
            finish()
        }

        binding.btnSalinKode.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Kode Kelas", kodeKelas)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Kode Kelas $kodeKelas berhasil disalin!", Toast.LENGTH_SHORT).show()
        }

        binding.ivShareKelas.setOnClickListener {
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, "Ayo bergabung ke kelas Kibooz $namaKelas dengan kode: $kodeKelas")
                type = "text/plain"
            }
            startActivity(Intent.createChooser(shareIntent, "Bagikan Kode Kelas"))
        }

        // Navigasi ke Peserta Kelas (Hamburger Menu, Quick Card, & Tab)
        binding.ivMenuKelas.setOnClickListener {
            openPesertaKelas(namaKelas)
        }
        binding.cardQuickPeserta.setOnClickListener {
            openPesertaKelas(namaKelas)
        }
        binding.tvPengajar.setOnClickListener {
            openPesertaKelas(namaKelas)
        }

        // Navigasi ke Forum Diskusi
        binding.cardQuickForum.setOnClickListener {
            openForumDiskusi(namaKelas)
        }
        binding.tabForum.setOnClickListener {
            openForumDiskusi(namaKelas)
        }

        // Tab switches
        binding.tabModul.setOnClickListener {
            Toast.makeText(this, "Menampilkan daftar modul", Toast.LENGTH_SHORT).show()
        }
        binding.tabVideo.setOnClickListener {
            val intent = Intent(this, VideoPembelajaranActivity::class.java).apply {
                putExtra("NAMA_KELAS", namaKelas)
                putExtra("CLASS_ID", activeClassId)
                putExtra("KODE_KELAS", kodeKelas)
            }
            startActivity(intent)
        }
        binding.tabAnggota.setOnClickListener {
            openPesertaKelas(namaKelas, kodeKelas)
        }

        binding.fabTambahModul.setOnClickListener {
            Toast.makeText(this, "Buka form tambah modul kelas baru", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openPesertaKelas(namaKelas: String, kodeKelas: String? = null) {
        val intent = Intent(this, PesertaKelasActivity::class.java).apply {
            putExtra("NAMA_KELAS", namaKelas)
            putExtra("CLASS_ID", activeClassId)
            if (kodeKelas != null) putExtra("KODE_KELAS", kodeKelas)
        }
        startActivity(intent)
    }

    private fun openForumDiskusi(namaKelas: String, kodeKelas: String? = null) {
        val intent = Intent(this, ForumDiskusiActivity::class.java).apply {
            putExtra("NAMA_KELAS", namaKelas)
            putExtra("CLASS_ID", activeClassId)
            if (kodeKelas != null) putExtra("KODE_KELAS", kodeKelas)
        }
        startActivity(intent)
    }
}
