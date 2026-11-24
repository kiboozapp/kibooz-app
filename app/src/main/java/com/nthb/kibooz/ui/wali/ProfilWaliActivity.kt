package com.nthb.kibooz.ui.wali

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.nthb.kibooz.appContainer
import com.nthb.kibooz.databinding.ActivityProfilWaliBinding
import com.nthb.kibooz.sessionManager
import com.nthb.kibooz.ui.account.HapusAkunActivity
import com.nthb.kibooz.ui.account.UbahKataSandiActivity
import com.nthb.kibooz.ui.camera.CameraScanSampahActivity
import com.nthb.kibooz.util.InsetsUtil

class ProfilWaliActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfilWaliBinding
    private val viewModel: WaliProfileViewModel by viewModels {
        WaliProfileViewModel.Factory(appContainer.waliProfileRepository, intent.getStringExtra(ProfilSiswaActivity.EXTRA_STUDENT_ID)) {
            appContainer.authRepository.restore()?.user?.fullName
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfilWaliBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetsUtil.applySystemBarInsets(
            root = binding.clRootProfilWali,
            headerView = binding.clHeaderArea,
            bottomBar = binding.clBottomNavigation,
            fab = binding.fabScannerSampah
        )

        setupListeners()
        bindWaliProfile(binding, viewModel)
    }

    override fun onRestart() {
        super.onRestart()
        // Kembali dari profil siswa membawa nama atau anak yang mungkin berubah
        viewModel.load()
    }

    private fun setupListeners() {
        // Menu 1: Ubah Kata Sandi
        binding.menuUbahSandi.setOnClickListener {
            startActivity(Intent(this, UbahKataSandiActivity::class.java))
        }

        // Menu 2: Syarat dan Ketentuan
        binding.menuSyaratKetentuan.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Syarat dan Ketentuan Penggunaan")
                .setMessage(
                    "Ketentuan Layanan Aplikasi Kibooz bagi Orang Tua / Wali Murid:\n\n" +
                    "1. Akun dan Hak Akses:\n" +
                    "Akun wali murid diperuntukkan khusus bagi orang tua atau wali resmi siswa terdaftar untuk memantau perkembangan emosional, catatan presensi mood, dan kebiasaan pilah sampah anak di lingkungan sekolah.\n\n" +
                    "2. Perlindungan Privasi Anak:\n" +
                    "Setiap data anak yang ditampilkan dalam platform ini dilindungi oleh standar keamanan enkripsi. Dilarang menyalahgunakan informasi atau data identitas siswa lain.\n\n" +
                    "3. Penerapan Rekomendasi Panduan:\n" +
                    "Rekomendasi pendampingan emosi di rumah dirancang untuk mendukung interaksi positif antara orang tua dan anak sesuai dengan laporan observasi guru kelas.\n\n" +
                    "4. Poin dan Pilah Sampah:\n" +
                    "Sistem perolehan poin ramah lingkungan ditujukan sebagai sarana edukasi kebiasaan memilah sampah sejak dini."
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
                    "Seluruh pemindaian ekspresi emosi anak dianalisis secara lokal langsung pada perangkat menggunakan model TensorFlow Lite. Tidak ada rekaman foto atau video wajah anak yang diunggah ke server.\n\n" +
                    "2. Data yang Dikirim ke Server:\n" +
                    "Aplikasi hanya mengirimkan label kategori emosi (Senang, Sedih, Bingung, dsb.) beserta tingkat akurasi dan catatan observasi guru secara terenkripsi via HTTPS.\n\n" +
                    "3. Pembatasan Akses Data Roster:\n" +
                    "Sesuai regulasi privasi pendidikan, wali murid hanya memiliki akses penuh terhadap data anak kandung sendiri dan tidak dapat mengakses data pribadi anak lain di kelas.\n\n" +
                    "4. Hak Hapus Akun:\n" +
                    "Pengguna memiliki hak penuh untuk mengubah kata sandi atau menghapus akun beserta seluruh data riwayat kapan saja melalui menu pengaturan profil."
                )
                .setPositiveButton("Tutup", null)
                .show()
        }

        // Menu 4: Panduan
        binding.menuPanduan.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Panduan Aplikasi Kibooz untuk Orang Tua")
                .setMessage(
                    "Panduan Cepat Penggunaan Kibooz:\n\n" +
                    "1. Memantau Mood Harian Si Kecil:\n" +
                    "Buka Dasbor Utama untuk melihat status emosi anak yang dicatat guru setiap hari, lengkap dengan jam pencatatan dan catatan pendampingan dari wali kelas.\n\n" +
                    "2. Menerapkan Panduan di Rumah:\n" +
                    "Jika anak terdeteksi mengalami emosi seperti cemas atau sedih, ketuk kartu rekomendasi panduan untuk membaca tips penanganan dan kirim konfirmasi penerapan ke guru.\n\n" +
                    "3. Memilah Sampah & Klaim Poin:\n" +
                    "Ajak si kecil memilah sampah organik atau anorganik di rumah, lalu ketuk tombol kamera scanner di bagian tengah bawah untuk mendeteksi sampah dan mendapatkan poin.\n\n" +
                    "4. Memantau Ranking & Statistik:\n" +
                    "Ketuk menu Sampah atau Leaderboard di navigasi bawah untuk melihat peringkat keaktifan pilah sampah anak di kelasnya.\n\n" +
                    "5. Bergabung atau Pindah Kelas:\n" +
                    "Buka Ruang Kelas Si Kecil lalu ketuk tombol 'Gabung kelas' di kanan atas untuk memasukkan kode kelas baru dari wali kelas."
                )
                .setPositiveButton("Tutup", null)
                .show()
        }

        // Menu 5: Keluar Akun Wali
        binding.menuKeluar.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Keluar Akun Wali")
                .setMessage("Apakah Anda yakin ingin keluar dari akun Kibooz Wali?")
                .setPositiveButton("Ya, Keluar") { _, _ ->
                    sessionManager.signOut()
                }
                .setNegativeButton("Batal", null)
                .show()
        }

        // Menu 6: Hapus Akun
        binding.menuHapusAkun.setOnClickListener {
            startActivity(HapusAkunActivity.intent(this, isTeacher = false))
        }

        // Bottom Navigation Tabs
        binding.tabHome.setOnClickListener {
            // Kembali ke dasbor yang sudah ada, bukan menumpuk dasbor baru
            startActivity(
                Intent(this, DashboardWaliActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
            )
            finish()
        }

        binding.tabSampah.setOnClickListener {
            startActivity(
                Intent(this, StatistikSampahActivity::class.java)
                    .putExtra(StatistikSampahActivity.EXTRA_STUDENT_ID, viewModel.state.value.profile?.id)
            )
            finish()
        }

        binding.tabLeaderboard.setOnClickListener {
            startActivity(
                Intent(this, LeaderboardActivity::class.java)
                    .putExtra(LeaderboardActivity.EXTRA_CLASS_ID, viewModel.state.value.profile?.classId)
            )
            finish()
        }

        binding.fabScannerSampah.setOnClickListener {
            startActivity(
                Intent(this, CameraScanSampahActivity::class.java)
                    .putExtra(CameraScanSampahActivity.EXTRA_STUDENT_ID, viewModel.state.value.profile?.id)
            )
        }
    }
}
