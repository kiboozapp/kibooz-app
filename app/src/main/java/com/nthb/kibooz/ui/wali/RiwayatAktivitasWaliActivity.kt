package com.nthb.kibooz.ui.wali

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.nthb.kibooz.appContainer
import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.databinding.ActivityRiwayatAktivitasWaliBinding
import com.nthb.kibooz.ui.common.displayName
import com.nthb.kibooz.ui.common.emoji
import com.nthb.kibooz.util.InsetsUtil
import com.nthb.kibooz.util.TimeFormat
import kotlinx.coroutines.launch

class RiwayatAktivitasWaliActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRiwayatAktivitasWaliBinding
    private var studentId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRiwayatAktivitasWaliBinding.inflate(layoutInflater)
        setContentView(binding.root)

        studentId = intent.getStringExtra(EXTRA_STUDENT_ID)

        InsetsUtil.applySystemBarInsets(
            root = binding.root,
            headerView = binding.clTopBar
        )

        setupListeners()
        loadActivityHistory()
    }

    private fun setupListeners() {
        binding.ivBack.setOnClickListener {
            finish()
        }

        binding.cardTimelineSampah.setOnClickListener {
            startActivity(
                Intent(this, StatistikSampahActivity::class.java)
                    .putExtra(StatistikSampahActivity.EXTRA_STUDENT_ID, studentId)
            )
        }

        binding.cardTimelineMood.setOnClickListener {
            Toast.makeText(this, "Riwayat pencatatan mood si kecil", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadActivityHistory() {
        lifecycleScope.launch {
            when (val result = appContainer.waliRepository.dashboard()) {
                is ApiResult.Success -> {
                    val data = result.data
                    val student = data.student
                    val points = data.pointsSummary

                    binding.tvStudentSubtitle.text = "${student.fullName} • ${student.className} • NISN: ${student.nisn}"
                    binding.tvRank.text = if (points.classRank > 0) "#${points.classRank}" else "-"
                    binding.tvTotalPoints.text = points.totalPoints.toString()
                    binding.tvSampahCount.text = points.sortedCount.toString()

                    // Riwayat Mood
                    val mood = data.todayMood
                    if (mood != null) {
                        binding.tvTimelineMoodTitle.text = "Mood: ${mood.moodType.emoji()} ${mood.moodType.displayName()}"
                        binding.tvTimelineMoodNotes.text = "Catatan guru: ${mood.teacherNotes?.takeIf { it.isNotBlank() } ?: "Tidak ada catatan tambahan."}"
                        binding.tvTimelineMoodTime.text = TimeFormat.localTime(mood.recordedAt) ?: "Hari ini"
                    } else {
                        binding.tvTimelineMoodTitle.text = "Mood: Belum ada pencatatan hari ini"
                        binding.tvTimelineMoodNotes.text = "Guru kelas mencatat suasana hati anak setiap pagi untuk menjaga kenyamanan belajar si kecil."
                    }

                    // Riwayat Sampah
                    binding.tvTimelineSampahDetail.text =
                        "Pilah Sampah Mandiri: ${points.organicCount} Organik & ${points.anorganicCount} Anorganik (${points.sortedCount} Total Terpilah)"

                    // Riwayat Panduan
                    val guidance = data.recommendedGuidance
                    if (guidance != null) {
                        binding.tvTimelineGuidanceTitle.text = guidance.title
                        binding.tvTimelineGuidanceCategory.text = "Kategori: ${guidance.category}"
                        binding.cardTimelineGuidance.setOnClickListener {
                            startActivity(
                                Intent(this@RiwayatAktivitasWaliActivity, DetailPenangananMoodActivity::class.java)
                                    .putExtra(GuidanceExtras.STUDENT_ID, student.id)
                                    .putExtra(GuidanceExtras.GUIDANCE_ID, guidance.id)
                                    .putExtra(GuidanceExtras.TITLE, guidance.title)
                                    .putExtra(GuidanceExtras.CATEGORY, guidance.category)
                            )
                        }
                    } else {
                        binding.cardTimelineGuidance.visibility = View.GONE
                    }
                }
                else -> {
                    binding.tvStudentSubtitle.text = "Data aktivitas si kecil tersimpan aman"
                }
            }
        }
    }

    companion object {
        const val EXTRA_STUDENT_ID = "EXTRA_STUDENT_ID"
    }
}
