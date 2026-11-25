package com.nthb.kibooz.ui.guru

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
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
import com.nthb.kibooz.data.remote.VideoItem
import com.nthb.kibooz.data.session.Role
import com.nthb.kibooz.databinding.ActivityVideoPembelajaranBinding
import com.nthb.kibooz.util.InsetsUtil
import com.nthb.kibooz.util.TimeFormat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Layar daftar video pembelajaran kelas. Guru dapat menambah, mengubah metadata, dan menghapus video. */
class VideoPembelajaranActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVideoPembelajaranBinding
    private var activeClassId: String? = null
    private var currentSubjectName: String = "Bahasa & Komunikasi"
    private var isTeacher: Boolean = false
    private var serverFetchSucceeded: Boolean = false
    private var seedingInProgress: Boolean = false

    private val loadedVideos = mutableListOf<VideoItem>()
    private val dynamicVideoCards = mutableListOf<Pair<View, VideoItem>>()

    private var videoUrl1: String = "https://www.youtube.com/watch?v=e23kDLhj_qA"
    private var videoUrl2: String = "https://www.youtube.com/watch?v=VoukA9eehoQ"
    private var videoUrl3: String = "https://www.youtube.com/watch?v=Xz8qBhIj9Nw"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoPembelajaranBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetsUtil.applySystemBarInsets(
            root = binding.clRootVideo,
            headerView = binding.clTopBar,
            fab = binding.fabTambahVideo
        )

        currentSubjectName = intent.getStringExtra("NAMA_KELAS") ?: "Bahasa & Komunikasi"
        activeClassId = intent.getStringExtra("CLASS_ID")
        binding.tvKelasTitle.text = currentSubjectName

        // Periksa peran pengguna untuk Role-Gating: Wali murid hanya menonton, Guru dapat mengelola video
        val sessionUser = appContainer.authRepository.session.value?.user
        isTeacher = sessionUser?.role == Role.GURU
        binding.fabTambahVideo.visibility = if (isTeacher) View.VISIBLE else View.GONE

        // Sembunyikan kartu fallback dan empty-state dari awal agar tidak berkedip
        binding.cardEmptyVideos.visibility = View.GONE
        binding.cardVideo1.visibility = View.GONE
        binding.cardVideo2.visibility = View.GONE
        binding.cardVideo3.visibility = View.GONE

        setupListeners(currentSubjectName)
        resolveClassAndLoadVideos()
    }

    private fun adaptFallbackVideos(namaKelas: String) {
        val specs = StarterVideos.forSubject(namaKelas)
        if (specs.size >= 3) {
            binding.tvTitleVideo1.text = specs[0].title
            videoUrl1 = specs[0].videoUrl
            binding.tvTitleVideo2.text = specs[1].title
            videoUrl2 = specs[1].videoUrl
            binding.tvTitleVideo3.text = specs[2].title
            videoUrl3 = specs[2].videoUrl
        }

        val cal = java.util.Calendar.getInstance()
        val fmt = java.text.SimpleDateFormat("d MMMM yyyy", java.util.Locale("id", "ID"))
        binding.tvDateVideo1.text = "Diunggah hari ini • ${fmt.format(cal.time)}"
        cal.add(java.util.Calendar.DAY_OF_YEAR, -2)
        binding.tvDateVideo2.text = "Diunggah 2 hari lalu • ${fmt.format(cal.time)}"
        cal.add(java.util.Calendar.DAY_OF_YEAR, -3)
        binding.tvDateVideo3.text = "Diunggah 5 hari lalu • ${fmt.format(cal.time)}"
    }

    private fun resolveClassAndLoadVideos() {
        val videoRepo = appContainer.videoRepository
        val classRepo = appContainer.classRepository

        lifecycleScope.launch {
            if (activeClassId.isNullOrBlank()) {
                val childrenRes = classRepo.children()
                if (childrenRes is ApiResult.Success) {
                    activeClassId = childrenRes.data.firstOrNull()?.classId
                }

                if (activeClassId.isNullOrBlank()) {
                    val teacherClasses = classRepo.teacherClasses()
                    if (teacherClasses is ApiResult.Success) {
                        activeClassId = teacherClasses.data.firstOrNull()?.id
                    }
                }
            }

            val classId = activeClassId
            val result = if (!classId.isNullOrBlank()) videoRepo.videos(classId) else null
            loadedVideos.clear()
            if (result is ApiResult.Success) {
                serverFetchSucceeded = true
                if (result.data.videos.isNotEmpty()) {
                    loadedVideos.addAll(result.data.videos)
                } else if (isTeacher && !classId.isNullOrBlank() && shouldAutoSeedClass(classId)) {
                    // Pertama kali Guru membuka kelas kosong: jadikan 3 video bawaan data resmi di server
                    seedStarterVideosToServer(classId, silent = true)
                    return@launch
                }
            } else {
                serverFetchSucceeded = false
            }
            renderVideoList()
        }
    }

    private fun seedPrefs() = getSharedPreferences(PREFS_SEED_NAME, Context.MODE_PRIVATE)

    private fun shouldAutoSeedClass(classId: String): Boolean =
        !seedPrefs().getBoolean(SEED_KEY_PREFIX + classId, false)

    private fun markClassSeeded(classId: String) {
        seedPrefs().edit().putBoolean(SEED_KEY_PREFIX + classId, true).apply()
    }

    /**
     * Menyimpan 3 video bawaan ke server via `POST /classes/{classId}/videos` agar tersimpan persisten,
     * dapat diedit maupun dihapus Guru, dan tetap ada saat Guru menambahkan video baru.
     */
    private fun seedStarterVideosToServer(classId: String, silent: Boolean) {
        if (seedingInProgress) return
        seedingInProgress = true
        binding.btnSeedStarterVideos.isEnabled = false
        binding.btnSeedStarterVideos.text = "Menyimpan ke server..."

        lifecycleScope.launch {
            val specs = StarterVideos.forSubject(currentSubjectName)
            var savedAny = false
            // Urutan terbalik dengan jeda tipis agar urutan `created_at DESC` server tampil #1, #2, #3
            specs.reversed().forEachIndexed { idx, spec ->
                if (idx > 0) delay(150L)
                val res = appContainer.videoRepository.createVideo(
                    classId = classId,
                    title = spec.title,
                    videoUrl = spec.videoUrl,
                    description = spec.description
                )
                if (res is ApiResult.Success) savedAny = true
            }

            if (savedAny) {
                markClassSeeded(classId)
                val refreshed = appContainer.videoRepository.videos(classId)
                loadedVideos.clear()
                if (refreshed is ApiResult.Success) {
                    loadedVideos.addAll(refreshed.data.videos)
                }
                if (!silent) {
                    Toast.makeText(
                        this@VideoPembelajaranActivity,
                        "3 video bawaan berhasil disimpan ke kelas!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else if (!silent) {
                Toast.makeText(
                    this@VideoPembelajaranActivity,
                    "Gagal menyimpan video bawaan ke server",
                    Toast.LENGTH_SHORT
                ).show()
            }

            binding.btnSeedStarterVideos.isEnabled = true
            binding.btnSeedStarterVideos.text = "Muat 3 Video Bawaan ke Kelas"
            seedingInProgress = false
            renderVideoList()
        }
    }

    private fun renderVideoList() {
        dynamicVideoCards.forEach { (view, _) -> binding.llVideoContainer.removeView(view) }
        dynamicVideoCards.clear()

        when {
            loadedVideos.isNotEmpty() -> {
                binding.cardEmptyVideos.visibility = View.GONE
                binding.cardVideo1.visibility = View.GONE
                binding.cardVideo2.visibility = View.GONE
                binding.cardVideo3.visibility = View.GONE
                loadedVideos.forEachIndexed { index, video ->
                    val card = buildVideoCard(video)
                    binding.llVideoContainer.addView(card, DYNAMIC_INSERT_INDEX + index)
                    dynamicVideoCards.add(card to video)
                }
            }
            isTeacher && serverFetchSucceeded -> {
                // Guru yang menghapus seluruh video melihat status kosong dengan opsi muat ulang bawaan
                binding.cardVideo1.visibility = View.GONE
                binding.cardVideo2.visibility = View.GONE
                binding.cardVideo3.visibility = View.GONE
                binding.cardEmptyVideos.visibility = View.VISIBLE
            }
            else -> {
                // Wali di kelas kosong atau mode offline tetap mendapat referensi materi bawaan
                binding.cardEmptyVideos.visibility = View.GONE
                adaptFallbackVideos(currentSubjectName)
                binding.cardVideo1.visibility = View.VISIBLE
                binding.cardVideo2.visibility = View.VISIBLE
                binding.cardVideo3.visibility = View.VISIBLE
            }
        }

        filterVideos(binding.etSearchVideo.text?.toString().orEmpty().trim())
    }

    private fun buildVideoCard(video: VideoItem): MaterialCardView {
        val density = resources.displayMetrics.density
        val balooFont = ResourcesCompat.getFont(this, R.font.baloo2)
        val primaryPurple = ContextCompat.getColor(this, R.color.kibooz_primary_purple)

        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (14 * density).toInt()
            }
            radius = 18 * density
            cardElevation = 2 * density
            setCardBackgroundColor(Color.WHITE)
            strokeColor = Color.parseColor("#ECEAF5")
            strokeWidth = (1 * density).toInt()
            isClickable = true
            isFocusable = true
            setOnClickListener { playVideo(video.videoUrl, video.title) }
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * density).toInt()
            setPadding(pad, pad, pad, pad)
        }

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val iconBadge = FrameLayout(this).apply {
            val size = (38 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size)
            setBackgroundResource(R.drawable.bg_tag_lavender)
        }
        val videoIcon = ImageView(this).apply {
            val iconSize = (20 * density).toInt()
            layoutParams = FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER)
            setImageResource(R.drawable.ic_video)
            setColorFilter(primaryPurple)
        }
        iconBadge.addView(videoIcon)

        val titleTv = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = (12 * density).toInt()
            }
            text = video.title
            setTextColor(Color.parseColor("#221C38"))
            textSize = 16f
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            typeface = balooFont
            paint.isFakeBoldText = true
        }

        headerRow.addView(iconBadge)
        headerRow.addView(titleTv)
        container.addView(headerRow)

        val description = video.description?.trim()?.takeIf { it.isNotEmpty() }
            ?: "Video materi pembelajaran si kecil"
        val descTv = TextView(this).apply {
            text = description
            setTextColor(Color.parseColor("#75718E"))
            textSize = 13f
            typeface = balooFont
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (8 * density).toInt()
            }
        }
        container.addView(descTv)

        TimeFormat.localDate(video.createdAt)?.let { formattedDate ->
            val dateRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = (6 * density).toInt()
                }
            }
            val calIcon = ImageView(this).apply {
                val size = (14 * density).toInt()
                layoutParams = LinearLayout.LayoutParams(size, size)
                setImageResource(R.drawable.ic_calendar)
                setColorFilter(Color.parseColor("#75718E"))
            }
            val dateTv = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = (6 * density).toInt()
                }
                text = "Diunggah $formattedDate"
                setTextColor(Color.parseColor("#75718E"))
                textSize = 12f
                typeface = balooFont
            }
            dateRow.addView(calIcon)
            dateRow.addView(dateTv)
            container.addView(dateRow)
        }

        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (12 * density).toInt()
            }
        }

        val playBtn = TextView(this).apply {
            text = "Putar Video"
            setTextColor(primaryPurple)
            textSize = 13f
            typeface = balooFont
            paint.isFakeBoldText = true
            setOnClickListener { playVideo(video.videoUrl, video.title) }
        }

        val spacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
        }

        actionRow.addView(playBtn)
        actionRow.addView(spacer)

        if (isTeacher) {
            val editChip = buildActionChip(
                label = "Edit",
                textColor = primaryPurple,
                bgColor = Color.parseColor("#F1EEFD"),
                font = balooFont,
                density = density
            ) { showVideoFormDialog(video) }
            val deleteChip = buildActionChip(
                label = "Hapus",
                textColor = Color.parseColor("#DC2626"),
                bgColor = Color.parseColor("#FEE2E2"),
                font = balooFont,
                density = density
            ) { confirmDeleteVideo(video) }
            actionRow.addView(editChip)
            actionRow.addView(deleteChip)
        }

        val shareBtn = ImageView(this).apply {
            val size = (28 * density).toInt()
            val pad = (4 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size)
            setPadding(pad, pad, pad, pad)
            setImageResource(R.drawable.ic_share)
            setColorFilter(primaryPurple)
            contentDescription = "Bagikan Video"
            setOnClickListener { shareVideo(video.title, video.videoUrl) }
        }
        actionRow.addView(shareBtn)

        container.addView(actionRow)
        card.addView(container)
        return card
    }

    private fun buildActionChip(
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
        val padH = (10 * density).toInt()
        val padV = (4 * density).toInt()
        setPadding(padH, padV, padH, padV)
        background = GradientDrawable().apply {
            cornerRadius = 12 * density
            setColor(bgColor)
        }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            marginEnd = (8 * density).toInt()
        }
        setOnClickListener { onClick() }
    }

    private fun setupListeners(namaKelas: String) {
        binding.ivBack.setOnClickListener { finish() }

        binding.etSearchVideo.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterVideos(s?.toString().orEmpty().trim())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.ivMenu.setOnClickListener {
            Toast.makeText(this, "Opsi Video Kelas $namaKelas", Toast.LENGTH_SHORT).show()
        }

        binding.fabTambahVideo.setOnClickListener { showVideoFormDialog() }

        binding.btnSeedStarterVideos.setOnClickListener {
            val classId = activeClassId
            if (classId.isNullOrBlank()) {
                Toast.makeText(this, "Kelas belum tersedia, muat ulang halaman", Toast.LENGTH_SHORT).show()
            } else {
                seedStarterVideosToServer(classId, silent = false)
            }
        }

        binding.btnShareVideo1.setOnClickListener { shareVideo(binding.tvTitleVideo1.text.toString(), videoUrl1) }
        binding.btnShareVideo2.setOnClickListener { shareVideo(binding.tvTitleVideo2.text.toString(), videoUrl2) }
        binding.btnShareVideo3.setOnClickListener { shareVideo(binding.tvTitleVideo3.text.toString(), videoUrl3) }

        binding.cardVideo1.setOnClickListener { playVideo(videoUrl1, binding.tvTitleVideo1.text.toString()) }
        binding.cardVideo2.setOnClickListener { playVideo(videoUrl2, binding.tvTitleVideo2.text.toString()) }
        binding.cardVideo3.setOnClickListener { playVideo(videoUrl3, binding.tvTitleVideo3.text.toString()) }
    }

    private fun playVideo(url: String, title: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            Toast.makeText(this, "Membuka video: $title", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showVideoFormDialog(existing: VideoItem? = null) {
        val classId = activeClassId
        if (classId.isNullOrBlank()) {
            Toast.makeText(this, "Kelas belum tersedia, muat ulang halaman", Toast.LENGTH_SHORT).show()
            return
        }

        val density = resources.displayMetrics.density
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((24 * density).toInt(), (16 * density).toInt(), (24 * density).toInt(), 0)
        }

        val etTitle = EditText(this).apply {
            hint = "Judul Video (cth. Belajar Berhitung)"
            setSingleLine()
            setText(existing?.title.orEmpty())
        }
        val etUrl = EditText(this).apply {
            hint = "Alamat URL Video (https://...)"
            setSingleLine()
            setText(existing?.videoUrl.orEmpty())
            if (existing != null) {
                isEnabled = false
                alpha = 0.65f
            }
        }
        val etDesc = EditText(this).apply {
            hint = if (existing != null) "Deskripsi Singkat (kosongkan untuk hapus)" else "Deskripsi Singkat (opsional)"
            setSingleLine()
            setText(existing?.description.orEmpty())
        }

        container.addView(etTitle)
        container.addView(etUrl)
        if (existing != null) {
            val urlNote = TextView(this).apply {
                text = "URL video tidak dapat diubah setelah ditambahkan"
                textSize = 11f
                setTextColor(Color.parseColor("#75718E"))
            }
            container.addView(urlNote)
        }
        container.addView(etDesc)

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (existing == null) "Tambah Video Pembelajaran" else "Edit Video Pembelajaran")
            .setView(container)
            .setPositiveButton(if (existing == null) "Unggah" else "Simpan", null)
            .setNegativeButton("Batal", null)
            .create()

        dialog.setOnShowListener {
            val submitBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            submitBtn.setOnClickListener {
                val title = etTitle.text.toString().trim()
                val url = etUrl.text.toString().trim()
                val desc = etDesc.text.toString().trim()

                if (title.isBlank()) {
                    Toast.makeText(this, "Judul video wajib diisi", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (existing == null) {
                    if (url.isBlank() || !url.startsWith("https://", ignoreCase = true)) {
                        Toast.makeText(this, "URL video wajib diawali https://", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    submitBtn.isEnabled = false
                    lifecycleScope.launch {
                        val result = appContainer.videoRepository.createVideo(
                            classId = classId,
                            title = title,
                            videoUrl = url,
                            description = desc.ifBlank { null }
                        )
                        submitBtn.isEnabled = true
                        handleVideoMutationResult(result, "Video berhasil ditambahkan!", dialog) { saved ->
                            markClassSeeded(classId)
                            loadedVideos.add(0, saved)
                        }
                    }
                } else {
                    submitBtn.isEnabled = false
                    lifecycleScope.launch {
                        val result = appContainer.videoRepository.updateVideo(
                            classId = classId,
                            videoId = existing.id,
                            title = title,
                            description = desc
                        )
                        submitBtn.isEnabled = true
                        handleVideoMutationResult(result, "Video berhasil diperbarui!", dialog) { updated ->
                            val idx = loadedVideos.indexOfFirst { it.id == updated.id }
                            if (idx >= 0) loadedVideos[idx] = updated else loadedVideos.add(0, updated)
                        }
                    }
                }
            }
        }
        dialog.show()
    }

    private fun handleVideoMutationResult(
        result: ApiResult<VideoItem>,
        successMessage: String,
        dialog: AlertDialog,
        onSuccess: (VideoItem) -> Unit
    ) {
        when (result) {
            is ApiResult.Success -> {
                onSuccess(result.data)
                renderVideoList()
                Toast.makeText(this, successMessage, Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            is ApiResult.Failure -> Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
            is ApiResult.NetworkError -> Toast.makeText(this, "Koneksi internet bermasalah", Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmDeleteVideo(video: VideoItem) {
        val classId = activeClassId ?: return
        AlertDialog.Builder(this)
            .setTitle("Hapus Video?")
            .setMessage("Video \"${video.title}\" akan dihapus dari daftar kelas ini.")
            .setPositiveButton("Hapus") { _, _ ->
                lifecycleScope.launch {
                    when (val result = appContainer.videoRepository.deleteVideo(classId, video.id)) {
                        is ApiResult.Success -> {
                            loadedVideos.removeAll { it.id == video.id }
                            renderVideoList()
                            Toast.makeText(this@VideoPembelajaranActivity, "Video berhasil dihapus", Toast.LENGTH_SHORT).show()
                        }
                        is ApiResult.Failure -> Toast.makeText(this@VideoPembelajaranActivity, result.message, Toast.LENGTH_SHORT).show()
                        is ApiResult.NetworkError -> Toast.makeText(this@VideoPembelajaranActivity, "Koneksi internet bermasalah", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun shareVideo(title: String, url: String? = null) {
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            val text = if (url != null) "Tonton video pembelajaran Kibooz: $title\n$url" else "Tonton video pembelajaran Kibooz: $title"
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
        }
        startActivity(Intent.createChooser(shareIntent, "Bagikan Video Pembelajaran"))
    }

    private fun filterVideos(query: String) {
        if (dynamicVideoCards.isNotEmpty()) {
            dynamicVideoCards.forEach { (card, video) ->
                val matches = query.isEmpty() ||
                    video.title.contains(query, ignoreCase = true) ||
                    video.description.orEmpty().contains(query, ignoreCase = true)
                card.visibility = if (matches) View.VISIBLE else View.GONE
            }
            return
        }

        if (isTeacher && serverFetchSucceeded) return

        val fallbackCards = listOf(
            binding.cardVideo1 to binding.tvTitleVideo1.text.toString(),
            binding.cardVideo2 to binding.tvTitleVideo2.text.toString(),
            binding.cardVideo3 to binding.tvTitleVideo3.text.toString()
        )
        fallbackCards.forEach { (card, title) ->
            val matches = query.isEmpty() || title.contains(query, ignoreCase = true)
            card.visibility = if (matches) View.VISIBLE else View.GONE
        }
    }

    private companion object {
        const val DYNAMIC_INSERT_INDEX = 1
        const val PREFS_SEED_NAME = "kibooz_video_starter_seed"
        const val SEED_KEY_PREFIX = "seeded_"
    }
}
