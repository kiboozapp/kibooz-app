package com.nthb.kibooz.ui.guru

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
import com.nthb.kibooz.data.remote.GuruHistoryEvent
import com.nthb.kibooz.databinding.ActivityGuruHistoryBinding
import com.nthb.kibooz.util.InsetsUtil
import com.nthb.kibooz.util.TimeFormat
import kotlinx.coroutines.launch

/** Linimasa riwayat aktivitas kelas lintas tanggal untuk Guru (mood, panduan wali, video). */
class GuruHistoryActivity : AppCompatActivity() {

    private val binding by lazy { ActivityGuruHistoryBinding.inflate(layoutInflater) }
    private var activeClassId: String? = null
    private var selectedFilter: String = "all"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        InsetsUtil.applySystemBarInsets(root = binding.root, headerView = binding.clTopBar)

        activeClassId = intent.getStringExtra(EXTRA_CLASS_ID)
            ?: appContainer.guruClassSelection.current(appContainer.authRepository.session.value?.user?.id)

        val className = intent.getStringExtra(EXTRA_CLASS_NAME)
        if (!className.isNullOrBlank()) {
            binding.tvClassSubtitle.text = "Aktivitas kelas $className"
        }

        binding.ivBack.setOnClickListener { finish() }

        val chips = listOf(
            binding.chipAll to "all",
            binding.chipMood to "mood",
            binding.chipGuidance to "guidance",
            binding.chipVideo to "video"
        )
        chips.forEach { (view, filter) ->
            view.setOnClickListener {
                selectedFilter = filter
                updateChipStyles(chips)
                loadHistory()
            }
        }

        updateChipStyles(chips)
        loadHistory()
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

    private fun loadHistory() {
        binding.tvEmptyState.visibility = View.VISIBLE
        binding.tvEmptyState.text = "Memuat riwayat aktivitas kelas..."
        removeDynamicCards()

        lifecycleScope.launch {
            when (val res = appContainer.historyRepository.guruHistory(classId = activeClassId, type = selectedFilter)) {
                is ApiResult.Success -> {
                    val events = res.data.events
                    if (events.isEmpty()) {
                        binding.tvEmptyState.visibility = View.VISIBLE
                        binding.tvEmptyState.text = "Belum ada aktivitas tercatat untuk filter ini."
                        return@launch
                    }

                    val rawClass = events.first().className
                    val label = if (rawClass.startsWith("Kelas", ignoreCase = true)) rawClass else "Kelas $rawClass"
                    binding.tvClassSubtitle.text = "$label (${res.data.total} catatan)"
                    binding.tvEmptyState.visibility = View.GONE
                    events.forEach { event ->
                        binding.llTimelineContainer.addView(buildEventCard(event))
                    }
                }
                is ApiResult.Failure -> {
                    binding.tvEmptyState.visibility = View.VISIBLE
                    binding.tvEmptyState.text = if (res.status == 404) {
                        "Riwayat lintas tanggal belum tersedia di server saat ini."
                    } else {
                        res.message
                    }
                }
                is ApiResult.NetworkError -> {
                    binding.tvEmptyState.visibility = View.VISIBLE
                    binding.tvEmptyState.text = "Tidak dapat terhubung ke server."
                }
            }
        }
    }

    private fun removeDynamicCards() {
        for (i in binding.llTimelineContainer.childCount - 1 downTo 0) {
            val child = binding.llTimelineContainer.getChildAt(i)
            if (child.id != R.id.tvEmptyState) {
                binding.llTimelineContainer.removeViewAt(i)
            }
        }
    }

    private fun buildEventCard(event: GuruHistoryEvent): MaterialCardView {
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

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (14 * density).toInt()
            setPadding(pad, pad, pad, pad)
        }

        val (badgeLabel, badgeText, badgeBg) = when (event.type) {
            "mood" -> Triple("Mood Siswa", Color.parseColor("#6B46C1"), Color.parseColor("#F1EEFD"))
            "guidance" -> Triple("Panduan Wali", Color.parseColor("#2B6CB0"), Color.parseColor("#EBF8FF"))
            else -> Triple("Video Belajar", Color.parseColor("#2F855A"), Color.parseColor("#E6FFFA"))
        }

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val badgeView = TextView(this).apply {
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

        headerRow.addView(badgeView)
        headerRow.addView(timeView)
        container.addView(headerRow)

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
        container.addView(titleView)

        val detailParts = buildList {
            event.actorName?.takeIf { it.isNotBlank() }?.let { add("Oleh: $it") }
            event.description?.takeIf { it.isNotBlank() }?.let { add(it) }
        }
        if (detailParts.isNotEmpty()) {
            val descView = TextView(this).apply {
                text = detailParts.joinToString("\n")
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
            container.addView(descView)
        }

        card.addView(container)
        return card
    }

    companion object {
        const val EXTRA_CLASS_ID = "EXTRA_CLASS_ID"
        const val EXTRA_CLASS_NAME = "EXTRA_CLASS_NAME"
    }
}
