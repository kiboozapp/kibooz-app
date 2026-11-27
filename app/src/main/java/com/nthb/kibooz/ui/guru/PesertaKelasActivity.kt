package com.nthb.kibooz.ui.guru

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar
import com.nthb.kibooz.R
import com.nthb.kibooz.appContainer
import com.nthb.kibooz.data.remote.ClassStudent
import com.nthb.kibooz.databinding.ActivityPesertaKelasBinding
import com.nthb.kibooz.ml.Emotion
import com.nthb.kibooz.util.ClassTeachers
import com.nthb.kibooz.util.InsetsUtil
import kotlinx.coroutines.launch

class PesertaKelasActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPesertaKelasBinding
    private var loadedTeacherAvatarUrl: String? = null
    private var hasLoadedTeacherAvatar: Boolean = false

    private val viewModel: PesertaKelasViewModel by viewModels {
        PesertaKelasViewModel.Factory(
            appContainer.classRepository,
            appContainer.leaderboardRepository,
            appContainer.authRepository,
            appContainer.classManagementRepository,
            appContainer.chatRepository
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPesertaKelasBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Insets status bar
        InsetsUtil.applySystemBarInsets(
            root = binding.clRootPeserta,
            headerView = binding.clTopBar
        )

        val rombelName = intent.getStringExtra("ROMBEL_NAME")?.takeIf { it.isNotBlank() }
            ?: intent.getStringExtra("CHILD_CLASS_NAME")?.takeIf { it.isNotBlank() }
        val namaKelas = rombelName.orEmpty()
        val headerTitle = rombelName
            ?: intent.getStringExtra("NAMA_KELAS")?.takeIf { it.isNotBlank() }
            ?: "Daftar Peserta"
        val classId = intent.getStringExtra("CLASS_ID")

        val sessionUser = appContainer.authRepository.session.value?.user
        val isTeacher = sessionUser?.role == com.nthb.kibooz.data.session.Role.GURU

        prefillInitialUi(headerTitle, namaKelas, sessionUser, isTeacher)
        setupMoveRequestsSection(classId, namaKelas, isTeacher)
        setupListeners(headerTitle)
        observeViewModel()

        viewModel.loadPeserta(classId, namaKelas)
    }

    private fun prefillInitialUi(
        headerTitle: String,
        namaKelas: String,
        sessionUser: com.nthb.kibooz.data.session.SessionUser?,
        isTeacher: Boolean
    ) {
        val initialTeacherName = intent.getStringExtra("TEACHER_NAME")?.takeIf { it.isNotBlank() }
        val initialTeacherAvatarUrl = intent.getStringExtra("TEACHER_AVATAR_URL")?.takeIf { it.isNotBlank() }
        binding.tvKelasTitle.text = headerTitle

        val prefilledAvatar = initialTeacherAvatarUrl ?: sessionUser?.avatarUrl?.takeIf { isTeacher }
        loadedTeacherAvatarUrl = prefilledAvatar
        hasLoadedTeacherAvatar = true
        appContainer.avatarLoader.load(binding.ivTeacherAvatar, prefilledAvatar, R.drawable.img_avatar_guru)

        binding.tvTeacherName.text = initialTeacherName
            ?: sessionUser?.fullName?.takeIf { isTeacher && it.isNotBlank() }
            ?: ClassTeachers.DEFAULT_TEACHER_LABEL
        binding.tvTeacherRole.text = ClassTeachers.formatSubtitle(emptyList(), namaKelas)
    }

    private fun setupMoveRequestsSection(classId: String?, namaKelas: String, isTeacher: Boolean) {
        val refreshPendingMoveCard: (String) -> Unit = { targetClassId ->
            lifecycleScope.launch {
                val res = appContainer.moveRequestRepository.classMoveRequests(targetClassId)
                if (res is com.nthb.kibooz.data.remote.ApiResult.Success) {
                    val count = res.data.size
                    if (count > 0) {
                        binding.tvTitlePermintaanPindah.text = getString(R.string.guru_pindah_cek_jumlah, count)
                        binding.tvSubPermintaanPindah.text = "$count pengajuan pindah kelas menunggu persetujuan Anda"
                    } else {
                        binding.tvTitlePermintaanPindah.text = getString(R.string.guru_pindah_cek)
                        binding.tvSubPermintaanPindah.text = "Belum ada pengajuan pindah masuk yang menunggu"
                    }
                }
            }
        }

        val moveRequestsLauncher = registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK && !classId.isNullOrBlank()) {
                viewModel.loadPeserta(classId, namaKelas)
                refreshPendingMoveCard(classId)
            }
        }

        if (isTeacher && !classId.isNullOrBlank()) {
            binding.cardPermintaanPindah.visibility = View.VISIBLE
            binding.cardPermintaanPindah.setOnClickListener {
                val currentTitle = binding.tvKelasTitle.text?.toString()?.takeIf { it.isNotBlank() } ?: namaKelas
                moveRequestsLauncher.launch(PermintaanPindahKelasActivity.intent(this, classId, currentTitle))
            }
            refreshPendingMoveCard(classId)
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (state.className.isNotBlank()) {
                        binding.tvKelasTitle.text = state.className
                    }
                    binding.tvTeacherName.text = state.teacherName
                    binding.tvTeacherRole.text = state.teacherRole
                    if (!hasLoadedTeacherAvatar || loadedTeacherAvatarUrl != state.teacherAvatarUrl) {
                        hasLoadedTeacherAvatar = true
                        loadedTeacherAvatarUrl = state.teacherAvatarUrl
                        appContainer.avatarLoader.load(
                            binding.ivTeacherAvatar,
                            state.teacherAvatarUrl,
                            R.drawable.img_avatar_guru
                        )
                    }
                    binding.tvStudentCount.text = "${state.students.size} Siswa Terdaftar"
                    binding.tvNisnHintGuru.isVisible = state.isTeacher && state.students.isNotEmpty()
                    binding.ivMenu.isVisible = state.isTeacher

                    if (state.students.isNotEmpty()) {
                        binding.tvEmptyPeserta.visibility = View.GONE
                        renderStudents(state.students, state)
                    } else if (!state.isLoading) {
                        binding.llStudentList.removeAllViews()
                        binding.tvEmptyPeserta.visibility = View.VISIBLE
                    }

                    state.actionMessage?.let { msg ->
                        Snackbar.make(binding.root, formatActionMessage(msg), Snackbar.LENGTH_LONG).show()
                        viewModel.actionMessageShown()
                    }
                }
            }
        }
    }

    private fun formatActionMessage(msg: PesertaMessage): String = when (msg.resId) {
        R.string.peserta_nisn_sukses_verif_geser -> getString(msg.resId, msg.studentName.orEmpty(), msg.count)
        R.string.peserta_nisn_sukses_verif,
        R.string.peserta_nisn_sukses_batal,
        R.string.peserta_keluarkan_sukses -> getString(msg.resId, msg.studentName.orEmpty())
        else -> getString(msg.resId)
    }

    private fun renderStudents(students: List<ClassStudent>, state: PesertaUiState) {
        binding.llStudentList.removeAllViews()

        val balooFont = ResourcesCompat.getFont(this, R.font.baloo2)
        val density = resources.displayMetrics.density
        val isTeacher = state.isTeacher

        students.forEachIndexed { index, student ->
            val row = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                minimumHeight = (66 * density).toInt()
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding((16 * density).toInt(), (10 * density).toInt(), (16 * density).toInt(), (10 * density).toInt())
                if (isTeacher) {
                    val ripple = android.util.TypedValue()
                    context.theme.resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)
                    setBackgroundResource(ripple.resourceId)
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { showStudentOptionsDialog(student, state) }
                }
            }

            val card = MaterialCardView(this).apply {
                layoutParams = LinearLayout.LayoutParams((40 * density).toInt(), (40 * density).toInt())
                radius = 20 * density
                cardElevation = 0f
            }
            val avatar = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
                scaleType = ImageView.ScaleType.CENTER_CROP
                appContainer.avatarLoader.load(this, student.avatarUrl, R.drawable.img_avatar_siswa)
            }
            card.addView(avatar)
            row.addView(card)

            val textContainer = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = (14 * density).toInt()
                    marginEnd = (8 * density).toInt()
                }
                orientation = LinearLayout.VERTICAL
            }
            val nameText = TextView(this).apply {
                text = student.fullName
                setTextColor(Color.parseColor("#2A2740"))
                textSize = 15f
                typeface = balooFont
                paint.isFakeBoldText = true
            }
            val subText = TextView(this).apply {
                text = when {
                    isTeacher && (student.nisnNeedsUpdate || student.nisn.isBlank()) ->
                        "${getString(R.string.peserta_nisn_kosong_label)} • ${student.currentPoints} Poin"
                    student.nisn != "-" && student.nisn.isNotBlank() ->
                        "NISN: ${student.nisn} • ${student.currentPoints} Poin"
                    else ->
                        "Peringkat #${student.classRank} • ${student.currentPoints} Poin Eco"
                }
                setTextColor(Color.parseColor("#75718E"))
                textSize = 11.5f
                typeface = balooFont
            }
            textContainer.addView(nameText)
            textContainer.addView(subText)

            if (isTeacher) {
                val badge = createNisnBadgeView(student, state, balooFont, density)
                badge.setOnClickListener { onStudentNisnClicked(student, state) }
                textContainer.addView(badge)
            }

            row.addView(textContainer)

            val moodBadge = TextView(this).apply {
                typeface = balooFont
                textSize = 12f
                paint.isFakeBoldText = true

                when (student.todayMood) {
                    Emotion.SENANG -> {
                        text = "😊 Senang"
                        setTextColor(Color.parseColor("#10B981"))
                    }
                    Emotion.SEDIH -> {
                        text = "😢 Sedih"
                        setTextColor(Color.parseColor("#6366F1"))
                    }
                    Emotion.BINGUNG -> {
                        text = "🤔 Bingung"
                        setTextColor(Color.parseColor("#F59E0B"))
                    }
                    Emotion.MARAH -> {
                        text = "😡 Marah"
                        setTextColor(Color.parseColor("#EF4444"))
                    }
                    null -> {
                        text = "Siswa Aktif"
                        setTextColor(Color.parseColor("#6B7280"))
                    }
                }
            }
            row.addView(moodBadge)

            if (isTeacher) {
                val moreButton = ImageView(this).apply {
                    layoutParams = LinearLayout.LayoutParams((36 * density).toInt(), (36 * density).toInt()).apply {
                        marginStart = (4 * density).toInt()
                    }
                    val borderlessRipple = android.util.TypedValue()
                    context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, borderlessRipple, true)
                    setBackgroundResource(borderlessRipple.resourceId)
                    setPadding((6 * density).toInt(), (6 * density).toInt(), (6 * density).toInt(), (6 * density).toInt())
                    setImageResource(R.drawable.ic_more_vert)
                    contentDescription = getString(R.string.peserta_opsi_cd, student.fullName)
                    setOnClickListener { showStudentOptionsDialog(student, state) }
                }
                row.addView(moreButton)
            }

            binding.llStudentList.addView(row)

            if (index < students.size - 1) {
                val divider = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        (1 * density).toInt()
                    ).apply {
                        setMargins((16 * density).toInt(), 0, (16 * density).toInt(), 0)
                    }
                    setBackgroundColor(Color.parseColor("#F3F0F9"))
                }
                binding.llStudentList.addView(divider)
            }
        }
    }

    private fun createNisnBadgeView(
        student: ClassStudent,
        state: PesertaUiState,
        balooFont: android.graphics.Typeface?,
        density: Float
    ): TextView {
        val isBusy = state.busyStudentId == student.id
        val needsInput = student.nisnNeedsUpdate || student.nisn.isBlank() || student.nisn == "-"
        val label: String
        val fillColor: Int
        val strokeColor: Int
        val textColor: Int

        when {
            isBusy -> {
                label = getString(R.string.peserta_nisn_memproses)
                fillColor = Color.parseColor("#ECEBFA")
                strokeColor = Color.parseColor("#D1C4E9")
                textColor = Color.parseColor("#5D40B3")
            }
            student.nisnVerified -> {
                label = "✓ ${getString(R.string.peserta_nisn_terverifikasi)}"
                fillColor = Color.parseColor("#DCFCE7")
                strokeColor = Color.parseColor("#86EFAC")
                textColor = Color.parseColor("#166534")
            }
            needsInput -> {
                label = "! ${getString(R.string.peserta_nisn_perlu_isi)}"
                fillColor = Color.parseColor("#FEF3C7")
                strokeColor = Color.parseColor("#FCD34D")
                textColor = Color.parseColor("#92400E")
            }
            else -> {
                label = getString(R.string.peserta_nisn_verifikasi_aksi)
                fillColor = Color.parseColor("#F5F3FF")
                strokeColor = Color.parseColor("#5D40B3")
                textColor = Color.parseColor("#5D40B3")
            }
        }

        val pill = GradientDrawable().apply {
            cornerRadius = 12 * density
            setColor(fillColor)
            setStroke((1 * density).toInt().coerceAtLeast(1), strokeColor)
        }

        return TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (5 * density).toInt()
            }
            background = pill
            text = label
            typeface = balooFont
            textSize = 11f
            paint.isFakeBoldText = !needsInput
            setTextColor(textColor)
            setPadding((10 * density).toInt(), (3 * density).toInt(), (10 * density).toInt(), (3 * density).toInt())
        }
    }

    private fun showStudentOptionsDialog(student: ClassStudent, state: PesertaUiState) {
        if (state.busyStudentId != null) return
        if (state.classArchived) {
            Snackbar.make(binding.root, R.string.error_class_archived, Snackbar.LENGTH_LONG).show()
            return
        }

        val needsInput = student.nisnNeedsUpdate || student.nisn.isBlank() || student.nisn == "-"
        val nisnLabel: CharSequence = when {
            needsInput -> getString(R.string.peserta_opsi_nisn_kosong)
            student.nisnVerified -> getString(R.string.peserta_opsi_batal_nisn, student.nisn)
            else -> getString(R.string.peserta_opsi_verifikasi_nisn, student.nisn)
        }
        val kickText = SpannableString(getString(R.string.peserta_opsi_keluarkan)).apply {
            setSpan(
                ForegroundColorSpan(ContextCompat.getColor(this@PesertaKelasActivity, R.color.kibooz_red_danger)),
                0,
                length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.peserta_opsi_judul, student.fullName))
            .setItems(arrayOf(nisnLabel, kickText)) { _, which ->
                when (which) {
                    0 -> onStudentNisnClicked(student, state)
                    1 -> confirmRemoveStudent(student)
                }
            }
            .setNegativeButton(R.string.batal, null)
            .show()
    }

    private fun confirmRemoveStudent(student: ClassStudent) {
        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.peserta_keluarkan_judul, student.fullName))
            .setMessage(getString(R.string.peserta_keluarkan_pesan, student.fullName))
            .setPositiveButton(R.string.peserta_keluarkan_ya) { _, _ ->
                viewModel.removeStudent(student)
            }
            .setNegativeButton(R.string.batal, null)
            .show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            ?.setTextColor(ContextCompat.getColor(this, R.color.kibooz_red_danger))
    }

    private fun onStudentNisnClicked(student: ClassStudent, state: PesertaUiState) {
        if (state.busyStudentId != null) return
        if (state.classArchived) {
            Snackbar.make(binding.root, R.string.error_class_archived, Snackbar.LENGTH_LONG).show()
            return
        }

        val needsInput = student.nisnNeedsUpdate || student.nisn.isBlank() || student.nisn == "-"
        when {
            needsInput -> {
                AlertDialog.Builder(this)
                    .setTitle(R.string.peserta_nisn_dialog_kosong_judul)
                    .setMessage(getString(R.string.peserta_nisn_dialog_kosong_pesan, student.fullName))
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
            student.nisnVerified -> {
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.peserta_nisn_dialog_batal_judul, student.fullName))
                    .setMessage(getString(R.string.peserta_nisn_dialog_batal_pesan, student.nisn))
                    .setPositiveButton(R.string.peserta_nisn_dialog_batal_ya) { _, _ ->
                        viewModel.unverifyStudentNisn(student)
                    }
                    .setNegativeButton(R.string.batal, null)
                    .show()
            }
            else -> {
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.peserta_nisn_dialog_verif_judul, student.fullName))
                    .setMessage(getString(R.string.peserta_nisn_dialog_verif_pesan, student.nisn))
                    .setPositiveButton(R.string.peserta_nisn_dialog_verif_ya) { _, _ ->
                        viewModel.verifyStudentNisn(student)
                    }
                    .setNegativeButton(R.string.batal, null)
                    .show()
            }
        }
    }

    private fun setupListeners(namaKelas: String) {
        binding.ivBack.setOnClickListener {
            finish()
        }

        binding.ivMenu.setOnClickListener {
            val students = viewModel.uiState.value.students
            val verified = students.count { it.nisnVerified }
            val needsUpdate = students.count { !it.nisnVerified && (it.nisnNeedsUpdate || it.nisn.isBlank() || it.nisn == "-") }
            val pending = students.size - verified - needsUpdate
            val title = viewModel.uiState.value.className.ifBlank { namaKelas }
            AlertDialog.Builder(this)
                .setTitle(R.string.peserta_nisn_ringkasan_judul)
                .setMessage(getString(R.string.peserta_nisn_ringkasan_isi, title, verified, pending, needsUpdate))
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }
}
