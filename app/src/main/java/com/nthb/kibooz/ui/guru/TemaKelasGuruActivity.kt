package com.nthb.kibooz.ui.guru

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.nthb.kibooz.databinding.ActivityTemaKelasGuruBinding
import com.nthb.kibooz.util.ClassCode
import com.nthb.kibooz.util.InsetsUtil

/**
 * Layar perantara untuk peran Guru setelah memilih sebuah kelas rombel dari RuangKelasGuruActivity.
 * Menampilkan 6 bidang pembelajaran tematik di dalam kelas tersebut sebelum masuk ke DetailKelasActivity.
 */
class TemaKelasGuruActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTemaKelasGuruBinding

    private var classId: String? = null
    private var joinCode: String = ""
    private var className: String = "Kelas"
    private var gradeLevel: String = "TK-A"
    private var totalStudents: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTemaKelasGuruBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetsUtil.applySystemBarInsets(
            root = binding.root,
            headerView = binding.clTopBar
        )

        classId = intent.getStringExtra("CLASS_ID")
        val rawCode = intent.getStringExtra("KODE_KELAS").orEmpty()
        joinCode = if (rawCode.isNotBlank()) rawCode else ClassCode.resolveJoinCode(classId)
        className = intent.getStringExtra("NAMA_KELAS") ?: "Kelas"
        gradeLevel = intent.getStringExtra("GRADE_LEVEL") ?: "TK-A"
        totalStudents = intent.getIntExtra("TOTAL_STUDENTS", 0)

        setupHeader()
        setupListeners()
    }

    private fun setupHeader() {
        binding.tvHeaderTitle.text = className
        binding.tvKodeKelasBadge.text = joinCode
        binding.tvSubtitleTema.text = "$gradeLevel • $totalStudents Siswa Terdaftar • 6 Tema Pembelajaran"
    }

    private fun setupListeners() {
        binding.ivBack.setOnClickListener {
            finish()
        }

        binding.btnKodeKelasPill.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Kode Kelas", joinCode))
            Toast.makeText(this, "Kode Kelas $joinCode berhasil disalin!", Toast.LENGTH_SHORT).show()
        }

        binding.etSearchTema.doAfterTextChanged { text ->
            filterSubjectCards(text?.toString().orEmpty().trim().lowercase())
        }

        binding.cardMapelSeni.setOnClickListener {
            openDetailKelas("SENI-A1", "Seni & Kreativitas")
        }
        binding.cardMapelBahasa.setOnClickListener {
            openDetailKelas("BHS-A1", "Bahasa & Komunikasi")
        }
        binding.cardMapelLogika.setOnClickListener {
            openDetailKelas("LOG-A1", "Berpikir & Logika")
        }
        binding.cardMapelMotorik.setOnClickListener {
            openDetailKelas("MOT-A1", "Belajar Fisik Motorik")
        }
        binding.cardMapelAgama.setOnClickListener {
            openDetailKelas("AGM-A1", "Nilai Agama & Moral")
        }
        binding.cardMapelSosial.setOnClickListener {
            openDetailKelas("SOS-A1", "Sosial & Emotional")
        }
    }

    private fun openDetailKelas(mapelCode: String, subjectName: String) {
        val intent = Intent(this, DetailKelasActivity::class.java).apply {
            putExtra("KODE_KELAS", joinCode)
            putExtra("MAPEL_CODE", mapelCode)
            putExtra("NAMA_KELAS", subjectName)
            putExtra("ROMBEL_NAME", className)
            putExtra("CLASS_ID", classId)
            putExtra("TOTAL_STUDENTS", totalStudents)
        }
        startActivity(intent)
    }

    private fun filterSubjectCards(query: String) {
        val cards = listOf(
            binding.cardMapelSeni to "seni kreativitas menggambar warna melipat",
            binding.cardMapelBahasa to "bahasa komunikasi huruf eja membaca kata",
            binding.cardMapelLogika to "berpikir logika hitung angka matematika geometri pola",
            binding.cardMapelMotorik to "belajar fisik motorik senam lompat origami jalan",
            binding.cardMapelAgama to "nilai agama moral doa bersyukur ibadah adab",
            binding.cardMapelSosial to "sosial emotional emosi senang sedih maaf terima kasih"
        )

        cards.forEach { (card, keywords) ->
            val matches = query.isEmpty() || keywords.contains(query) || query.split(" ").all { keywords.contains(it) }
            card.visibility = if (matches) View.VISIBLE else View.GONE
        }
    }
}
