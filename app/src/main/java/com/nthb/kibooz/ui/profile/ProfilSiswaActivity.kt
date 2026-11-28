package com.nthb.kibooz.ui.profile

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import com.nthb.kibooz.R
import com.nthb.kibooz.appContainer
import com.nthb.kibooz.ui.camera.CameraScanSampahActivity
import com.nthb.kibooz.ui.components.WaliTab
import com.nthb.kibooz.ui.theme.KiboozTheme
import com.nthb.kibooz.ui.wali.DashboardWaliActivity
import com.nthb.kibooz.ui.wali.LeaderboardActivity
import com.nthb.kibooz.ui.wali.StatistikSampahActivity
import com.nthb.kibooz.ui.wali.WaliProfileState
import com.nthb.kibooz.ui.wali.WaliProfileViewModel
import kotlinx.coroutines.launch

/**
 * Profil seorang anak beserta data Wali. Wali dengan beberapa anak memilih anak dari nama di header, menambah anak
 * dengan kode kelas, mengedit data dan menghapus anak yang dipilih. Isi layarnya digambar oleh [ProfilSiswaScreen].
 */
class ProfilSiswaActivity : AppCompatActivity() {

    private val viewModel: WaliProfileViewModel by viewModels {
        WaliProfileViewModel.Factory(appContainer.waliProfileRepository, intent.getStringExtra(EXTRA_STUDENT_ID))
    }

    // Edit anak bisa mengganti foto di URL yang sama, jadi gambar dimuat ulang setiap perubahan berhasil
    private var avatarVersion by mutableIntStateOf(0)

    /** Edit atau tambah anak yang berhasil mengubah data, jadi profil dimuat ulang. */
    private val changeLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            avatarVersion++
            viewModel.load()
        }
    }

    private var errorSnackbarShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KiboozTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                ProfilSiswaScreen(
                    state = state,
                    avatarLoader = appContainer.avatarLoader,
                    avatarVersion = avatarVersion,
                    onPickChild = ::pickChild,
                    onEdit = ::openEdit,
                    onAddChild = ::openAddChild,
                    onDelete = ::confirmDelete,
                    onRetry = viewModel::load,
                    onTab = ::openTab,
                    onScan = ::openScan
                )
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::handleEvents)
            }
        }
    }

    /** Menampilkan pesan sekali pakai. Galat muat tanpa data lama tampil sebagai kartu di layar. */
    private fun handleEvents(state: WaliProfileState) {
        val root = findViewById<View>(android.R.id.content)
        val error = state.loadError
        // Dengan data lama, cukup satu pesan di bawah dengan coba lagi
        if (error != null && state.profile != null && !errorSnackbarShown) {
            errorSnackbarShown = true
            Snackbar.make(root, error, Snackbar.LENGTH_INDEFINITE)
                .setAction(R.string.coba_lagi) { viewModel.load() }
                .show()
        }
        if (error == null) errorSnackbarShown = false

        state.message?.let {
            Snackbar.make(root, it, Snackbar.LENGTH_LONG).show()
            viewModel.messageShown()
        }
    }

    private fun pickChild() {
        val state = viewModel.state.value
        if (!state.canPickChild) return

        val checked = state.children.indexOfFirst { it.id == state.profile?.id }
        AlertDialog.Builder(this)
            .setTitle(R.string.wali_pilih_anak)
            .setSingleChoiceItems(state.children.map { it.fullName }.toTypedArray(), checked) { dialog, which ->
                viewModel.selectChild(state.children[which].id)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.batal, null)
            .show()
    }

    /** Dialog menyebut apa yang hilang. Menghapus tidak bisa dibatalkan di server. */
    private fun confirmDelete() {
        val state = viewModel.state.value
        val profile = state.profile ?: return
        val lastChild = state.children.size == 1
        val message = getString(R.string.anak_hapus_pesan) +
            if (lastChild) "\n\n" + getString(R.string.anak_hapus_pesan_terakhir) else ""

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.anak_hapus_judul, profile.fullName))
            .setMessage(message)
            .setPositiveButton(R.string.anak_hapus_ya) { _, _ -> viewModel.deleteSelectedChild() }
            .setNegativeButton(R.string.batal, null)
            .show()
    }

    private fun openEdit() {
        val id = viewModel.state.value.profile?.id ?: return
        changeLauncher.launch(Intent(this, EditProfilSiswaActivity::class.java).putExtra(EXTRA_STUDENT_ID, id))
    }

    private fun openAddChild() {
        changeLauncher.launch(Intent(this, TambahAnakActivity::class.java))
    }

    private fun openScan() {
        startActivity(
            Intent(this, CameraScanSampahActivity::class.java)
                .putExtra(CameraScanSampahActivity.EXTRA_STUDENT_ID, viewModel.state.value.profile?.id)
        )
    }

    /** Anak yang dipilih ikut dibawa supaya statistik dan peringkatnya milik anak yang sama. */
    private fun openTab(tab: WaliTab) {
        val profile = viewModel.state.value.profile
        when (tab) {
            WaliTab.PROFILE -> Unit
            WaliTab.HOME -> {
                // Kembali ke dasbor yang sudah ada, bukan menumpuk dasbor baru
                startActivity(
                    Intent(this, DashboardWaliActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        profile?.id?.let { putExtra(EXTRA_STUDENT_ID, it) }
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

    companion object {
        /** Anak yang ditampilkan. Tanpa extra ini yang tampil adalah anak yang paling lama terdaftar. */
        const val EXTRA_STUDENT_ID = "STUDENT_ID"
    }
}
