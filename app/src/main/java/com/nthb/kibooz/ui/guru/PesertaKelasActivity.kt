package com.nthb.kibooz.ui.guru

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.card.MaterialCardView
import com.nthb.kibooz.R
import com.nthb.kibooz.appContainer
import com.nthb.kibooz.data.remote.ClassStudent
import com.nthb.kibooz.databinding.ActivityPesertaKelasBinding
import com.nthb.kibooz.ml.Emotion
import com.nthb.kibooz.util.InsetsUtil
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPesertaKelasBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Insets status bar
        InsetsUtil.applySystemBarInsets(
            root = binding.clRootPeserta,
            headerView = binding.clTopBar
        )

        val namaKelas = intent.getStringExtra("NAMA_KELAS") ?: "Bahasa & Komunikasi"
        val classId = intent.getStringExtra("CLASS_ID")
        binding.tvKelasTitle.text = namaKelas

        val sessionUser = appContainer.authRepository.session.value?.user
        val isTeacher = sessionUser?.role == com.nthb.kibooz.data.session.Role.GURU
        if (isTeacher && sessionUser?.fullName?.isNotBlank() == true) {
            binding.tvTeacherName.text = sessionUser.fullName
            binding.tvTeacherRole.text = "Wali Kelas & Pendidik Utama"
        } else {
            binding.tvTeacherName.text = "Ibu Dewi Lestari, S.Pd."
            binding.tvTeacherRole.text = "Wali Kelas & Pendidik Utama"
        }

        setupListeners(namaKelas)
        observeViewModel()

        viewModel.loadPeserta(classId, namaKelas)
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
                    binding.tvStudentCount.text = "${state.students.size} Siswa Terdaftar"

                    if (state.students.isNotEmpty()) {
                        binding.tvEmptyPeserta.visibility = View.GONE
                        renderStudents(state.students)
                    } else if (!state.isLoading) {
                        binding.llStudentList.removeAllViews()
                        binding.tvEmptyPeserta.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    private fun renderStudents(students: List<ClassStudent>) {
        binding.llStudentList.removeAllViews()

        val balooFont = ResourcesCompat.getFont(this, R.font.baloo2)
        val density = resources.displayMetrics.density

        students.forEachIndexed { index, student ->
            val row = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    (58 * density).toInt()
                )
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding((16 * density).toInt(), 0, (16 * density).toInt(), 0)
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
                setImageResource(R.drawable.img_avatar_siswa)
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

    private fun setupListeners(namaKelas: String) {
        binding.ivBack.setOnClickListener {
            finish()
        }

        binding.ivMenu.setOnClickListener {
            Toast.makeText(this, "Kelola Anggota Kelas $namaKelas", Toast.LENGTH_SHORT).show()
        }
    }
}
