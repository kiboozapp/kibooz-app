package com.nthb.kibooz.ui.guru

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.nthb.kibooz.R
import com.nthb.kibooz.appContainer
import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.session.Role
import com.nthb.kibooz.databinding.ActivityDetailKelasBinding
import com.nthb.kibooz.util.ClassCode
import com.nthb.kibooz.util.InsetsUtil
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class DetailKelasActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailKelasBinding
    private var activeClassId: String? = null
    private var isTeacherRole: Boolean = true
    private var selectedMeetingIndex: Int = 3
    private var currentKodeKelas: String = ""
    private var currentNamaKelas: String = ""
    private var hasInitialized: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailKelasBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetsUtil.applySystemBarInsets(
            root = binding.root,
            headerView = binding.clTopBar,
            fab = binding.fabTambahModul
        )

        val rawKodeKelas = intent.getStringExtra("KODE_KELAS").orEmpty()
        val namaKelas = intent.getStringExtra("NAMA_KELAS") ?: "Seni & Kreativitas"
        activeClassId = intent.getStringExtra("CLASS_ID")

        // Role-Gating: Wali murid hanya memantau modul dan aktivitas, Guru dapat mengelola modul dan menyalin kode
        val sessionUser = appContainer.authRepository.session.value?.user
        isTeacherRole = sessionUser?.role == Role.GURU
        binding.fabTambahModul.visibility = if (isTeacherRole) View.VISIBLE else View.GONE
        binding.btnSalinKode.visibility = if (isTeacherRole) View.VISIBLE else View.GONE
        binding.ivShareKelas.visibility = if (isTeacherRole) View.VISIBLE else View.GONE

        val kodeKelas = if (rawKodeKelas.isNotBlank() && !rawKodeKelas.contains("-") && rawKodeKelas.length == 8) {
            rawKodeKelas
        } else {
            ClassCode.resolveJoinCode(activeClassId)
        }

        val initialCount = if (intent.hasExtra("TOTAL_STUDENTS")) {
            intent.getIntExtra("TOTAL_STUDENTS", 0)
        } else {
            if (isTeacherRole) 0 else 1
        }
        currentKodeKelas = kodeKelas
        currentNamaKelas = namaKelas
        setupViews(kodeKelas, namaKelas, initialCount)
        setupDynamicMeetingSchedule(initialCount)
        setupListeners(kodeKelas, namaKelas)
        loadClassData(kodeKelas, namaKelas)
        hasInitialized = true
    }

    override fun onResume() {
        super.onResume()
        if (hasInitialized && activeClassId != null) {
            loadClassData(currentKodeKelas, currentNamaKelas)
        }
    }

    private fun loadClassData(kodeKelas: String, namaKelas: String) {
        lifecycleScope.launch {
            var count = if (intent.hasExtra("TOTAL_STUDENTS")) {
                intent.getIntExtra("TOTAL_STUDENTS", 0)
            } else {
                if (isTeacherRole) 0 else 1
            }
            if (activeClassId.isNullOrBlank()) {
                if (isTeacherRole) {
                    val teacherClasses = appContainer.classRepository.teacherClasses()
                    if (teacherClasses is ApiResult.Success) {
                        val match = teacherClasses.data.firstOrNull {
                            it.joinCode.equals(kodeKelas, ignoreCase = true) || it.name.equals(namaKelas, ignoreCase = true)
                        } ?: teacherClasses.data.firstOrNull()
                        activeClassId = match?.id
                        if (match != null) {
                            count = match.totalStudents
                        }
                    }
                } else {
                    when (val dash = appContainer.waliRepository.dashboard()) {
                        is ApiResult.Success -> {
                            activeClassId = dash.data.student.classId
                        }
                        else -> {}
                    }
                }
            }

            val classId = activeClassId
            if (!classId.isNullOrBlank()) {
                if (isTeacherRole) {
                    val roster = appContainer.classRepository.students(classId)
                    if (roster is ApiResult.Success) {
                        count = roster.data.total
                    }
                } else {
                    when (val lb = appContainer.leaderboardRepository.leaderboard(classId)) {
                        is ApiResult.Success -> {
                            count = lb.data.podium.size + lb.data.rankings.size
                        }
                        else -> {}
                    }
                }
                when (val forumRes = appContainer.forumRepository.threads(classId)) {
                    is ApiResult.Success -> {
                        val realThreads = maxOf(forumRes.data.total, forumRes.data.threads.size)
                        updateForumBanner(realThreads)
                    }
                    else -> updateForumBanner(0)
                }
            } else {
                updateForumBanner(0)
            }
            setupDynamicMeetingSchedule(count)
            updatePesertaBanner(count)
        }
    }

    private fun updateForumBanner(threadCount: Int) {
        binding.tvSubForumDiskusi.text = if (threadCount <= 0) {
            "Belum ada pesan di forum kelas"
        } else {
            "$threadCount pesan diskusi wali murid & guru"
        }
    }

    private fun updatePesertaBanner(studentCount: Int) {
        binding.tvTitleDaftarPeserta.text = "Daftar Peserta Kelas"
        val countText = "$studentCount Siswa Terdaftar"
        binding.tvSubDaftarPeserta.text = "1 Guru Pengajar & $countText"
    }

    private fun setupDynamicMeetingSchedule(studentCount: Int) {
        val calendar = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("d MMM yyyy", Locale("id", "ID"))
        val dateToday = dateFormat.format(calendar.time)

        // Pertemuan 3: Terjadwal sebagai pertemuan aktif hari ini
        binding.tvStatusModul3.text = "Sedang Berlangsung • $dateToday • Sesi Aktif Hari Ini"

        // Pertemuan 2: Selesai 7 hari lalu
        calendar.add(Calendar.DAY_OF_YEAR, -7)
        val dateP2 = dateFormat.format(calendar.time)
        binding.tvStatusModul2.text = "Selesai • $dateP2 • Materi Diselesaikan"

        // Pertemuan 1: Selesai 14 hari lalu
        calendar.add(Calendar.DAY_OF_YEAR, -7)
        val dateP1 = dateFormat.format(calendar.time)
        binding.tvStatusModul1.text = "Selesai • $dateP1 • Materi Diselesaikan"

        // Pertemuan 4: Mendatang 7 hari ke depan
        calendar.time = Date()
        calendar.add(Calendar.DAY_OF_YEAR, 7)
        val dateP4 = dateFormat.format(calendar.time)
        binding.tvStatusModul4.text = "Mendatang • $dateP4 • Terjadwal"

        highlightSelectedMeeting(selectedMeetingIndex)
    }

    private fun highlightSelectedMeeting(index: Int) {
        selectedMeetingIndex = index
        val purpleColor = getColor(R.color.kibooz_primary_purple)
        val defaultColor = getColor(R.color.kibooz_border)

        val cards = listOf(
            binding.cardModul1,
            binding.cardModul2,
            binding.cardModul3,
            binding.cardModul4
        )

        cards.forEachIndexed { i, card ->
            if (i + 1 == index) {
                card.strokeColor = purpleColor
                card.strokeWidth = (2 * resources.displayMetrics.density).toInt()
                card.cardElevation = 6 * resources.displayMetrics.density
            } else {
                card.strokeColor = defaultColor
                card.strokeWidth = (1 * resources.displayMetrics.density).toInt()
                card.cardElevation = 2 * resources.displayMetrics.density
            }
        }
    }

    private fun setupViews(kodeKelas: String, namaKelas: String, initialCount: Int) {
        val rombelName = intent.getStringExtra("ROMBEL_NAME")?.takeIf { it.isNotBlank() }
            ?: intent.getStringExtra("CHILD_CLASS_NAME")?.takeIf { it.isNotBlank() }
        binding.tvTitleBar.text = rombelName ?: "Detail $namaKelas"
        binding.tvNamaKelas.text = namaKelas
        binding.tvKodeKelas.text = kodeKelas
        binding.tabModul.text = "Modul (4)"
        val sessionUser = appContainer.authRepository.session.value?.user
        if (isTeacherRole && !sessionUser?.fullName.isNullOrBlank()) {
            binding.tvPengajar.text = "Pengajar: ${sessionUser!!.fullName}"
        }
        updatePesertaBanner(initialCount)

        when {
            namaKelas.contains("Bahasa", ignoreCase = true) || kodeKelas.startsWith("BHS") -> {
                binding.ivMapelIcon.setImageResource(R.drawable.img_mapel_bahasa)
                binding.tvDeskripsiKelas.text = "Mengenalkan kosakata baru, bercerita bergambar, dan melatih kepercayaan diri anak dalam berkomunikasi."
                binding.tvTitleModul1.text = "Pertemuan 1: Mengenal Huruf Vokal & Konsonan"
                binding.tvTitleModul2.text = "Pertemuan 2: Bercerita Bergambar Bersama Teman"
                binding.tvTitleModul3.text = "Pertemuan 3: Mengeja Kata Sederhana"
                binding.tvTitleModul4.text = "Pertemuan 4: Menyanyi Lagu Kata Ajaib"
            }
            namaKelas.contains("Logika", ignoreCase = true) || namaKelas.contains("Berpikir", ignoreCase = true) || kodeKelas.startsWith("LOG") -> {
                binding.ivMapelIcon.setImageResource(R.drawable.img_mapel_logika)
                binding.tvDeskripsiKelas.text = "Mengasah kemampuan berpikir kritis melalui pengenalan pola, berhitung benda, dan perbandingan ukuran."
                binding.tvTitleModul1.text = "Pertemuan 1: Mengenal Pola Bentuk Geometri"
                binding.tvTitleModul2.text = "Pertemuan 2: Menghitung Benda 1 sampai 10"
                binding.tvTitleModul3.text = "Pertemuan 3: Membandingkan Ukuran Besar & Kecil"
                binding.tvTitleModul4.text = "Pertemuan 4: Teka-Teki Warna & Urutan"
            }
            namaKelas.contains("Motorik", ignoreCase = true) || namaKelas.contains("Fisik", ignoreCase = true) || kodeKelas.startsWith("MOT") -> {
                binding.ivMapelIcon.setImageResource(R.drawable.img_mapel_motorik)
                binding.tvDeskripsiKelas.text = "Melatih koordinasi gerak kasar dan halus anak lewat senam ceria, permainan ketangkasan, dan origami."
                binding.tvTitleModul1.text = "Pertemuan 1: Gerak Jalan Seimbang & Senam Ceria"
                binding.tvTitleModul2.text = "Pertemuan 2: Melipat Kertas Origami Hewan"
                binding.tvTitleModul3.text = "Pertemuan 3: Menangkap & Melempar Bola Karet"
                binding.tvTitleModul4.text = "Pertemuan 4: Meronce Manik-Manik & Tali Sepatu"
            }
            namaKelas.contains("Agama", ignoreCase = true) || namaKelas.contains("Moral", ignoreCase = true) || kodeKelas.startsWith("AGM") -> {
                binding.ivMapelIcon.setImageResource(R.drawable.img_mapel_agama)
                binding.tvDeskripsiKelas.text = "Menanamkan akhlak mulia, kebiasaan berdoa harian, berbagi bersama sahabat, dan rasa syukur setiap hari."
                binding.tvTitleModul1.text = "Pertemuan 1: Mengenal Doa Harian & Bersyukur"
                binding.tvTitleModul2.text = "Pertemuan 2: Berbagi Bekal dengan Sahabat"
                binding.tvTitleModul3.text = "Pertemuan 3: Menghormati Orang Tua & Guru"
                binding.tvTitleModul4.text = "Pertemuan 4: Menjaga Kebersihan Tempat Ibadah"
            }
            namaKelas.contains("Sosial", ignoreCase = true) || namaKelas.contains("Emotional", ignoreCase = true) || namaKelas.contains("Emosional", ignoreCase = true) || kodeKelas.startsWith("SOS") -> {
                binding.ivMapelIcon.setImageResource(R.drawable.img_mapel_sosial)
                binding.tvDeskripsiKelas.text = "Membantu anak mengenali dan mengelola emosi, bekerja sama dalam kelompok, serta bersikap sopan santun."
                binding.tvTitleModul1.text = "Pertemuan 1: Mengenali Perasaan Bahagia & Sedih"
                binding.tvTitleModul2.text = "Pertemuan 2: Mengungkapkan Keinginan dengan Sopan"
                binding.tvTitleModul3.text = "Pertemuan 3: Bekerja Sama dalam Permainan Kelompok"
                binding.tvTitleModul4.text = "Pertemuan 4: Mengucapkan Maaf dan Terima Kasih"
            }
            else -> {
                binding.ivMapelIcon.setImageResource(R.drawable.img_mapel_seni)
                binding.tvDeskripsiKelas.text = "Aktivitas menggambar emosi, mewarnai suasana hati, dan kerajinan daur ulang kreatif untuk mengenalkan emosi si kecil."
                binding.tvTitleModul1.text = "Pertemuan 1: Mengenal Warna Emosiku"
                binding.tvTitleModul2.text = "Pertemuan 2: Menggambar Ekspresi Senang & Sedih"
                binding.tvTitleModul3.text = "Pertemuan 3: Kolase Kertas Daur Ulang"
                binding.tvTitleModul4.text = "Pertemuan 4: Pameran Karya Seni Bersama"
            }
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

        // Interaktivitas kartu pertemuan modul
        binding.cardModul1.setOnClickListener {
            selectModule(1, binding.tvTitleModul1.text.toString(), binding.tvStatusModul1.text.toString())
        }
        binding.cardModul2.setOnClickListener {
            selectModule(2, binding.tvTitleModul2.text.toString(), binding.tvStatusModul2.text.toString())
        }
        binding.cardModul3.setOnClickListener {
            selectModule(3, binding.tvTitleModul3.text.toString(), binding.tvStatusModul3.text.toString())
        }
        binding.cardModul4.setOnClickListener {
            selectModule(4, binding.tvTitleModul4.text.toString(), binding.tvStatusModul4.text.toString())
        }
    }

    private fun selectModule(index: Int, title: String, status: String) {
        highlightSelectedMeeting(index)
        Toast.makeText(this, "Pertemuan $index Aktif: $title ($status)", Toast.LENGTH_SHORT).show()
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
