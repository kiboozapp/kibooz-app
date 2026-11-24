package com.nthb.kibooz.ui.guru

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.nthb.kibooz.appContainer
import com.nthb.kibooz.databinding.ActivityProfilGuruBinding
import com.nthb.kibooz.sessionManager
import com.nthb.kibooz.ui.account.HapusAkunActivity
import com.nthb.kibooz.ui.account.UbahKataSandiActivity
import com.nthb.kibooz.ui.camera.CameraScanMoodActivity
import com.nthb.kibooz.util.InsetsUtil
import kotlinx.coroutines.launch

class ProfilGuruActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfilGuruBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfilGuruBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Terapkan insets lengkap: Status bar pada header atas, Navigation bar pada bottom bar & FAB
        InsetsUtil.applySystemBarInsets(
            root = binding.clRootProfilGuru,
            headerView = binding.clHeaderArea,
            bottomBar = binding.clBottomNavigation,
            fab = binding.fabScannerFace
        )

        setupListeners()
        bindProfileData()
    }

    override fun onResume() {
        super.onResume()
        bindProfileData()
    }

    private fun bindProfileData() {
        val sessionUser = appContainer.authRepository.session.value?.user
        if (!sessionUser?.fullName.isNullOrBlank()) {
            binding.tvNamaGuru.text = sessionUser?.fullName
        }

        lifecycleScope.launch {
            if (sessionUser == null) {
                val stored = appContainer.sessionStore.readSession()?.user
                if (!stored?.fullName.isNullOrBlank()) {
                    binding.tvNamaGuru.text = stored?.fullName
                }
            }
        }
    }

    private fun setupListeners() {
        // Tombol Lihat Detail -> Membuka Detail Profil Guru
        binding.btnLihatDetail.setOnClickListener {
            startActivity(Intent(this, DetailProfilGuruActivity::class.java))
        }

        // Menu 1: Ubah Kata Sandi
        binding.menuUbahSandi.setOnClickListener {
            startActivity(Intent(this, UbahKataSandiActivity::class.java))
        }

        // Menu 2: Syarat dan Ketentuan
        binding.menuSyaratKetentuan.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Syarat dan Ketentuan Penggunaan")
                .setMessage(
                    "Ketentuan Layanan Aplikasi Kibooz bagi Guru / Pendidik:\n\n" +
                    "1. Akun dan Hak Akses:\n" +
                    "Akun guru diperuntukkan khusus bagi tenaga pendidik resmi untuk mengelola rombongan belajar, mencatat kondisi emosional harian siswa, menyetujui perpindahan kelas, dan membagikan materi pembelajaran.\n\n" +
                    "2. Kerahasiaan Data Siswa:\n" +
                    "Seluruh identitas siswa, NISN, dan riwayat mood kelas bersifat rahasia dan hanya digunakan untuk keperluan bimbingan serta komunikasi pendidikan dengan orang tua/wali resmi.\n\n" +
                    "3. Konten Kelas dan Forum Diskusi:\n" +
                    "Guru bertanggung jawab menjaga ketertiban komunikasi di forum diskusi serta memastikan tautan video pembelajaran yang ditambahkan sesuai untuk usia PAUD/TK.\n\n" +
                    "4. Kode Gabung dan Keanggotaan:\n" +
                    "Kode gabung kelas hanya dibagikan kepada wali murid yang berhak mendaftarkan atau memindahkan anaknya ke rombel terkait."
                )
                .setPositiveButton("Saya Mengerti", null)
                .show()
        }

        // Menu 3: Kebijakan Privasi
        binding.menuKebijakanPrivasi.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Kebijakan Privasi & Keamanan Data")
                .setMessage(
                    "Komitmen Privasi Kibooz:\n\n" +
                    "1. Pemrosesan Citra Wajah di Perangkat (Edge AI):\n" +
                    "Seluruh pemindaian ekspresi wajah siswa diproses secara lokal langsung pada perangkat guru menggunakan model TensorFlow Lite. Foto maupun video wajah anak tidak pernah diunggah ke server.\n\n" +
                    "2. Data yang Dikirim ke Server:\n" +
                    "Aplikasi hanya mengirimkan label kategori emosi (Senang, Bingung, Sedih, Marah), tingkat keyakinan model, dan catatan guru secara terenkripsi melalui HTTPS.\n\n" +
                    "3. Isolasi Data Kelas:\n" +
                    "Data peserta kelas, forum, video, dan statistik mood hanya dapat diakses oleh tim pengajar pada kelas yang bersangkutan.\n\n" +
                    "4. Kontrol Akun dan Keamanan:\n" +
                    "Guru dapat memperbarui kata sandi atau menghapus akun kapan saja melalui menu pengaturan profil."
                )
                .setPositiveButton("Tutup", null)
                .show()
        }

        // Menu 4: Panduan
        binding.menuPanduan.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Panduan Aplikasi Kibooz untuk Guru")
                .setMessage(
                    "Panduan Cepat Penggunaan Kibooz Guru:\n\n" +
                    "1. Memindai Mood Harian Siswa:\n" +
                    "Ketuk tombol kamera scanner di bagian tengah bawah untuk mendeteksi ekspresi siswa di kelas, lalu konfirmasi dan simpan catatan pendampingan pada layar review.\n\n" +
                    "2. Memantau Statistik Mood Kelas:\n" +
                    "Buka tab Statistik di navigasi bawah untuk melihat grafik distribusi emosi harian, tren mingguan, tren bulanan, serta mencari status mood masing-masing siswa.\n\n" +
                    "3. Mengelola Ruang Kelas & Tema:\n" +
                    "Buka tab Ruang Kelas untuk membuat kelas baru (maksimal 20 kelas aktif), menyalin atau memperbarui kode gabung, serta memilih 6 tema pembelajaran di tiap kelas.\n\n" +
                    "4. Modul, Video Belajar & Forum:\n" +
                    "Di dalam detail tema pembelajaran, ketuk tab Video Belajar untuk menambahkan tautan video edukasi atau buka Forum Diskusi untuk berkomunikasi dengan wali murid.\n\n" +
                    "5. Melihat Peringkat Pilah Sampah:\n" +
                    "Ketuk kartu Ranking Kelas pada Dasbor Utama untuk memantau keaktifan siswa dalam memilah sampah."
                )
                .setPositiveButton("Tutup", null)
                .show()
        }

        // Menu 5: Keluar Akun
        binding.menuKeluar.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Keluar Akun Guru")
                .setMessage("Apakah Anda yakin ingin keluar dari akun Kibooz Guru?")
                .setPositiveButton("Ya, Keluar") { _, _ ->
                    sessionManager.signOut()
            }
                .setNegativeButton("Batal", null)
                .show()
        }

        // Menu 6: Hapus Akun
        binding.menuHapusAkun.setOnClickListener {
            startActivity(HapusAkunActivity.intent(this, isTeacher = true))
        }

        // Bottom Navigation Tabs
        binding.tabHome.setOnClickListener {
            // Kembali ke dasbor yang sudah ada, bukan menumpuk dasbor baru
            startActivity(
                Intent(this, DashboardGuruActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
            )
            finish()
        }

        binding.tabKelas.setOnClickListener {
            startActivity(Intent(this, RuangKelasGuruActivity::class.java))
            finish()
        }

        binding.tabStats.setOnClickListener {
            startActivity(Intent(this, StatistikMoodActivity::class.java))
            finish()
        }

        binding.fabScannerFace.setOnClickListener {
            startActivity(Intent(this, CameraScanMoodActivity::class.java))
        }
    }
}
