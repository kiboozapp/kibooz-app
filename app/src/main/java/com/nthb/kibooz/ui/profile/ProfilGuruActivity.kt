package com.nthb.kibooz.ui.profile

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.nthb.kibooz.R
import com.nthb.kibooz.appContainer
import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.errorMessageRes
import com.nthb.kibooz.sessionManager
import com.nthb.kibooz.ui.account.AvatarPickerHelper
import com.nthb.kibooz.ui.account.HapusAkunActivity
import com.nthb.kibooz.ui.account.UbahKataSandiActivity
import com.nthb.kibooz.ui.camera.CameraScanMoodActivity
import com.nthb.kibooz.ui.components.GuruTab
import com.nthb.kibooz.ui.guru.DashboardGuruActivity
import com.nthb.kibooz.ui.guru.DetailProfilGuruActivity
import com.nthb.kibooz.ui.guru.RuangKelasGuruActivity
import com.nthb.kibooz.ui.guru.StatistikMoodActivity
import com.nthb.kibooz.ui.theme.KiboozTheme
import com.nthb.kibooz.util.TimeFormat
import kotlinx.coroutines.launch

class ProfilGuruActivity : AppCompatActivity() {

    private lateinit var avatarPicker: AvatarPickerHelper
    private var header by mutableStateOf(GuruProfileHeader(name = "", subtitle = "", avatarUrl = null))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        avatarPicker = AvatarPickerHelper(this) { croppedFile ->
            uploadAvatar(croppedFile)
        }

        setContent {
            KiboozTheme {
                ProfilGuruScreen(
                    header = header,
                    avatarLoader = appContainer.avatarLoader,
                    onAvatar = avatarPicker::launchPicker,
                    onDetail = { startActivity(Intent(this, DetailProfilGuruActivity::class.java)) },
                    menu = menuItems(),
                    onTab = ::openTab,
                    onScan = { startActivity(Intent(this, CameraScanMoodActivity::class.java)) }
                )
            }
        }
        bindProfileData()
    }

    override fun onResume() {
        super.onResume()
        bindProfileData()
    }

    private fun bindProfileData() {
        val sessionUser = appContainer.authRepository.session.value?.user
        if (sessionUser != null) {
            header = header.copy(
                name = sessionUser.fullName.ifBlank { header.name },
                avatarUrl = sessionUser.avatarUrl
            )
        }

        lifecycleScope.launch {
            if (sessionUser == null) {
                val stored = appContainer.sessionStore.readSession()?.user
                if (stored != null) {
                    header = header.copy(name = stored.fullName.ifBlank { header.name }, avatarUrl = stored.avatarUrl)
                }
            }
            when (val res = appContainer.guruProfileRepository.profileDetail()) {
                is ApiResult.Success -> {
                    val d = res.data
                    val joined = TimeFormat.localDate(d.joinedAt)
                    header = GuruProfileHeader(
                        name = d.fullName,
                        subtitle = if (joined != null) "Bergabung sejak $joined" else d.schoolName,
                        avatarUrl = d.avatarUrl
                    )
                    appContainer.authRepository.updateSessionUser(fullName = d.fullName, avatarUrl = d.avatarUrl)
                }
                else -> Unit
            }
        }
    }

    private fun uploadAvatar(jpegFile: java.io.File) {
        Toast.makeText(this, R.string.avatar_mengunggah, Toast.LENGTH_SHORT).show()
        val oldUrl = appContainer.authRepository.session.value?.user?.avatarUrl
        lifecycleScope.launch {
            when (val res = appContainer.accountRepository.uploadAvatar(jpegFile, studentId = null)) {
                is ApiResult.Success -> {
                    appContainer.avatarLoader.evict(oldUrl)
                    appContainer.authRepository.updateSessionUser(avatarUrl = res.data)
                    header = header.copy(avatarUrl = res.data)
                    Toast.makeText(this@ProfilGuruActivity, R.string.avatar_sukses, Toast.LENGTH_SHORT).show()
                }
                else -> {
                    Toast.makeText(
                        this@ProfilGuruActivity,
                        res.errorMessageRes() ?: R.string.error_unknown,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun menuItems() = listOf(
        ProfileMenuItem(R.drawable.ic_key, getString(R.string.profil_menu_ubah_sandi), {
            startActivity(Intent(this, UbahKataSandiActivity::class.java))
        }),
        ProfileMenuItem(R.drawable.ic_document, getString(R.string.profil_menu_syarat), ::showTerms),
        ProfileMenuItem(R.drawable.ic_shield_check, getString(R.string.profil_menu_privasi), ::showPrivacy),
        ProfileMenuItem(R.drawable.ic_book, getString(R.string.profil_menu_panduan), ::showGuide),
        ProfileMenuItem(R.drawable.ic_logout, getString(R.string.profil_menu_keluar), ::confirmSignOut, danger = true),
        ProfileMenuItem(
            R.drawable.ic_trash_delete,
            getString(R.string.hapus_akun_menu),
            { startActivity(HapusAkunActivity.intent(this, isTeacher = true)) },
            danger = true
        )
    )

    private fun showTerms() {
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

    private fun showPrivacy() {
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

    private fun showGuide() {
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

    private fun confirmSignOut() {
        AlertDialog.Builder(this)
            .setTitle("Keluar Akun Guru")
            .setMessage("Apakah Anda yakin ingin keluar dari akun Kibooz Guru?")
            .setPositiveButton("Ya, Keluar") { _, _ -> sessionManager.signOut() }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun openTab(tab: GuruTab) {
        when (tab) {
            GuruTab.PROFILE -> Unit
            GuruTab.HOME -> {
                // Kembali ke dasbor yang sudah ada, bukan menumpuk dasbor baru
                startActivity(
                    Intent(this, DashboardGuruActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                )
                finish()
            }
            GuruTab.CLASS -> {
                startActivity(Intent(this, RuangKelasGuruActivity::class.java))
                finish()
            }
            GuruTab.STATS -> {
                startActivity(Intent(this, StatistikMoodActivity::class.java))
                finish()
            }
        }
    }
}
