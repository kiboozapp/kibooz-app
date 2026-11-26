package com.nthb.kibooz.ui.guru

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.nthb.kibooz.R
import com.nthb.kibooz.appContainer
import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.ModuleItem
import com.nthb.kibooz.data.session.Role
import com.nthb.kibooz.databinding.ActivityDetailKelasBinding
import com.nthb.kibooz.util.ClassCode
import com.nthb.kibooz.util.InsetsUtil
import com.nthb.kibooz.util.TimeFormat
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class DetailKelasActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailKelasBinding
    private var activeClassId: String? = null
    private var resolvedRombelName: String? = null
    private var isTeacherRole: Boolean = true
    private var selectedMeetingIndex: Int = 3
    private var currentKodeKelas: String = ""
    private var currentNamaKelas: String = ""
    private var loadedModules: List<ModuleItem> = emptyList()
    private var selectedModuleId: String? = null
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
        resolvedRombelName = intent.getStringExtra("ROMBEL_NAME")?.takeIf { it.isNotBlank() }
            ?: intent.getStringExtra("CHILD_CLASS_NAME")?.takeIf { it.isNotBlank() }

        // Role-Gating: Wali murid hanya memantau modul dan aktivitas, Guru dapat mengelola modul dan menyalin kode
        val sessionUser = appContainer.authRepository.session.value?.user
        isTeacherRole = sessionUser?.role == Role.GURU
        binding.fabTambahModul.visibility = if (isTeacherRole) View.VISIBLE else View.GONE
        binding.btnSalinKode.visibility = if (isTeacherRole) View.VISIBLE else View.GONE

        val kodeKelas = if (rawKodeKelas.isNotBlank() && (ClassCode.normalize(rawKodeKelas) != null || !rawKodeKelas.contains("-"))) {
            rawKodeKelas.trim()
        } else {
            ""
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
            if (isTeacherRole) {
                val teacherClasses = appContainer.classRepository.teacherClasses()
                if (teacherClasses is ApiResult.Success) {
                    val match = teacherClasses.data.firstOrNull {
                        (!activeClassId.isNullOrBlank() && it.id == activeClassId) ||
                            it.joinCode.equals(kodeKelas, ignoreCase = true) ||
                            it.name.equals(namaKelas, ignoreCase = true)
                    } ?: teacherClasses.data.firstOrNull()
                    if (activeClassId.isNullOrBlank()) activeClassId = match?.id
                    if (match != null) {
                        if (resolvedRombelName.isNullOrBlank()) resolvedRombelName = match.name
                        if (match.joinCode.isNotBlank()) {
                            showClassCode(match.joinCode)
                        }
                        count = match.totalStudents
                    }
                }
            } else {
                when (val dash = appContainer.waliRepository.dashboard()) {
                    is ApiResult.Success -> {
                        val st = dash.data.student
                        if (activeClassId.isNullOrBlank()) activeClassId = st.classId
                        if (resolvedRombelName.isNullOrBlank()) resolvedRombelName = st.className
                        st.joinCode?.takeIf { it.isNotBlank() }?.let { liveCode ->
                            showClassCode(liveCode)
                        }
                    }
                    else -> {}
                }
            }

            resolvedRombelName?.let { className ->
                binding.tvTitleBar.text = className
                binding.tvTitleForumDiskusi.text = "Ruang Diskusi $className"
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
        loadClassModules()
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

    /** Baris kode dan tombol bagikan hanya tampil jika server memberi kode. */
    private fun showClassCode(code: String) {
        currentKodeKelas = code
        binding.tvKodeKelas.text = code
        binding.rowKodeKelas.visibility = if (code.isNotBlank()) View.VISIBLE else View.GONE
        binding.ivShareKelas.visibility = if (isTeacherRole && code.isNotBlank()) View.VISIBLE else View.GONE
    }

    private fun setupViews(kodeKelas: String, namaKelas: String, initialCount: Int) {
        val rombelName = resolvedRombelName
        binding.tvTitleBar.text = rombelName ?: "Detail $namaKelas"
        binding.tvTitleForumDiskusi.text = rombelName?.let { "Ruang Diskusi $it" } ?: "Ruang Diskusi Kelas Aktif"
        binding.tvNamaKelas.text = namaKelas
        showClassCode(kodeKelas)
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
            val clip = ClipData.newPlainText("Kode Kelas", currentKodeKelas)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Kode Kelas $currentKodeKelas berhasil disalin!", Toast.LENGTH_SHORT).show()
        }

        binding.ivShareKelas.setOnClickListener {
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, "Ayo bergabung ke kelas Kibooz $namaKelas dengan kode: $currentKodeKelas")
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
            openForumDiskusi(currentKodeKelas)
        }
        binding.tabForum.setOnClickListener {
            openForumDiskusi(currentKodeKelas)
        }

        // Tab switches
        binding.tabModul.setOnClickListener {
            Toast.makeText(this, "Menampilkan daftar modul", Toast.LENGTH_SHORT).show()
        }
        binding.tabVideo.setOnClickListener {
            val intent = Intent(this, VideoPembelajaranActivity::class.java).apply {
                putExtra("NAMA_KELAS", namaKelas)
                putExtra("CLASS_ID", activeClassId)
                putExtra("KODE_KELAS", currentKodeKelas)
            }
            startActivity(intent)
        }
        binding.tabAnggota.setOnClickListener {
            openPesertaKelas(namaKelas, currentKodeKelas)
        }

        binding.fabTambahModul.setOnClickListener {
            showModuleFormDialog()
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

    private fun openForumDiskusi(kodeKelas: String? = null) {
        val classTitle = resolvedRombelName?.takeIf { it.isNotBlank() }
        val intent = Intent(this, ForumDiskusiActivity::class.java).apply {
            if (classTitle != null) {
                putExtra("ROMBEL_NAME", classTitle)
                putExtra("NAMA_KELAS", classTitle)
            }
            putExtra("CLASS_ID", activeClassId)
            if (kodeKelas != null) putExtra("KODE_KELAS", kodeKelas)
        }
        startActivity(intent)
    }

    private fun loadClassModules() {
        val classId = activeClassId?.takeIf { it.isNotBlank() }
        if (classId == null) {
            showFallbackModules()
            return
        }

        lifecycleScope.launch {
            when (val res = appContainer.moduleRepository.modules(classId)) {
                is ApiResult.Success -> {
                    loadedModules = res.data.modules
                    if (loadedModules.isNotEmpty()) {
                        renderDynamicModules(loadedModules)
                    } else {
                        showFallbackModules()
                    }
                }
                is ApiResult.Failure, is ApiResult.NetworkError -> {
                    showFallbackModules()
                }
            }
        }
    }

    private fun showFallbackModules() {
        binding.tabModul.text = "Modul (4)"
        binding.llDynamicModules.visibility = View.GONE
        binding.llDynamicModules.removeAllViews()
        binding.cardModul1.visibility = View.VISIBLE
        binding.cardModul2.visibility = View.VISIBLE
        binding.cardModul3.visibility = View.VISIBLE
        binding.cardModul4.visibility = View.VISIBLE
    }

    private fun renderDynamicModules(modules: List<ModuleItem>) {
        binding.tabModul.text = "Modul (${modules.size})"
        binding.cardModul1.visibility = View.GONE
        binding.cardModul2.visibility = View.GONE
        binding.cardModul3.visibility = View.GONE
        binding.cardModul4.visibility = View.GONE
        binding.llDynamicModules.visibility = View.VISIBLE
        binding.llDynamicModules.removeAllViews()

        if (selectedModuleId == null) {
            selectedModuleId = modules.firstOrNull { it.status == "ongoing" }?.id ?: modules.firstOrNull()?.id
        }

        modules.forEach { module ->
            binding.llDynamicModules.addView(buildDynamicModuleCard(module))
        }
    }

    private fun buildDynamicModuleCard(module: ModuleItem): MaterialCardView {
        val density = resources.displayMetrics.density
        val font = ResourcesCompat.getFont(this, R.font.baloo2)
        val primaryPurple = ContextCompat.getColor(this, R.color.kibooz_primary_purple)
        val borderColor = ContextCompat.getColor(this, R.color.kibooz_border)
        val isSelected = module.id == selectedModuleId || module.status == "ongoing"

        val card = MaterialCardView(this).apply {
            radius = 16 * density
            cardElevation = 2 * density
            setCardBackgroundColor(Color.WHITE)
            strokeColor = if (isSelected) primaryPurple else borderColor
            strokeWidth = (if (isSelected) 1.5f * density else density).toInt()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (8 * density).toInt()
            }
            setOnClickListener {
                selectedModuleId = module.id
                renderDynamicModules(loadedModules)
                Toast.makeText(
                    this@DetailKelasActivity,
                    "Pertemuan ${module.meetingNumber}: ${module.title}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (14 * density).toInt()
            setPadding(pad, pad, pad, pad)
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val badgeSize = (38 * density).toInt()
        val pBadge = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(badgeSize, badgeSize)
            gravity = Gravity.CENTER
            text = "P${module.meetingNumber}"
            textSize = 15f
            typeface = font
            paint.isFakeBoldText = true
            if (isSelected) {
                setBackgroundResource(R.drawable.bg_button_purple)
                setTextColor(Color.WHITE)
            } else {
                setBackgroundResource(R.drawable.bg_tag_lavender)
                setTextColor(primaryPurple)
            }
        }

        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = (12 * density).toInt()
            }
        }

        val titleView = TextView(this).apply {
            text = "Pertemuan ${module.meetingNumber}: ${module.title}"
            textSize = 14f
            typeface = font
            paint.isFakeBoldText = true
            setTextColor(Color.parseColor("#242038"))
        }

        val (statusLabel, statusColor) = when (module.status) {
            "completed" -> "Selesai" to Color.parseColor("#4CAF50")
            "ongoing" -> "Sedang Berlangsung" to primaryPurple
            else -> "Mendatang" to Color.parseColor("#75718E")
        }
        val dateText = (module.scheduledAt ?: module.createdAt).takeIf { it.isNotBlank() }?.let { TimeFormat.localDate(it) }
        val statusText = if (dateText != null) "$statusLabel • $dateText" else statusLabel

        val statusView = TextView(this).apply {
            text = statusText
            textSize = 12f
            typeface = font
            paint.isFakeBoldText = module.status == "ongoing"
            setTextColor(statusColor)
        }

        textCol.addView(titleView)
        textCol.addView(statusView)

        module.description?.trim()?.takeIf { it.isNotEmpty() }?.let { desc ->
            val descView = TextView(this).apply {
                text = desc
                textSize = 12f
                typeface = font
                setTextColor(Color.parseColor("#57536E"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = (2 * density).toInt()
                }
            }
            textCol.addView(descView)
        }

        row.addView(pBadge)
        row.addView(textCol)
        outer.addView(row)

        if (isTeacherRole) {
            val actionRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = (8 * density).toInt()
                }
            }

            val editChip = buildModuleChip("Edit", primaryPurple, Color.parseColor("#F1EEFD"), font, density) {
                showModuleFormDialog(module)
            }
            val deleteChip = buildModuleChip("Hapus", Color.parseColor("#DC2626"), Color.parseColor("#FEE2E2"), font, density) {
                confirmDeleteModule(module)
            }
            actionRow.addView(editChip)
            actionRow.addView(deleteChip)
            outer.addView(actionRow)
        }

        card.addView(outer)
        return card
    }

    private fun buildModuleChip(
        label: String,
        textColor: Int,
        bgColor: Int,
        font: android.graphics.Typeface?,
        density: Float,
        onClick: () -> Unit
    ): TextView = TextView(this).apply {
        text = label
        setTextColor(textColor)
        textSize = 12f
        typeface = font
        paint.isFakeBoldText = true
        setPadding((10 * density).toInt(), (4 * density).toInt(), (10 * density).toInt(), (4 * density).toInt())
        background = GradientDrawable().apply {
            cornerRadius = 12 * density
            setColor(bgColor)
        }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            marginStart = (8 * density).toInt()
        }
        setOnClickListener { onClick() }
    }

    private fun showModuleFormDialog(existing: ModuleItem? = null) {
        val classId = activeClassId?.takeIf { it.isNotBlank() }
        if (classId == null) {
            Toast.makeText(this, "Kelas aktif belum termuat", Toast.LENGTH_SHORT).show()
            return
        }

        val density = resources.displayMetrics.density
        val pad = (18 * density).toInt()
        val font = ResourcesCompat.getFont(this, R.font.baloo2)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, pad / 2)
        }

        val nextNumber = (loadedModules.maxOfOrNull { it.meetingNumber } ?: 0) + 1
        val etMeeting = EditText(this).apply {
            hint = "Nomor Pertemuan (1-100)"
            inputType = InputType.TYPE_CLASS_NUMBER
            typeface = font
            setText((existing?.meetingNumber ?: nextNumber).toString())
        }
        val etTitle = EditText(this).apply {
            hint = "Judul Modul Pertemuan"
            typeface = font
            setText(existing?.title.orEmpty())
        }
        val etDesc = EditText(this).apply {
            hint = "Deskripsi singkat (opsional)"
            typeface = font
            setText(existing?.description.orEmpty())
        }

        val rgStatus = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (8 * density).toInt()
            }
        }
        val rbCompleted = RadioButton(this).apply { text = "Selesai"; typeface = font }
        val rbOngoing = RadioButton(this).apply { text = "Berlangsung"; typeface = font }
        val rbUpcoming = RadioButton(this).apply { text = "Mendatang"; typeface = font }
        rgStatus.addView(rbCompleted)
        rgStatus.addView(rbOngoing)
        rgStatus.addView(rbUpcoming)

        when (existing?.status ?: "upcoming") {
            "completed" -> rbCompleted.isChecked = true
            "ongoing" -> rbOngoing.isChecked = true
            else -> rbUpcoming.isChecked = true
        }

        container.addView(etMeeting)
        container.addView(etTitle)
        container.addView(etDesc)
        container.addView(rgStatus)

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (existing == null) "Tambah Modul Pertemuan" else "Edit Modul Pertemuan")
            .setView(container)
            .setNegativeButton("Batal", null)
            .setPositiveButton("Simpan", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val number = etMeeting.text.toString().trim().toIntOrNull()
                val title = etTitle.text.toString().trim()
                val desc = etDesc.text.toString().trim()
                val status = when {
                    rbCompleted.isChecked -> "completed"
                    rbOngoing.isChecked -> "ongoing"
                    else -> "upcoming"
                }

                if (number == null || number !in 1..100) {
                    Toast.makeText(this, "Nomor pertemuan harus antara 1 sampai 100", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (title.isEmpty()) {
                    Toast.makeText(this, "Judul modul tidak boleh kosong", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                lifecycleScope.launch {
                    val result = if (existing == null) {
                        appContainer.moduleRepository.createModule(classId, number, title, desc, status)
                    } else {
                        appContainer.moduleRepository.updateModule(classId, existing.id, number, title, desc, status)
                    }
                    when (result) {
                        is ApiResult.Success -> {
                            dialog.dismiss()
                            Toast.makeText(
                                this@DetailKelasActivity,
                                if (existing == null) "Modul pertemuan ditambahkan" else "Modul pertemuan diperbarui",
                                Toast.LENGTH_SHORT
                            ).show()
                            loadClassModules()
                        }
                        is ApiResult.Failure -> {
                            val msg = if (result.status == 404) {
                                "Endpoint modul dinamis menunggu pembaruan server."
                            } else {
                                result.message
                            }
                            Toast.makeText(this@DetailKelasActivity, msg, Toast.LENGTH_SHORT).show()
                        }
                        is ApiResult.NetworkError -> {
                            Toast.makeText(this@DetailKelasActivity, "Jaringan tidak tersedia", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        dialog.show()
    }

    private fun confirmDeleteModule(module: ModuleItem) {
        val classId = activeClassId?.takeIf { it.isNotBlank() } ?: return
        AlertDialog.Builder(this)
            .setTitle("Hapus Modul")
            .setMessage("Hapus modul Pertemuan ${module.meetingNumber}: ${module.title}?")
            .setNegativeButton("Batal", null)
            .setPositiveButton("Hapus") { _, _ ->
                lifecycleScope.launch {
                    when (val res = appContainer.moduleRepository.deleteModule(classId, module.id)) {
                        is ApiResult.Success -> {
                            Toast.makeText(this@DetailKelasActivity, "Modul dihapus", Toast.LENGTH_SHORT).show()
                            loadClassModules()
                        }
                        is ApiResult.Failure -> {
                            Toast.makeText(this@DetailKelasActivity, res.message, Toast.LENGTH_SHORT).show()
                        }
                        is ApiResult.NetworkError -> {
                            Toast.makeText(this@DetailKelasActivity, "Jaringan tidak tersedia", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .show()
    }
}
