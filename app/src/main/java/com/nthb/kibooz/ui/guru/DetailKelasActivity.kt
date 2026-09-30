package com.nthb.kibooz.ui.guru

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.nthb.kibooz.R
import com.nthb.kibooz.databinding.ActivityDetailKelasBinding
import com.nthb.kibooz.util.InsetsUtil

class DetailKelasActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailKelasBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailKelasBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetsUtil.applySystemBarInsets(
            root = binding.root,
            headerView = binding.clTopBar,
            fab = binding.fabTambahModul
        )

        val kodeKelas = intent.getStringExtra("KODE_KELAS") ?: "SENI-A1"
        val namaKelas = intent.getStringExtra("NAMA_KELAS") ?: "Seni & Kreativitas"

        setupViews(kodeKelas, namaKelas)
        setupListeners(kodeKelas, namaKelas)
    }

    private fun setupViews(kodeKelas: String, namaKelas: String) {
        binding.tvTitleBar.text = "Detail $namaKelas"
        binding.tvNamaKelas.text = namaKelas
        binding.tvKodeKelas.text = kodeKelas

        when {
            kodeKelas.startsWith("BHS") -> binding.ivMapelIcon.setImageResource(R.drawable.img_mapel_bahasa)
            kodeKelas.startsWith("LOG") -> binding.ivMapelIcon.setImageResource(R.drawable.img_mapel_logika)
            kodeKelas.startsWith("MOT") -> binding.ivMapelIcon.setImageResource(R.drawable.img_mapel_motorik)
            kodeKelas.startsWith("AGM") -> binding.ivMapelIcon.setImageResource(R.drawable.img_mapel_agama)
            kodeKelas.startsWith("SOS") -> binding.ivMapelIcon.setImageResource(R.drawable.img_mapel_sosial)
            else -> binding.ivMapelIcon.setImageResource(R.drawable.img_mapel_seni)
        }
    }

    private fun setupListeners(kodeKelas: String, namaKelas: String) {
        binding.ivBack.setOnClickListener {
            finish()
        }

        binding.btnSalinKode.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Kode Kelas", kodeKelas)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Kode Kelas $kodeKelas berhasil disalin!", Toast.LENGTH_SHORT).show()
        }

        binding.ivShareKelas.setOnClickListener {
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, "Ayo bergabung ke kelas Kibooz $namaKelas dengan kode: $kodeKelas")
                type = "text/plain"
            }
            startActivity(Intent.createChooser(shareIntent, "Bagikan Kode Kelas"))
        }

        // Navigasi ke Peserta Kelas (Hamburger Menu, Quick Card, & Tab)
        binding.ivMenuKelas.setOnClickListener {
            openPesertaKelas(namaKelas)
        }
        binding.cardQuickPeserta.setOnClickListener {
            openPesertaKelas(namaKelas)
        }
        binding.tvPengajar.setOnClickListener {
            openPesertaKelas(namaKelas)
        }

        // Navigasi ke Forum Diskusi
        binding.cardQuickForum.setOnClickListener {
            openForumDiskusi(namaKelas)
        }
        binding.tabForum.setOnClickListener {
            openForumDiskusi(namaKelas)
        }

        // Tab switches
        binding.tabModul.setOnClickListener {
            Toast.makeText(this, "Menampilkan daftar modul", Toast.LENGTH_SHORT).show()
        }
        binding.tabVideo.setOnClickListener {
            val intent = Intent(this, VideoPembelajaranActivity::class.java).apply {
                putExtra("NAMA_KELAS", namaKelas)
            }
            startActivity(intent)
        }
        binding.tabAnggota.setOnClickListener {
            openPesertaKelas(namaKelas)
        }

        binding.fabTambahModul.setOnClickListener {
            Toast.makeText(this, "Buka form tambah modul kelas baru", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openPesertaKelas(namaKelas: String) {
        val intent = Intent(this, PesertaKelasActivity::class.java).apply {
            putExtra("NAMA_KELAS", namaKelas)
        }
        startActivity(intent)
    }

    private fun openForumDiskusi(namaKelas: String) {
        val intent = Intent(this, ForumDiskusiActivity::class.java).apply {
            putExtra("NAMA_KELAS", namaKelas)
        }
        startActivity(intent)
    }
}
