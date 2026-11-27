package com.nthb.kibooz.ui.guru

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.nthb.kibooz.R
import com.nthb.kibooz.appContainer
import com.nthb.kibooz.data.remote.ClassSummary
import com.nthb.kibooz.data.remote.ServiceOutage
import com.nthb.kibooz.databinding.ActivityRuangKelasGuruBinding
import com.nthb.kibooz.databinding.DialogTambahKelasBinding
import com.nthb.kibooz.databinding.ItemKelasGuruBinding
import com.nthb.kibooz.ui.camera.CameraScanMoodActivity
import com.nthb.kibooz.util.ClassNames
import com.nthb.kibooz.util.InsetsUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Ruang kelas Guru. Daftar kelas dan jumlah anaknya dari server, kode kelas dengan tombol salin, dan aksi kelas
 * (kode baru, buka atau tutup pendaftaran, arsip, pulihkan). Kelas yang diarsipkan tampil terpisah dan hanya
 * punya aksi pulihkan, karena server menolak perubahan lain dengan `CLASS_ARCHIVED`.
 */
class RuangKelasGuruActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRuangKelasGuruBinding
    private val viewModel: KelolaKelasViewModel by viewModels {
        KelolaKelasViewModel.Factory(appContainer.classManagementRepository)
    }

    private val moveRequestsLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            renderedCards = null
            viewModel.load()
        }
    }

    private var addDialog: AlertDialog? = null
    private var addDialogBinding: DialogTambahKelasBinding? = null

    // Kartu hanya dibangun ulang saat isinya berubah, jadi mengetik di kolom cari tidak mengedip
    private var renderedCards: Triple<List<ClassSummary>, List<ClassSummary>, String?>? = null

    // Galat muat ulang yang sudah dijelaskan, supaya pesannya tidak muncul lagi di setiap perubahan state
    @StringRes private var announcedLoadError: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRuangKelasGuruBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetsUtil.applySystemBarInsets(
            root = binding.root,
            headerView = binding.clTopBar,
            bottomBar = binding.clBottomNavigation,
            fab = binding.fabScanner
        )

        setupListeners()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect(::render) }
                launch { viewModel.state.map { it.outage }.distinctUntilChanged().collectLatest(::runOutageCountdown) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Kembali dari layar lain bisa membawa jumlah anak yang baru
        val state = viewModel.state.value
        if (!state.loading && state.classes != null) viewModel.load()
    }

    override fun onDestroy() {
        addDialog?.setOnDismissListener(null)
        addDialog?.dismiss()
        super.onDestroy()
    }

    private fun setupListeners() {
        binding.ivBack.setOnClickListener { finish() }

        binding.btnTambahKelas.setOnClickListener { showAddDialog() }
        binding.btnCobaLagi.setOnClickListener { viewModel.load() }
        binding.connectionStatus.onRetry = { viewModel.load() }
        binding.etSearchKelas.doAfterTextChanged { viewModel.setQuery(it?.toString().orEmpty()) }

        // Bottom Navigation
        binding.tabHome.setOnClickListener { finish() }
        binding.tabStatistik.setOnClickListener {
            startActivity(Intent(this, StatistikMoodActivity::class.java))
            finish()
        }
        binding.tabProfil.setOnClickListener {
            startActivity(Intent(this, ProfilGuruActivity::class.java))
            finish()
        }
        binding.fabScanner.setOnClickListener {
            startActivity(Intent(this, CameraScanMoodActivity::class.java))
        }
    }

    /**
     * Hitung mundur sampai percobaan otomatis berikutnya saat server tidak terjangkau (FE-12). Berhenti sendiri
     * saat layar tidak terlihat, dan dimulai lagi dari awal saat layar tampil kembali.
     */
    private suspend fun runOutageCountdown(outage: ServiceOutage?) {
        if (outage == null) {
            binding.connectionStatus.hide()
            return
        }

        for (secondsLeft in outage.waitSeconds downTo 1) {
            binding.connectionStatus.show(outage.kind, secondsLeft.toInt())
            delay(1_000)
        }
        viewModel.retryAfterOutage()
    }

    private fun render(state: KelolaKelasState) {
        val noData = state.classes == null
        binding.tvDaftarJudul.text =
            if (noData) getString(R.string.kelola_judul_polos) else getString(R.string.kelola_judul_daftar, state.activeTotal)
        binding.btnTambahKelas.isEnabled = !noData && !state.creating
        binding.llCari.isVisible = !state.classes.isNullOrEmpty()

        binding.llMemuat.isVisible = noData && state.loading && state.outage == null
        val failed = noData && !state.loading && state.loadError != null && state.outage == null
        binding.llGagal.isVisible = failed
        if (failed) state.loadError?.let { binding.tvGagalPesan.setText(it) }
        binding.llKosong.isVisible = state.empty
        binding.tvTidakCocok.isVisible = state.noMatch

        showCards(state)
        renderAddDialog(state)
        renderNotice(state.notice)

        if (state.createDone) {
            addDialog?.dismiss()
            viewModel.createHandled()
        }

        renderReloadError(noData, state)
    }

    private fun renderNotice(notice: KelolaNotice?) {
        if (notice == null) return
        val args = if (notice.args.isNotEmpty() && notice.res in classNameNoticeResources) {
            notice.args.mapIndexed { index, value ->
                if (index == 0) ClassNames.stemOrFallback(value, "ini") else value
            }
        } else {
            notice.args
        }
        val message = when (args.size) {
            0 -> getString(notice.res)
            1 -> getString(notice.res, args[0])
            else -> getString(notice.res, args[0], args[1])
        }
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
        viewModel.noticeShown()
    }

    private fun renderReloadError(noData: Boolean, state: KelolaKelasState) {
        val loadError = state.loadError
        if (loadError == null) {
            announcedLoadError = null
            return
        }
        if (!noData && state.outage == null && loadError != announcedLoadError) {
            announcedLoadError = loadError
            Snackbar.make(binding.root, loadError, Snackbar.LENGTH_LONG)
                .setAnchorView(binding.clBottomNavigation)
                .setAction(R.string.coba_lagi) { viewModel.load() }
                .show()
        }
    }

    private fun showCards(state: KelolaKelasState) {
        val activeList = state.active

        val key = Triple(activeList, state.archived, state.busyClassId)
        if (key == renderedCards) return
        renderedCards = key

        binding.llKelasAktif.removeAllViews()
        activeList.forEach { addCard(binding.llKelasAktif, it, state.busyClassId == it.id) }

        binding.llArsip.isVisible = state.archived.isNotEmpty()
        binding.tvArsipJudul.text = getString(R.string.kelola_arsip_judul, state.archivedTotal)
        binding.llKelasArsip.removeAllViews()
        state.archived.forEach { addCard(binding.llKelasArsip, it, state.busyClassId == it.id) }
    }

    private fun addCard(parent: LinearLayout, item: ClassSummary, busy: Boolean) {
        val card = ItemKelasGuruBinding.inflate(layoutInflater, parent, false)
        val archived = item.archived

        card.tvNama.text = item.name
        card.tvMeta.text = getString(R.string.kelola_meta, item.gradeLevel, item.academicYear, item.schoolName)
        card.tvAnak.text = getString(R.string.kelola_anak, item.totalStudents)

        val (statusText, statusBackground, statusColor) = when {
            archived -> Triple(R.string.kelola_status_arsip, R.drawable.bg_chip_kelas_arsip, R.color.kelola_status_arsip_text)
            item.joinOpen -> Triple(R.string.kelola_status_buka, R.drawable.bg_chip_kelas_buka, R.color.kelola_status_buka_text)
            else -> Triple(R.string.kelola_status_tutup, R.drawable.bg_chip_kelas_tutup, R.color.kelola_status_tutup_text)
        }
        card.tvStatus.setText(statusText)
        card.tvStatus.setBackgroundResource(statusBackground)
        card.tvStatus.setTextColor(ContextCompat.getColor(this, statusColor))

        // Kelas yang diarsipkan hanya bisa dipulihkan. Kode, pendaftaran, kode baru dan arsip ditolak server
        card.llKode.isVisible = !archived
        card.llAksi.isVisible = !archived
        card.btnPermintaanPindah.isVisible = !archived
        card.btnPulihkan.isVisible = archived

        val shortClassName = ClassNames.stemOrFallback(item.name, "ini")

        if (archived) {
            card.cardKelas.isClickable = false
            card.cardKelas.isFocusable = false
            card.btnPulihkan.contentDescription = getString(R.string.kelola_aksi_pulihkan_cd, shortClassName)
            card.btnPulihkan.isEnabled = !busy
            card.btnPulihkan.setOnClickListener {
                confirm(
                    R.string.kelola_konfirmasi_pulihkan_judul,
                    getString(R.string.kelola_konfirmasi_pulihkan_isi, shortClassName),
                    R.string.kelola_konfirmasi_pulihkan_ya
                ) { viewModel.restore(item.id) }
            }
        } else {
            card.cardKelas.setOnClickListener { openDetailKelas(item) }

            card.btnPermintaanPindah.tag = item.id
            card.btnPermintaanPindah.text = getString(R.string.guru_pindah_cek)
            card.btnPermintaanPindah.setStrokeColorResource(R.color.kibooz_primary_purple)
            card.btnPermintaanPindah.setTextColor(getColor(R.color.kibooz_primary_purple))
            card.btnPermintaanPindah.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT)
            card.btnPermintaanPindah.setOnClickListener {
                moveRequestsLauncher.launch(PermintaanPindahKelasActivity.intent(this, item.id, item.name))
            }
            lifecycleScope.launch {
                val res = appContainer.moveRequestRepository.classMoveRequests(item.id)
                if (res is com.nthb.kibooz.data.remote.ApiResult.Success && card.btnPermintaanPindah.tag == item.id) {
                    val count = res.data.size
                    if (count > 0) {
                        card.btnPermintaanPindah.text = getString(R.string.guru_pindah_cek_jumlah, count)
                        card.btnPermintaanPindah.backgroundTintList = android.content.res.ColorStateList.valueOf(
                            getColor(R.color.kibooz_lavender_light)
                        )
                    }
                }
            }

            card.tvKode.text = item.joinCode
            card.btnSalin.contentDescription = getString(R.string.kelola_salin_cd, item.joinCode)
            card.btnSalin.setOnClickListener { copyJoinCode(item.joinCode) }

            card.btnKodeBaru.contentDescription = getString(R.string.kelola_aksi_kode_baru_cd, shortClassName)
            card.btnKodeBaru.isEnabled = !busy
            card.btnKodeBaru.setOnClickListener {
                confirm(
                    R.string.kelola_konfirmasi_kode_judul,
                    getString(R.string.kelola_konfirmasi_kode_isi, shortClassName),
                    R.string.kelola_konfirmasi_kode_ya
                ) { viewModel.rotateJoinCode(item.id) }
            }

            val closing = item.joinOpen
            card.btnPendaftaran.setText(if (closing) R.string.kelola_aksi_tutup else R.string.kelola_aksi_buka)
            card.btnPendaftaran.contentDescription =
                getString(if (closing) R.string.kelola_aksi_tutup_cd else R.string.kelola_aksi_buka_cd, shortClassName)
            card.btnPendaftaran.isEnabled = !busy
            card.btnPendaftaran.setOnClickListener {
                confirm(
                    if (closing) R.string.kelola_konfirmasi_tutup_judul else R.string.kelola_konfirmasi_buka_judul,
                    getString(if (closing) R.string.kelola_konfirmasi_tutup_isi else R.string.kelola_konfirmasi_buka_isi, shortClassName),
                    if (closing) R.string.kelola_konfirmasi_tutup_ya else R.string.kelola_konfirmasi_buka_ya
                ) { viewModel.setJoining(item.id, open = !closing) }
            }

            card.btnArsip.contentDescription = getString(R.string.kelola_aksi_arsip_cd, shortClassName)
            card.btnArsip.isEnabled = !busy
            card.btnArsip.setOnClickListener {
                confirm(
                    R.string.kelola_konfirmasi_arsip_judul,
                    getString(R.string.kelola_konfirmasi_arsip_isi, shortClassName),
                    R.string.kelola_konfirmasi_arsip_ya
                ) { viewModel.archive(item.id) }
            }
        }

        parent.addView(card.root)
    }

    private fun confirm(@StringRes title: Int, message: String, @StringRes positive: Int, onConfirm: () -> Unit) {
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(positive) { _, _ -> onConfirm() }
            .setNegativeButton(R.string.batal, null)
            .show()
    }

    private fun copyJoinCode(code: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.kelola_kode_label), code))
        Snackbar.make(binding.root, getString(R.string.kelola_kode_tersalin, code), Snackbar.LENGTH_SHORT).show()
    }

    private fun openDetailKelas(item: ClassSummary) {
        val intent = Intent(this, TemaKelasGuruActivity::class.java).apply {
            putExtra("KODE_KELAS", item.joinCode)
            putExtra("NAMA_KELAS", item.name)
            putExtra("CLASS_ID", item.id)
            putExtra("GRADE_LEVEL", item.gradeLevel)
            putExtra("TOTAL_STUDENTS", item.totalStudents)
        }
        startActivity(intent)
    }

    private fun showAddDialog() {
        viewModel.clearCreateError()
        val form = DialogTambahKelasBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.kelola_dialog_judul)
            .setView(form.root)
            .setPositiveButton(R.string.kelola_dialog_buat, null)
            .setNegativeButton(R.string.batal, null)
            .setOnDismissListener {
                addDialog = null
                addDialogBinding = null
                viewModel.clearCreateError()
            }
            .create()
        dialog.setCanceledOnTouchOutside(false)

        // Tombol dipasang sesudah show supaya dialog tidak menutup sendiri saat isian belum benar
        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener { submitAddDialog(form) }
        }
        form.etTahun.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) submitAddDialog(form)
            actionId == EditorInfo.IME_ACTION_DONE
        }

        addDialog = dialog
        addDialogBinding = form
        dialog.show()
        renderAddDialog(viewModel.state.value)
    }

    private fun submitAddDialog(form: DialogTambahKelasBinding) {
        viewModel.createClass(
            name = form.etNama.text?.toString().orEmpty(),
            gradeLevel = form.etTingkat.text?.toString().orEmpty(),
            academicYear = form.etTahun.text?.toString().orEmpty()
        )
    }

    private fun renderAddDialog(state: KelolaKelasState) {
        val dialog = addDialog ?: return
        val form = addDialogBinding ?: return

        val positive = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
        positive?.isEnabled = !state.creating
        positive?.setText(if (state.creating) R.string.kelola_dialog_membuat else R.string.kelola_dialog_buat)
        dialog.getButton(DialogInterface.BUTTON_NEGATIVE)?.isEnabled = !state.creating
        listOf(form.etNama, form.etTingkat, form.etTahun).forEach { it.isEnabled = !state.creating }

        val error = state.createError
        form.tvError.isVisible = error != null
        if (error != null) form.tvError.setText(error)
    }

    private companion object {
        val classNameNoticeResources = setOf(
            R.string.kelola_hasil_dibuat,
            R.string.kelola_hasil_kode_baru,
            R.string.kelola_hasil_dibuka,
            R.string.kelola_hasil_ditutup,
            R.string.kelola_hasil_diarsipkan,
            R.string.kelola_hasil_dipulihkan
        )
    }
}
