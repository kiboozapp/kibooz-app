package com.nthb.kibooz.ui.guru

import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.nthb.kibooz.R
import com.nthb.kibooz.appContainer
import com.nthb.kibooz.data.remote.ClassStudent
import com.nthb.kibooz.databinding.ActivityPesertaKelasBinding
import com.nthb.kibooz.ml.Emotion
import com.nthb.kibooz.sessionManager
import com.nthb.kibooz.util.InsetsUtil
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class PesertaKelasActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPesertaKelasBinding

    private val viewModel: PesertaKelasViewModel by viewModels {
        PesertaKelasViewModel.Factory(
            appContainer.classRepository,
            appContainer.leaderboardRepository,
            appContainer.authRepository
        )
    }

    private val removalViewModel: KeluarkanSiswaViewModel by viewModels {
        // Yang disimpan hanya pintu keluar akun milik proses, bukan layar ini
        val session = applicationContext.sessionManager
        KeluarkanSiswaViewModel.Factory(appContainer.classMemberRepository, session::signOut)
    }

    private var isTeacher = false
    private var namaKelas = ""
    private var confirmDialog: AlertDialog? = null
    private var renderedRows: List<Any?>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPesertaKelasBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Insets status bar
        InsetsUtil.applySystemBarInsets(
            root = binding.clRootPeserta,
            headerView = binding.clTopBar
        )

        namaKelas = intent.getStringExtra("NAMA_KELAS") ?: "Bahasa & Komunikasi"
        val classId = intent.getStringExtra("CLASS_ID")
        binding.tvKelasTitle.text = namaKelas

        val sessionUser = appContainer.authRepository.session.value?.user
        isTeacher = sessionUser?.role == com.nthb.kibooz.data.session.Role.GURU
        if (isTeacher) {
            appContainer.avatarLoader.load(binding.ivTeacherAvatar, sessionUser?.avatarUrl, com.nthb.kibooz.R.drawable.img_avatar_guru)
        }
        if (isTeacher && sessionUser?.fullName?.isNotBlank() == true) {
            binding.tvTeacherName.text = sessionUser.fullName
            binding.tvTeacherRole.text = "Wali Kelas & Pendidik Utama"
        } else {
            binding.tvTeacherName.text = "Ibu Dewi Lestari, S.Pd."
            binding.tvTeacherRole.text = "Wali Kelas & Pendidik Utama"
        }

        val refreshPendingMoveCard: (String) -> Unit = { targetClassId ->
            lifecycleScope.launch {
                val res = appContainer.moveRequestRepository.classMoveRequests(targetClassId)
                if (res is com.nthb.kibooz.data.remote.ApiResult.Success) {
                    val count = res.data.size
                    if (count > 0) {
                        binding.tvTitlePermintaanPindah.text = getString(com.nthb.kibooz.R.string.guru_pindah_cek_jumlah, count)
                        binding.tvSubPermintaanPindah.text = "$count pengajuan pindah kelas menunggu persetujuan Anda"
                    } else {
                        binding.tvTitlePermintaanPindah.text = getString(com.nthb.kibooz.R.string.guru_pindah_cek)
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

        setupListeners(namaKelas)
        observeViewModel()

        viewModel.loadPeserta(classId, namaKelas)
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { removalViewModel.state.collect(::handleRemoval) }
                launch {
                    combine(viewModel.uiState, removalViewModel.state) { roster, removal -> roster to removal }
                        .collect { (roster, removal) -> render(roster, removal) }
                }
            }
        }
    }

    private fun render(state: PesertaUiState, removal: KeluarkanSiswaState) {
        if (isFinishing) return
        if (isTeacher && state.accessLost) {
            confirmDialog?.dismiss()
            closeClassScreens(getString(R.string.error_class_gone))
            return
        }

        if (state.className.isNotBlank()) {
            binding.tvKelasTitle.text = state.className
        }
        binding.tvTeacherName.text = state.teacherName
        binding.tvTeacherRole.text = state.teacherRole
        binding.tvStudentCount.text = "${state.students.size} Siswa Terdaftar"

        if (state.students.isNotEmpty()) {
            binding.tvEmptyPeserta.visibility = View.GONE
            // Baris hanya dibangun ulang saat isinya berubah, supaya fokus pembaca layar tidak berpindah tiap detik
            val key = listOf(state.students, state.classId, removal.archived, removal.locked)
            if (key != renderedRows) {
                renderedRows = key
                renderStudents(state, removal)
            }
        } else if (!state.isLoading) {
            renderedRows = null
            // Teks kosong adalah anak dari daftar, jadi dipasang lagi setelah baris dibersihkan
            binding.llStudentList.removeAllViews()
            binding.llStudentList.addView(binding.tvEmptyPeserta)
            binding.tvEmptyPeserta.visibility = View.VISIBLE
        }
    }

    /** Akibat dari mengeluarkan anak: pesan, perubahan daftar, atau menutup layar kelas. */
    private fun handleRemoval(removal: KeluarkanSiswaState) {
        if (isFinishing) return
        val end = removal.end
        if (end != null) {
            confirmDialog?.dismiss()
            if (end == ClassScreenEnd.ACCOUNT_GONE) {
                openRoleSelectionForGoneAccount()
            } else {
                closeClassScreens(getString(R.string.error_class_gone))
            }
            return
        }

        removal.notice?.let {
            Snackbar.make(binding.root, getString(it.res, *it.args.toTypedArray()), Snackbar.LENGTH_LONG).show()
            removalViewModel.noticeShown()
        }

        if (removal.reload || removal.removedStudentId != null) {
            val classId = viewModel.uiState.value.classId ?: intent.getStringExtra("CLASS_ID")
            removal.removedStudentId?.let(viewModel::onStudentRemoved)
            if (removal.reload) viewModel.loadPeserta(classId, namaKelas)
            removalViewModel.rosterHandled()
        }
    }

    private fun confirmRemoval(classId: String, student: ClassStudent) {
        confirmDialog?.dismiss()
        confirmDialog = MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.peserta_keluarkan_judul, student.fullName))
            .setMessage(getString(R.string.peserta_keluarkan_isi, student.fullName))
            .setPositiveButton(R.string.peserta_keluarkan_ya) { _, _ ->
                removalViewModel.remove(classId, student.id, student.fullName)
            }
            .setNegativeButton(R.string.batal, null)
            .show()
    }

    override fun onDestroy() {
        confirmDialog?.dismiss()
        super.onDestroy()
    }

    private fun renderStudents(state: PesertaUiState, removal: KeluarkanSiswaState) {
        binding.llStudentList.removeAllViews()

        val balooFont = ResourcesCompat.getFont(this, R.font.baloo2)
        val density = resources.displayMetrics.density
        val students = state.students
        // Tombol keluarkan hanya untuk Guru, pada daftar yang kelasnya diketahui dan belum diarsipkan
        val removableClassId = state.classId?.takeIf { isTeacher && !removal.archived }

        students.forEachIndexed { index, student ->
            val row = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                minimumHeight = (58 * density).toInt()
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val endPadding = if (removableClassId != null) 4 else 16
                setPaddingRelative((16 * density).toInt(), 0, (endPadding * density).toInt(), 0)
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
                text = if (student.nisn != "-" && student.nisn.isNotBlank()) {
                    "NISN: ${student.nisn} • ${student.currentPoints} Poin"
                } else {
                    "Peringkat #${student.classRank} • ${student.currentPoints} Poin Eco"
                }
                setTextColor(Color.parseColor("#75718E"))
                textSize = 11f
                typeface = balooFont
            }
            textContainer.addView(nameText)
            textContainer.addView(subText)
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

            if (removableClassId != null) {
                row.addView(removeButton(removableClassId, student, enabled = !removal.locked))
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

    private fun removeButton(classId: String, student: ClassStudent, enabled: Boolean): ImageButton {
        val size = (48 * resources.displayMetrics.density).toInt()
        val ripple = TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true)

        return ImageButton(this).apply {
            layoutParams = LinearLayout.LayoutParams(size, size)
            setImageResource(R.drawable.ic_trash_delete)
            setBackgroundResource(ripple.resourceId)
            contentDescription = getString(R.string.peserta_keluarkan_cd, student.fullName)
            isEnabled = enabled
            alpha = if (enabled) 1f else 0.4f
            setOnClickListener { confirmRemoval(classId, student) }
        }
    }

    private fun setupListeners(namaKelas: String) {
        binding.ivBack.setOnClickListener {
            finish()
        }

        binding.ivMenu.setOnClickListener {
            Toast.makeText(this, "Kelola Anggota Kelas $namaKelas", Toast.LENGTH_SHORT).show()
        }
    }
}
