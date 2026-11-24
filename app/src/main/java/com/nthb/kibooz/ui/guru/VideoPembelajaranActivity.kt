package com.nthb.kibooz.ui.guru

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
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
import kotlinx.coroutines.launch

class VideoPembelajaranActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVideoPembelajaranBinding
    private var activeClassId: String? = null
    private val dynamicVideoCards = mutableListOf<Pair<View, String>>()

    private var videoUrl1: String = "https://www.youtube.com/watch?v=e23kDLhj_qA"
    private var videoUrl2: String = "https://www.youtube.com/watch?v=VoukA9eehoQ"
    private var videoUrl3: String = "https://www.youtube.com/watch?v=wAjP-tqKJi0"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoPembelajaranBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetsUtil.applySystemBarInsets(
            root = binding.clRootVideo,
            headerView = binding.clTopBar,
            fab = binding.fabTambahVideo
        )

        val namaKelas = intent.getStringExtra("NAMA_KELAS") ?: "Bahasa & Komunikasi"
        activeClassId = intent.getStringExtra("CLASS_ID")
        binding.tvKelasTitle.text = namaKelas

        // Periksa peran pengguna untuk Role-Gating: Wali murid hanya menonton, Guru dapat menambah video
        val sessionUser = appContainer.authRepository.session.value?.user
        val isTeacher = sessionUser?.role == Role.GURU
        binding.fabTambahVideo.visibility = if (isTeacher) View.VISIBLE else View.GONE

        // Sembunyikan kartu fallback dari awal agar tidak ada kedipan visual (flicker)
        binding.cardVideo1.visibility = View.GONE
        binding.cardVideo2.visibility = View.GONE
        binding.cardVideo3.visibility = View.GONE

        setupListeners(namaKelas)
        resolveClassAndLoadVideos(namaKelas)
    }

    private fun adaptFallbackVideos(namaKelas: String) {
        when {
            namaKelas.contains("Seni", ignoreCase = true) -> {
                binding.tvTitleVideo1.text = "Kreasi Melipat & Menggunting Kertas"
                videoUrl1 = "https://www.youtube.com/watch?v=VoukA9eehoQ"
                binding.tvTitleVideo2.text = "Mengenal Warna Primer & Sekunder"
                videoUrl2 = "https://www.youtube.com/watch?v=e23kDLhj_qA"
                binding.tvTitleVideo3.text = "Karya Daur Ulang Bersama Kibby"
                videoUrl3 = "https://www.youtube.com/watch?v=wAjP-tqKJi0"
            }
            namaKelas.contains("Logika", ignoreCase = true) || namaKelas.contains("Berpikir", ignoreCase = true) -> {
                binding.tvTitleVideo1.text = "Belajar Berhitung Seru Bersama Kibby"
                videoUrl1 = "https://www.youtube.com/watch?v=VoukA9eehoQ"
                binding.tvTitleVideo2.text = "Mengenal Pola & Bentuk Geometri"
                videoUrl2 = "https://www.youtube.com/watch?v=e23kDLhj_qA"
                binding.tvTitleVideo3.text = "Teka-Teki Logika Anak Ceria"
                videoUrl3 = "https://www.youtube.com/watch?v=TKOVGPjaTQc"
            }
            namaKelas.contains("Motorik", ignoreCase = true) || namaKelas.contains("Fisik", ignoreCase = true) -> {
                binding.tvTitleVideo1.text = "Senam Ceria Anak Hebat"
                videoUrl1 = "https://www.youtube.com/watch?v=noh1FVOlBT8"
                binding.tvTitleVideo2.text = "Gerakan Tangkas & Keseimbangan Tubuh"
                videoUrl2 = "https://www.youtube.com/watch?v=VoukA9eehoQ"
                binding.tvTitleVideo3.text = "Permainan Melompat & Melempar Bola"
                videoUrl3 = "https://www.youtube.com/watch?v=wAjP-tqKJi0"
            }
            namaKelas.contains("Agama", ignoreCase = true) || namaKelas.contains("Moral", ignoreCase = true) -> {
                binding.tvTitleVideo1.text = "Doa Harian & Adab Sebelum Makan"
                videoUrl1 = "https://www.youtube.com/watch?v=wAjP-tqKJi0"
                binding.tvTitleVideo2.text = "Kisah Teladan Kejujuran & Kasih Sayang"
                videoUrl2 = "https://www.youtube.com/watch?v=Xz8qBhIj9Nw"
                binding.tvTitleVideo3.text = "Menjaga Kebersihan Sebagian dari Iman"
                videoUrl3 = "https://www.youtube.com/watch?v=TKOVGPjaTQc"
            }
            namaKelas.contains("Sosial", ignoreCase = true) || namaKelas.contains("Emotional", ignoreCase = true) || namaKelas.contains("Emosional", ignoreCase = true) -> {
                binding.tvTitleVideo1.text = "Mengenal Emosi & Ekspresi Wajah"
                videoUrl1 = "https://www.youtube.com/watch?v=wAjP-tqKJi0"
                binding.tvTitleVideo2.text = "Belajar Berbagi & Bekerja Sama"
                videoUrl2 = "https://www.youtube.com/watch?v=Xz8qBhIj9Nw"
                binding.tvTitleVideo3.text = "Mengucap Maaf & Terima Kasih"
                videoUrl3 = "https://www.youtube.com/watch?v=TKOVGPjaTQc"
            }
            else -> {
                binding.tvTitleVideo1.text = "Mengenal Huruf dan Suara"
                videoUrl1 = "https://www.youtube.com/watch?v=e23kDLhj_qA"
                binding.tvTitleVideo2.text = "Mengenal Nama-Nama Benda di Sekitar"
                videoUrl2 = "https://www.youtube.com/watch?v=VoukA9eehoQ"
                binding.tvTitleVideo3.text = "Ekspresi & Bercerita Bersama Kibby"
                videoUrl3 = "https://www.youtube.com/watch?v=Xz8qBhIj9Nw"
            }
        }

        val cal = java.util.Calendar.getInstance()
        val fmt = java.text.SimpleDateFormat("d MMMM yyyy", java.util.Locale("id", "ID"))
        binding.tvDateVideo1.text = "Diunggah hari ini • ${fmt.format(cal.time)}"
        cal.add(java.util.Calendar.DAY_OF_YEAR, -2)
        binding.tvDateVideo2.text = "Diunggah 2 hari lalu • ${fmt.format(cal.time)}"
        cal.add(java.util.Calendar.DAY_OF_YEAR, -3)
        binding.tvDateVideo3.text = "Diunggah 5 hari lalu • ${fmt.format(cal.time)}"
    }

    private fun resolveClassAndLoadVideos(namaKelas: String) {
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
            if (result is ApiResult.Success && result.data.videos.isNotEmpty()) {
                binding.cardVideo1.visibility = View.GONE
                binding.cardVideo2.visibility = View.GONE
                binding.cardVideo3.visibility = View.GONE
                result.data.videos.forEach { video ->
                    renderVideoCard(video)
                }
            } else {
                // Tampilkan fallback hanya saat data backend benar-benar kosong atau tidak tersedia
                adaptFallbackVideos(namaKelas)
                binding.cardVideo1.visibility = View.VISIBLE
                binding.cardVideo2.visibility = View.VISIBLE
                binding.cardVideo3.visibility = View.VISIBLE
            }
        }
    }

    private fun renderVideoCard(video: VideoItem) {
        val density = resources.displayMetrics.density
        val balooFont = ResourcesCompat.getFont(this, R.font.baloo2)

        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (16 * density).toInt()
            }
            radius = 18 * density
            cardElevation = 2 * density
            setCardBackgroundColor(Color.WHITE)
            strokeColor = Color.parseColor("#ECEAF5")
            strokeWidth = (1 * density).toInt()
            isClickable = true
            isFocusable = true
            setOnClickListener {
                playVideo(video.videoUrl, video.title)
            }
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Thumbnail dengan tampilan kaya
        val thumbnail = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (170 * density).toInt()
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.parseColor("#DEF2EE"))
            setImageResource(R.drawable.img_onboarding_1)
            contentDescription = "Thumbnail ${video.title}"
        }

        val detailsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((14 * density).toInt(), (14 * density).toInt(), (14 * density).toInt(), (14 * density).toInt())
        }

        val titleTv = TextView(this).apply {
            text = video.title
            setTextColor(Color.parseColor("#221C38"))
            textSize = 16f
            typeface = balooFont
            paint.isFakeBoldText = true
        }

        val descTv = TextView(this).apply {
            text = video.description ?: "Video materi pembelajaran si kecil"
            setTextColor(Color.parseColor("#75718E"))
            textSize = 12f
            typeface = balooFont
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = (4 * density).toInt()
            layoutParams = lp
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
            setTextColor(Color.parseColor("#6366F1"))
            textSize = 14f
            typeface = balooFont
            paint.isFakeBoldText = true
            setOnClickListener {
                playVideo(video.videoUrl, video.title)
            }
        }

        val shareBtn = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams((32 * density).toInt(), (32 * density).toInt())
            setImageResource(R.drawable.ic_share)
            setColorFilter(Color.parseColor("#6366F1"))
            setOnClickListener { shareVideo(video.title, video.videoUrl) }
        }

        val spacer = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
        }

        actionRow.addView(playBtn)
        actionRow.addView(spacer)
        actionRow.addView(shareBtn)

        detailsContainer.addView(titleTv)
        detailsContainer.addView(descTv)
        detailsContainer.addView(actionRow)

        container.addView(thumbnail)
        container.addView(detailsContainer)
        card.addView(container)

        binding.llVideoContainer.addView(card, 2)
        dynamicVideoCards.add(card to video.title)
    }

    private fun setupListeners(namaKelas: String) {
        binding.ivBack.setOnClickListener {
            finish()
        }

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

        binding.fabTambahVideo.setOnClickListener {
            showTambahVideoDialog()
        }

        binding.btnShareVideo1.setOnClickListener {
            shareVideo(binding.tvTitleVideo1.text.toString(), videoUrl1)
        }

        binding.btnShareVideo2.setOnClickListener {
            shareVideo(binding.tvTitleVideo2.text.toString(), videoUrl2)
        }

        binding.btnShareVideo3.setOnClickListener {
            shareVideo(binding.tvTitleVideo3.text.toString(), videoUrl3)
        }

        // Klik pada kartu langsung memutar video edukasi riil via Intent
        binding.cardVideo1.setOnClickListener {
            playVideo(videoUrl1, binding.tvTitleVideo1.text.toString())
        }

        binding.cardVideo2.setOnClickListener {
            playVideo(videoUrl2, binding.tvTitleVideo2.text.toString())
        }

        binding.cardVideo3.setOnClickListener {
            playVideo(videoUrl3, binding.tvTitleVideo3.text.toString())
        }
    }

    private fun playVideo(url: String, title: String) {
        try {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(browserIntent)
        } catch (e: Exception) {
            Toast.makeText(this, "Membuka video: $title", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showTambahVideoDialog() {
        val density = resources.displayMetrics.density
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((24 * density).toInt(), (16 * density).toInt(), (24 * density).toInt(), 0)
        }

        val etTitle = EditText(this).apply {
            hint = "Judul Video (cth. Belajar Berhitung)"
            setSingleLine()
        }
        val etUrl = EditText(this).apply {
            hint = "Alamat URL Video (https://...)"
            setSingleLine()
        }
        val etDesc = EditText(this).apply {
            hint = "Deskripsi Singkat"
            setSingleLine()
        }

        container.addView(etTitle)
        container.addView(etUrl)
        container.addView(etDesc)

        AlertDialog.Builder(this)
            .setTitle("Tambah Video Pembelajaran")
            .setView(container)
            .setPositiveButton("Unggah") { _, _ ->
                val title = etTitle.text.toString().trim()
                val url = etUrl.text.toString().trim()
                val desc = etDesc.text.toString().trim()

                if (title.isBlank() || url.isBlank()) {
                    Toast.makeText(this, "Judul dan URL video wajib diisi", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val classId = activeClassId ?: "078c0dcb-39d5-4e16-b91c-5a5dd116897b"

                lifecycleScope.launch {
                    when (val result = appContainer.videoRepository.createVideo(
                        classId = classId,
                        title = title,
                        videoUrl = url,
                        description = desc.ifBlank { null }
                    )) {
                        is ApiResult.Success<*> -> {
                            Toast.makeText(this@VideoPembelajaranActivity, "Video berhasil ditambahkan!", Toast.LENGTH_SHORT).show()
                            binding.cardVideo1.visibility = View.GONE
                            binding.cardVideo2.visibility = View.GONE
                            binding.cardVideo3.visibility = View.GONE
                            renderVideoCard(result.data as VideoItem)
                        }
                        is ApiResult.Failure -> {
                            Toast.makeText(this@VideoPembelajaranActivity, result.message, Toast.LENGTH_SHORT).show()
                        }
                        is ApiResult.NetworkError -> {
                            Toast.makeText(this@VideoPembelajaranActivity, "Koneksi internet bermasalah", Toast.LENGTH_SHORT).show()
                        }
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
        val fallbackCards = listOf(
            binding.cardVideo1 to binding.tvTitleVideo1.text.toString(),
            binding.cardVideo2 to binding.tvTitleVideo2.text.toString(),
            binding.cardVideo3 to binding.tvTitleVideo3.text.toString()
        )

        val targetCards = if (dynamicVideoCards.isNotEmpty()) dynamicVideoCards else fallbackCards
        targetCards.forEach { (card, title) ->
            if (query.isEmpty() || title.contains(query, ignoreCase = true)) {
                card.visibility = View.VISIBLE
            } else {
                card.visibility = View.GONE
            }
        }
    }
}

