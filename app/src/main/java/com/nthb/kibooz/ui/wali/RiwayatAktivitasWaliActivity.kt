package com.nthb.kibooz.ui.wali

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.nthb.kibooz.R
import com.nthb.kibooz.appContainer
import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.WaliHistoryEvent
import com.nthb.kibooz.databinding.ActivityRiwayatAktivitasWaliBinding
import com.nthb.kibooz.ui.common.displayName
import com.nthb.kibooz.ui.common.emoji
import com.nthb.kibooz.util.InsetsUtil
import com.nthb.kibooz.util.TimeFormat
import kotlinx.coroutines.launch

class RiwayatAktivitasWaliActivity : AppCompatActivity() {

    private val binding by lazy { ActivityRiwayatAktivitasWaliBinding.inflate(layoutInflater) }
    private var studentId: String? = null
    private var selectedFilter: String = "all"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

        val chips = listOf(
            binding.chipWaliAll to "all",
            binding.chipWaliMood to "mood",
            binding.chipWaliTrash to "trash",
            binding.chipWaliGuidance to "guidance"
        )
        chips.forEach { (view, filter) ->
            view.setOnClickListener {
                selectedFilter = filter
                updateChipStyles(chips)
                loadCrossDateTimeline()
            }
        }
        updateChipStyles(chips)

        binding.cardTimelineMood.setOnClickListener {
            selectedFilter = "mood"
            updateChipStyles(chips)
            loadCrossDateTimeline()
        }
    }

    private fun updateChipStyles(chips: List<Pair<TextView, String>>) {
        val white = ContextCompat.getColor(this, R.color.kibooz_white)
        val muted = ContextCompat.getColor(this, R.color.kibooz_text_muted)
        chips.forEach { (chip, filter) ->
            val active = filter == selectedFilter
            chip.setBackgroundResource(if (active) R.drawable.bg_pill_date_active else R.drawable.bg_pill_date_inactive)
            chip.setTextColor(if (active) white else muted)
            chip.paint.isFakeBoldText = active
        }
    }

    private fun loadActivityHistory() {
        lifecycleScope.launch {
            when (val result = appContainer.waliRepository.dashboard()) {
                is ApiResult.Success -> {
                    val data = result.data
                    val student = data.student
                    val points = data.pointsSummary
                    if (studentId.isNullOrBlank()) {
                        studentId = student.id
                    }

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

                    loadCrossDateTimeline()
                }
                else -> {
                    binding.tvStudentSubtitle.text = "Data aktivitas si kecil tersimpan aman"
                    loadCrossDateTimeline()
                }
            }
        }
    }

    private fun loadCrossDateTimeline() {
        val targetId = studentId?.takeIf { it.isNotBlank() }
        if (targetId == null) {
            binding.tvCrossDateStatus.visibility = View.VISIBLE
            binding.tvCrossDateStatus.text = "Data siswa belum tersedia untuk riwayat lintas tanggal."
            return
        }

        clearCrossDateCards()
        binding.tvCrossDateStatus.visibility = View.VISIBLE
        binding.tvCrossDateStatus.text = "Memuat riwayat lintas tanggal..."

        lifecycleScope.launch {
            when (val res = appContainer.historyRepository.waliChildHistory(targetId, type = selectedFilter)) {
                is ApiResult.Success -> {
                    val events = res.data.events
                    if (events.isEmpty()) {
                        binding.tvCrossDateStatus.visibility = View.VISIBLE
                        binding.tvCrossDateStatus.text = "Belum ada riwayat tercatat untuk filter ini."
                        return@launch
                    }

                    binding.tvCrossDateStatus.visibility = View.GONE
                    events.forEach { event ->
                        binding.llCrossDateTimeline.addView(buildCrossDateCard(event))
                    }
                }
                is ApiResult.Failure -> {
                    binding.tvCrossDateStatus.visibility = View.VISIBLE
                    binding.tvCrossDateStatus.text = if (res.status == 404) {
                        "Riwayat lintas tanggal belum aktif di server saat ini."
                    } else {
                        res.message
                    }
                }
                is ApiResult.NetworkError -> {
                    binding.tvCrossDateStatus.visibility = View.VISIBLE
                    binding.tvCrossDateStatus.text = "Tidak dapat terhubung ke server."
                }
            }
        }
    }

    private fun clearCrossDateCards() {
        for (i in binding.llCrossDateTimeline.childCount - 1 downTo 0) {
            val child = binding.llCrossDateTimeline.getChildAt(i)
            if (child.id != R.id.tvCrossDateStatus) {
                binding.llCrossDateTimeline.removeViewAt(i)
            }
        }
    }

    private fun buildCrossDateCard(event: WaliHistoryEvent): MaterialCardView {
        val density = resources.displayMetrics.density
        val font = ResourcesCompat.getFont(this, R.font.baloo2)
        val card = MaterialCardView(this).apply {
            radius = 16 * density
            cardElevation = 2 * density
            setCardBackgroundColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (10 * density).toInt()
            }
        }

        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (14 * density).toInt()
            setPadding(pad, pad, pad, pad)
        }

        val (badgeLabel, badgeText, badgeBg) = when (event.type) {
            "mood" -> Triple("Mood Harian", Color.parseColor("#6B46C1"), Color.parseColor("#F1EEFD"))
            "trash" -> Triple("Pilah Sampah", Color.parseColor("#2F855A"), Color.parseColor("#E6FFFA"))
            else -> Triple("Panduan Asuh", Color.parseColor("#2B6CB0"), Color.parseColor("#EBF8FF"))
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val badge = TextView(this).apply {
            text = badgeLabel
            textSize = 11f
            typeface = font
            paint.isFakeBoldText = true
            setTextColor(badgeText)
            setPadding((9 * density).toInt(), (3 * density).toInt(), (9 * density).toInt(), (3 * density).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 10 * density
                setColor(badgeBg)
            }
        }

        val timeView = TextView(this).apply {
            text = TimeFormat.localDateTime(event.occurredAt) ?: event.occurredAt
            textSize = 11f
            typeface = font
            setTextColor(Color.parseColor("#75718E"))
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        topRow.addView(badge)
        topRow.addView(timeView)
        inner.addView(topRow)

        val titleView = TextView(this).apply {
            text = event.title
            textSize = 14f
            typeface = font
            paint.isFakeBoldText = true
            setTextColor(Color.parseColor("#242038"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (8 * density).toInt()
            }
        }
        inner.addView(titleView)

        val detailText = buildList {
            event.guidanceCategory?.takeIf { it.isNotBlank() }?.let { add("Kategori: $it") }
            event.description?.takeIf { it.isNotBlank() }?.let { add(it) }
        }
        if (detailText.isNotEmpty()) {
            val descView = TextView(this).apply {
                text = detailText.joinToString("\n")
                textSize = 12f
                typeface = font
                setTextColor(Color.parseColor("#57536E"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = (4 * density).toInt()
                }
            }
            inner.addView(descView)
        }

        card.addView(inner)
        return card
    }

    companion object {
        const val EXTRA_STUDENT_ID = "EXTRA_STUDENT_ID"
    }
}
