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
import com.nthb.kibooz.data.remote.VideoList
import com.nthb.kibooz.databinding.ActivityVideoPembelajaranBinding
import com.nthb.kibooz.util.InsetsUtil
import kotlinx.coroutines.launch

class VideoPembelajaranActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVideoPembelajaranBinding
    private var activeClassId: String? = null
    private val dynamicVideoCards = mutableListOf<Pair<View, String>>()

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

        adaptFallbackVideos(namaKelas)
        setupListeners(namaKelas)
        resolveClassAndLoadVideos()
    }

    private fun adaptFallbackVideos(namaKelas: String) {
        when {
            namaKelas.contains("Seni", ignoreCase = true) -> {
                binding.tvTitleVideo1.text = "Kreasi Melipat & Menggunting Kertas"
                binding.tvTitleVideo2.text = "Mengenal Warna Primer & Sekunder"
                binding.tvTitleVideo3.text = "Karya Daur Ulang Bersama Kibby"
            }
            namaKelas.contains("Logika", ignoreCase = true) || namaKelas.contains("Berpikir", ignoreCase = true) -> {
                binding.tvTitleVideo1.text = "Belajar Berhitung Seru Bersama Kibby"
                binding.tvTitleVideo2.text = "Mengenal Pola & Bentuk Geometri"
                binding.tvTitleVideo3.text = "Teka-Teki Logika Anak Ceria"
            }
            namaKelas.contains("Motorik", ignoreCase = true) || namaKelas.contains("Fisik", ignoreCase = true) -> {
                binding.tvTitleVideo1.text = "Senam Ceria Anak Hebat"
                binding.tvTitleVideo2.text = "Gerakan Tangkas & Keseimbangan Tubuh"
                binding.tvTitleVideo3.text = "Permainan Melompat & Melempar Bola"
            }
            namaKelas.contains("Agama", ignoreCase = true) || namaKelas.contains("Moral", ignoreCase = true) -> {
                binding.tvTitleVideo1.text = "Doa Harian & Adab Sebelum Makan"
                binding.tvTitleVideo2.text = "Kisah Teladan Kejujuran & Kasih Sayang"
                binding.tvTitleVideo3.text = "Menjaga Kebersihan Sebagian dari Iman"
            }
            namaKelas.contains("Sosial", ignoreCase = true) || namaKelas.contains("Emosional", ignoreCase = true) -> {
                binding.tvTitleVideo1.text = "Mengenal Emosi & Ekspresi Wajah"
                binding.tvTitleVideo2.text = "Belajar Berbagi & Bekerja Sama"
                binding.tvTitleVideo3.text = "Mengucap Maaf & Terima Kasih"
            }
            else -> {
                binding.tvTitleVideo1.text = "Mengenal Huruf dan Suara"
                binding.tvTitleVideo2.text = "Mengenal Nama-Nama Benda di Sekitar"
                binding.tvTitleVideo3.text = "Ekspresi & Bercerita Bersama Kibby"
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

            val classId = activeClassId ?: return@launch
            val result = videoRepo.videos(classId)
            if (result is ApiResult.Success) {
                val list = result.data
                if (list.videos.isNotEmpty()) {
                    binding.cardVideo1.visibility = View.GONE
                    binding.cardVideo2.visibility = View.GONE
                    binding.cardVideo3.visibility = View.GONE
                    list.videos.forEach { video ->
                        renderVideoCard(video)
                    }
                }
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
        }

        val container = LinearLayout(this).apply {
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
            text = video.description ?: "Video pembelajaran materi kelas"
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
            text = "▶ Putar Video"
            setTextColor(Color.parseColor("#6366F1"))
            textSize = 14f
            typeface = balooFont
            paint.isFakeBoldText = true
            setOnClickListener {
                try {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(video.videoUrl))
                    startActivity(browserIntent)
                } catch (e: Exception) {
                    Toast.makeText(this@VideoPembelajaranActivity, "Membuka video: ${video.title}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val shareBtn = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams((32 * density).toInt(), (32 * density).toInt())
            setImageResource(R.drawable.ic_share)
            setColorFilter(Color.parseColor("#6366F1"))
            setOnClickListener { shareVideo(video.title) }
        }

        val spacer = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
        }

        actionRow.addView(playBtn)
        actionRow.addView(spacer)
        actionRow.addView(shareBtn)

        container.addView(titleTv)
        container.addView(descTv)
        container.addView(actionRow)
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
            shareVideo(binding.tvTitleVideo1.text.toString())
        }

        binding.btnShareVideo2.setOnClickListener {
            shareVideo(binding.tvTitleVideo2.text.toString())
        }

        binding.btnShareVideo3.setOnClickListener {
            shareVideo(binding.tvTitleVideo3.text.toString())
        }

        binding.cardVideo1.setOnClickListener {
            Toast.makeText(this, "Memutar Video: ${binding.tvTitleVideo1.text}", Toast.LENGTH_SHORT).show()
        }

        binding.cardVideo2.setOnClickListener {
            Toast.makeText(this, "Memutar Video: ${binding.tvTitleVideo2.text}", Toast.LENGTH_SHORT).show()
        }

        binding.cardVideo3.setOnClickListener {
            Toast.makeText(this, "Memutar Video: ${binding.tvTitleVideo3.text}", Toast.LENGTH_SHORT).show()
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

    private fun shareVideo(title: String) {
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, "Tonton video pembelajaran Kibooz: $title")
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
