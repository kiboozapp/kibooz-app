package com.nthb.kibooz.ui.profile

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.nthb.kibooz.R
import com.nthb.kibooz.appContainer
import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.errorMessageRes
import com.nthb.kibooz.sessionManager
import com.nthb.kibooz.ui.account.AvatarPickerHelper
import com.nthb.kibooz.ui.account.HapusAkunActivity
import com.nthb.kibooz.ui.account.UbahKataSandiActivity
import com.nthb.kibooz.ui.camera.CameraScanSampahActivity
import com.nthb.kibooz.ui.components.WaliTab
import com.nthb.kibooz.ui.theme.KiboozTheme
import com.nthb.kibooz.ui.wali.DashboardWaliActivity
import com.nthb.kibooz.ui.wali.LeaderboardActivity
import com.nthb.kibooz.ui.wali.ProfilSiswaActivity
import com.nthb.kibooz.ui.wali.StatistikSampahActivity
import com.nthb.kibooz.ui.wali.TambahAnakActivity
import com.nthb.kibooz.ui.wali.WaliProfileViewModel
import kotlinx.coroutines.launch

class ProfilWaliActivity : AppCompatActivity() {

    private val avatarPicker = AvatarPickerHelper(this, ::uploadSelfAvatar)
    private var avatarVersion by mutableIntStateOf(0)
    private val viewModel: WaliProfileViewModel by viewModels {
        WaliProfileViewModel.Factory(appContainer.waliProfileRepository, intent.getStringExtra(ProfilSiswaActivity.EXTRA_STUDENT_ID)) {
            appContainer.authRepository.restore()?.user?.fullName
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            KiboozTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                val session by appContainer.authRepository.session.collectAsStateWithLifecycle()
                val menu = remember { menuItems() }
                ProfilWaliScreen(
                    state = state,
                    avatarUrl = session?.user?.avatarUrl,
                    avatarVersion = avatarVersion,
                    avatarLoader = appContainer.avatarLoader,
                    onAvatar = avatarPicker::launchPicker,
                    onDetail = ::openDetail,
                    onRetry = viewModel::load,
                    menu = menu,
                    onTab = ::openTab,
                    onScan = ::openScan
                )
            }
        }
    }

    override fun onRestart() {
        super.onRestart()
        // Kembali dari profil siswa membawa nama atau anak yang mungkin berubah
        viewModel.load()
    }

    override fun onResume() {
        super.onResume()
        // Layar lain bisa mengganti foto di URL yang sama, jadi muat ulang setiap kembali ke layar ini
        avatarVersion++
    }

    private fun uploadSelfAvatar(jpegFile: java.io.File) {
        Toast.makeText(this, R.string.avatar_mengunggah, Toast.LENGTH_SHORT).show()
        val oldUrl = appContainer.authRepository.session.value?.user?.avatarUrl
        lifecycleScope.launch {
            when (val res = appContainer.accountRepository.uploadAvatar(jpegFile, studentId = null)) {
                is ApiResult.Success -> {
                    appContainer.avatarLoader.evict(oldUrl)
                    appContainer.authRepository.updateSessionUser(avatarUrl = res.data)
                    avatarVersion++
                    Toast.makeText(this@ProfilWaliActivity, R.string.avatar_sukses, Toast.LENGTH_SHORT).show()
                }
                else -> {
                    Toast.makeText(
                        this@ProfilWaliActivity,
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
        ProfileMenuItem(R.drawable.ic_book, getString(R.string.profil_wali_menu_panduan), ::showGuide),
        ProfileMenuItem(
            R.drawable.ic_logout,
            getString(R.string.profil_wali_menu_keluar),
            ::confirmSignOut,
            danger = true,
            showChevron = true
        ),
        ProfileMenuItem(
            R.drawable.ic_trash_delete,
            getString(R.string.hapus_akun_menu),
            { startActivity(HapusAkunActivity.intent(this, isTeacher = false)) },
            danger = true,
            showChevron = true
        )
    )

    private fun showTerms() {
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

    private fun showPrivacy() {
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

    private fun showGuide() {
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
                "5. Pindah Kelas:\n" +
                "Buka Ruang Kelas Si Kecil lalu ketuk tombol 'Pindah kelas' di kanan atas untuk mengajukan pindah menggunakan kode kelas tujuan."
            )
            .setPositiveButton("Tutup", null)
            .show()
    }

    private fun confirmSignOut() {
        AlertDialog.Builder(this)
            .setTitle("Keluar Akun Wali")
            .setMessage("Apakah Anda yakin ingin keluar dari akun Kibooz Wali?")
            .setPositiveButton("Ya, Keluar") { _, _ -> sessionManager.signOut() }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun openDetail() {
        val state = viewModel.state.value
        val target = if (state.noChild) TambahAnakActivity::class.java else ProfilSiswaActivity::class.java
        startActivity(Intent(this, target).putExtra(ProfilSiswaActivity.EXTRA_STUDENT_ID, state.profile?.id))
    }

    private fun openScan() {
        startActivity(
            Intent(this, CameraScanSampahActivity::class.java)
                .putExtra(CameraScanSampahActivity.EXTRA_STUDENT_ID, viewModel.state.value.profile?.id)
        )
    }

    private fun openTab(tab: WaliTab) {
        val profile = viewModel.state.value.profile
        when (tab) {
            WaliTab.PROFILE -> Unit
            WaliTab.HOME -> {
                // Kembali ke dasbor yang sudah ada, bukan menumpuk dasbor baru
                startActivity(
                    Intent(this, DashboardWaliActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        profile?.id?.let { putExtra(ProfilSiswaActivity.EXTRA_STUDENT_ID, it) }
                    }
                )
                finish()
            }
            WaliTab.WASTE -> {
                startActivity(
                    Intent(this, StatistikSampahActivity::class.java)
                        .putExtra(StatistikSampahActivity.EXTRA_STUDENT_ID, profile?.id)
                )
                finish()
            }
            WaliTab.RANKING -> {
                startActivity(
                    Intent(this, LeaderboardActivity::class.java)
                        .putExtra(LeaderboardActivity.EXTRA_CLASS_ID, profile?.classId)
                )
                finish()
            }
        }
    }
}
