package com.nthb.kibooz.ui.wali

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import com.nthb.kibooz.R
import com.nthb.kibooz.appContainer
import com.nthb.kibooz.data.remote.ChildProfile
import com.nthb.kibooz.databinding.ActivityProfilSiswaBinding
import com.nthb.kibooz.databinding.ItemInfoRowBinding
import com.nthb.kibooz.sessionManager
import com.nthb.kibooz.ui.camera.CameraScanSampahActivity
import com.nthb.kibooz.util.InsetsUtil
import kotlinx.coroutines.launch

/**
 * Profil seorang anak beserta data Wali. Wali dengan beberapa anak memilih anak dari nama di header, menambah anak
 * dengan kode kelas, mengedit data dan menghapus anak yang dipilih.
 */
class ProfilSiswaActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfilSiswaBinding
    private val viewModel: WaliProfileViewModel by viewModels {
        WaliProfileViewModel.Factory(appContainer.waliProfileRepository, intent.getStringExtra(EXTRA_STUDENT_ID))
    }

    /** Edit atau tambah anak yang berhasil mengubah data, jadi profil dimuat ulang. */
    private val changeLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) viewModel.load()
    }

    private var errorSnackbarShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfilSiswaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetsUtil.applySystemBarInsets(
            root = binding.root,
            headerView = binding.clTopHeader,
            bottomBar = binding.clBottomNavigation,
            fab = binding.fabScannerSampah
        )

        setupListeners()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::render)
            }
        }
    }

    private fun render(state: WaliProfileState) {
        val profile = state.profile

        binding.pbLoading.isVisible = state.loading && profile == null && !state.noChild
        binding.llEmpty.isVisible = state.noChild
        binding.llContent.isVisible = profile != null

        showLoadError(state, profile)

        val header = profile != null
        binding.tvNamaSiswa.isVisible = header
        binding.tvKelasSiswa.isVisible = header
        binding.btnEditProfil.isVisible = header
        if (profile != null) showProfile(state, profile)

        binding.btnHapusAnak.isEnabled = !state.deleting

        state.message?.let {
            Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
            viewModel.messageShown()
        }
    }

    /** Tanpa data, galat muat tampil sebagai kartu dengan coba lagi. Dengan data lama, cukup satu pesan di bawah. */
    private fun showLoadError(state: WaliProfileState, profile: ChildProfile?) {
        val error = state.loadError
        val blocking = error != null && profile == null

        if (blocking && state.outage != null) binding.connectionStatus.show(state.outage) else binding.connectionStatus.hide()
        binding.llError.isVisible = blocking && state.outage == null
        if (error != null) binding.tvError.setText(error)

        if (error != null && profile != null && !errorSnackbarShown) {
            errorSnackbarShown = true
            Snackbar.make(binding.root, error, Snackbar.LENGTH_INDEFINITE)
                .setAction(R.string.coba_lagi) { viewModel.load() }
                .show()
        }
        if (error == null) errorSnackbarShown = false
    }

    private fun showProfile(state: WaliProfileState, profile: ChildProfile) {
        val archived = state.children.firstOrNull { it.id == profile.id }?.classArchivedAt != null
        val className = if (archived) "${profile.className} ${getString(R.string.anak_kelas_diarsipkan)}" else profile.className

        binding.tvNamaSiswa.text = if (state.canPickChild) "${profile.fullName} ▾" else profile.fullName
        binding.tvNamaSiswa.contentDescription =
            if (state.canPickChild) getString(R.string.anak_pilih_anak_desc, profile.fullName) else null
        binding.tvNamaSiswa.isClickable = state.canPickChild
        binding.tvKelasSiswa.text = getString(R.string.anak_kelas_ringkas, className, profile.schoolName)

        binding.cardNisnPrompt.isVisible = profile.nisnNeedsUpdate
        binding.btnTambahAnak.isVisible = state.canAddChild
        binding.tvBatasAnak.isVisible = !state.canAddChild

        val nisnStatus = when {
            profile.nisnNeedsUpdate || profile.nisn.isEmpty() -> R.string.anak_nisn_kosong
            profile.nisnVerified -> R.string.anak_nisn_terverifikasi
            else -> R.string.anak_nisn_belum_terverifikasi
        }
        val rank = if (profile.classRank > 0) "#${profile.classRank}" else getString(R.string.anak_belum_ada_peringkat)
        fillRows(
            binding.llInfoAnak,
            listOf(
                R.string.anak_label_nisn to profile.nisn.ifEmpty { getString(R.string.anak_nisn_kosong) },
                R.string.anak_label_status_nisn to getString(nisnStatus),
                R.string.anak_label_kelas to getString(R.string.anak_kelas_ringkas, className, profile.gradeLevel),
                R.string.anak_label_sekolah to profile.schoolName,
                R.string.anak_label_tahun_ajaran to profile.academicYear,
                R.string.anak_label_peringkat to rank,
                R.string.anak_label_poin to profile.seasonPoints.toString()
            )
        )

        val guardian = profile.guardian
        val empty = getString(R.string.anak_belum_diisi)
        fillRows(
            binding.llInfoWali,
            listOf(
                R.string.anak_label_nama_wali to guardian.fullName,
                R.string.anak_label_email to guardian.email,
                R.string.anak_label_telepon to (guardian.phoneNumber?.takeIf { it.isNotBlank() } ?: empty),
                R.string.anak_label_whatsapp to (guardian.whatsappNumber?.takeIf { it.isNotBlank() } ?: empty),
                R.string.anak_label_alamat to (guardian.address?.takeIf { it.isNotBlank() } ?: empty)
            )
        )
    }

    private fun fillRows(container: ViewGroup, rows: List<Pair<Int, String>>) {
        container.removeAllViews()
        rows.forEach { (label, value) ->
            val row = ItemInfoRowBinding.inflate(LayoutInflater.from(this), container, false)
            row.tvInfoLabel.setText(label)
            row.tvInfoValue.text = value
            container.addView(row.root)
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

    private fun setupListeners() {
        binding.btnEditProfil.setOnClickListener { openEdit() }
        binding.btnIsiNisn.setOnClickListener { openEdit() }
        binding.tvNamaSiswa.setOnClickListener { pickChild() }
        binding.btnTambahAnak.setOnClickListener { openAddChild() }
        binding.btnTambahAnakKosong.setOnClickListener { openAddChild() }
        binding.btnHapusAnak.setOnClickListener { confirmDelete() }
        binding.btnRetry.setOnClickListener { viewModel.load() }
        binding.connectionStatus.onRetry = { viewModel.load() }

        binding.btnLogout.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Keluar Akun Wali")
                .setMessage("Apakah Anda yakin ingin keluar dari akun Kibooz Orang Tua?")
                .setPositiveButton("Ya, Keluar") { _, _ ->
                    sessionManager.signOut()
                }
                .setNegativeButton("Batal", null)
                .show()
        }

        // Bottom Navigation. Anak yang dipilih ikut dibawa supaya statistik dan peringkatnya milik anak yang sama
        binding.tabHome.setOnClickListener {
            // Kembali ke dasbor yang sudah ada, bukan menumpuk dasbor baru
            val intent = Intent(this, DashboardWaliActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
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

    companion object {
        /** Anak yang ditampilkan. Tanpa extra ini yang tampil adalah anak yang paling lama terdaftar. */
        const val EXTRA_STUDENT_ID = "STUDENT_ID"
    }
}
