package com.nthb.kibooz.ui.guru

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.nthb.kibooz.R
import com.nthb.kibooz.appContainer
import com.nthb.kibooz.data.remote.ClassTeacher
import com.nthb.kibooz.data.remote.ServiceOutage
import com.nthb.kibooz.databinding.ActivityPengajarKelasBinding
import com.nthb.kibooz.databinding.DialogTambahPengajarBinding
import com.nthb.kibooz.databinding.ItemPengajarKelasBinding
import com.nthb.kibooz.sessionManager
import com.nthb.kibooz.util.InsetsUtil
import com.nthb.kibooz.util.TimeFormat
import com.nthb.kibooz.util.formatWaitSeconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Guru di sebuah kelas, dibuka dari kartu kelas di [RuangKelasGuruActivity]. Menampilkan pemilik dan baris akun
 * yang masuk, dan hanya tombol yang diizinkan server. Aksi yang mengubah akses meminta konfirmasi lebih dulu.
 */
class PengajarKelasActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPengajarKelasBinding
    private val className: String by lazy { intent.getStringExtra(EXTRA_CLASS_NAME).orEmpty() }

    private val viewModel: PengajarKelasViewModel by viewModels {
        // Yang disimpan hanya pintu keluar akun milik proses, bukan layar ini
        val session = applicationContext.sessionManager
        PengajarKelasViewModel.Factory(
            repository = appContainer.classMemberRepository,
            classId = intent.getStringExtra(EXTRA_CLASS_ID).orEmpty(),
            archived = intent.getBooleanExtra(EXTRA_ARCHIVED, false),
            signOut = session::signOut
        )
    }

    private var addDialog: AlertDialog? = null
    private var addDialogBinding: DialogTambahPengajarBinding? = null
    private var confirmDialog: AlertDialog? = null

    // Baris hanya dibangun ulang saat isinya berubah, supaya fokus pembaca layar tidak berpindah tiap detik
    private var renderedRows: List<Any?>? = null

    // Galat muat ulang yang sudah dijelaskan, supaya pesannya tidak muncul lagi di setiap perubahan state
    @StringRes private var announcedLoadError: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPengajarKelasBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetsUtil.applySystemBarInsets(
            root = binding.root,
            headerView = binding.clTopBar
        )

        binding.tvSubtitle.text = className
        binding.ivBack.setOnClickListener { finish() }
        binding.btnCobaLagi.setOnClickListener { viewModel.load() }
        binding.connectionStatus.onRetry = { viewModel.load() }
        binding.btnTambahGuru.setOnClickListener { viewModel.openAddDialog() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect(::render) }
                launch { viewModel.state.map { it.outage }.distinctUntilChanged().collectLatest(::runOutageCountdown) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.reloadIfIdle()
    }

    override fun onDestroy() {
        // Listener dilepas dulu supaya rotasi tidak dianggap sebagai pengguna menutup dialog
        addDialog?.setOnDismissListener(null)
        addDialog?.dismiss()
        confirmDialog?.dismiss()
        super.onDestroy()
    }

    /** Hitung mundur sampai percobaan otomatis berikutnya saat server tidak terjangkau. */
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

    private fun render(state: PengajarKelasState) {
        if (isFinishing) return
        val end = state.end
        if (end != null) {
            confirmDialog?.dismiss()
            closeFor(end)
            return
        }

        val teachers = state.teachers
        val noData = teachers == null

        binding.pbLoading.isVisible = noData && state.loading
        val failed = noData && !state.loading && state.loadError != null && state.outage == null
        binding.llError.isVisible = failed
        if (failed) state.loadError?.let { binding.tvError.setText(it) }
        binding.btnCobaLagi.isEnabled = state.cooldownSeconds == null
        binding.tvKosong.isVisible = teachers != null && teachers.isEmpty()

        val info = when {
            teachers == null -> null
            state.archived -> R.string.pengajar_info_arsip
            teachers.size == 1 -> R.string.pengajar_info_sendiri
            state.self?.isOwner == true -> R.string.pengajar_info_pemilik
            else -> R.string.pengajar_info_bukan_pemilik
        }
        binding.tvInfo.isVisible = info != null
        if (info != null) binding.tvInfo.setText(info)

        binding.btnTambahGuru.isVisible = state.canAdd
        binding.btnTambahGuru.isEnabled = !state.busy

        val cooldown = state.cooldownSeconds
        binding.tvTunggu.isVisible = cooldown != null
        if (cooldown != null) {
            binding.tvTunggu.text = getString(R.string.pengajar_tunggu, formatWaitSeconds(cooldown))
        }

        showRows(state)
        renderAddDialog(state)

        state.notice?.let {
            Snackbar.make(binding.root, getString(it.res, *it.args.toTypedArray()), Snackbar.LENGTH_LONG).show()
            viewModel.noticeShown()
        }

        // Muat ulang yang gagal saat daftar lama masih tampil dijelaskan lewat pesan singkat, bukan layar penuh
        val loadError = state.loadError
        if (loadError == null) {
            announcedLoadError = null
        } else if (!noData && state.outage == null && loadError != announcedLoadError) {
            announcedLoadError = loadError
            Snackbar.make(binding.root, loadError, Snackbar.LENGTH_LONG)
                .setAction(R.string.coba_lagi) { viewModel.load() }
                .show()
        }
    }

    private fun closeFor(end: ClassScreenEnd) {
        when (end) {
            ClassScreenEnd.LEFT -> closeClassScreens(getString(R.string.pengajar_hasil_keluar, className))
            ClassScreenEnd.LOST -> closeClassScreens(getString(R.string.error_class_gone))
            ClassScreenEnd.ACCOUNT_GONE -> openRoleSelectionForGoneAccount()
        }
    }

    private fun showRows(state: PengajarKelasState) {
        val key = listOf(state.teachers, state.archived, state.locked)
        if (key == renderedRows) return
        renderedRows = key

        binding.llTeachers.removeAllViews()
        state.teachers.orEmpty().forEach { addRow(state, it) }
    }

    private fun addRow(state: PengajarKelasState, teacher: ClassTeacher) {
        val row = ItemPengajarKelasBinding.inflate(layoutInflater, binding.llTeachers, false)
        appContainer.avatarLoader.load(row.ivAvatar, teacher.avatarUrl, R.drawable.img_avatar_guru)
        row.tvNama.text =
            if (teacher.isSelf) getString(R.string.pengajar_anda, teacher.fullName) else teacher.fullName

        val nip = teacher.nip?.takeIf { it.isNotBlank() }
        row.tvNip.isVisible = nip != null
        if (nip != null) row.tvNip.text = getString(R.string.pengajar_nip, nip)

        val joined = TimeFormat.localDate(teacher.joinedAt)
        row.tvBergabung.isVisible = joined != null
        if (joined != null) row.tvBergabung.text = getString(R.string.pengajar_bergabung, joined)

        row.tvPemilik.isVisible = teacher.isOwner

        val actions = state.actionsFor(teacher)
        row.llAksi.isVisible = actions.isNotEmpty()

        row.btnJadikanPemilik.isVisible = TeacherAction.MAKE_OWNER in actions
        row.btnJadikanPemilik.isEnabled = !state.locked
        row.btnJadikanPemilik.contentDescription = getString(R.string.pengajar_aksi_pemilik_cd, teacher.fullName)
        row.btnJadikanPemilik.setOnClickListener { confirmMakeOwner(teacher) }

        row.btnKeluarkan.isVisible = TeacherAction.REMOVE in actions
        row.btnKeluarkan.isEnabled = !state.locked
        row.btnKeluarkan.contentDescription = getString(R.string.pengajar_aksi_keluarkan_cd, teacher.fullName)
        row.btnKeluarkan.setOnClickListener { confirmRemove(teacher) }

        row.btnKeluar.isVisible = TeacherAction.LEAVE in actions
        row.btnKeluar.isEnabled = !state.locked
        row.btnKeluar.setOnClickListener { confirmLeave(teacher) }

        binding.llTeachers.addView(row.root)
    }

    private fun confirmRemove(teacher: ClassTeacher) = confirm(
        title = getString(R.string.pengajar_konfirmasi_keluarkan_judul, teacher.fullName),
        message = getString(R.string.pengajar_konfirmasi_keluarkan_isi, teacher.fullName, className),
        positive = R.string.pengajar_aksi_keluarkan
    ) { viewModel.remove(teacher.id) }

    private fun confirmLeave(teacher: ClassTeacher) {
        val body = getString(R.string.pengajar_konfirmasi_keluar_isi, teacher.fullName, className)
        val successor = viewModel.state.value.successor
        val message = if (successor == null) {
            body
        } else {
            body + "\n\n" + getString(R.string.pengajar_konfirmasi_keluar_penerus, successor.fullName)
        }
        confirm(
            title = getString(R.string.pengajar_konfirmasi_keluar_judul, className),
            message = message,
            positive = R.string.pengajar_aksi_keluar
        ) { viewModel.remove(teacher.id) }
    }

    private fun confirmMakeOwner(teacher: ClassTeacher) = confirm(
        title = getString(R.string.pengajar_konfirmasi_pemilik_judul, teacher.fullName),
        message = getString(R.string.pengajar_konfirmasi_pemilik_isi, teacher.fullName, className),
        positive = R.string.pengajar_aksi_pemilik
    ) { viewModel.makeOwner(teacher.id) }

    private fun confirm(title: String, message: String, @StringRes positive: Int, onConfirm: () -> Unit) {
        confirmDialog?.dismiss()
        confirmDialog = MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(positive) { _, _ -> onConfirm() }
            .setNegativeButton(R.string.batal, null)
            .show()
    }

    /** Dialog tambah guru mengikuti state, jadi tetap terbuka dengan isiannya setelah layar diputar. */
    private fun renderAddDialog(state: PengajarKelasState) {
        val email = state.addEmail
        if (email == null) {
            addDialog?.setOnDismissListener(null)
            addDialog?.dismiss()
            addDialog = null
            addDialogBinding = null
            return
        }
        if (addDialog == null) showAddDialog(email)

        val dialog = addDialog ?: return
        val form = addDialogBinding ?: return
        val waiting = state.addWaitSeconds
        val cooldown = state.cooldownSeconds

        val positive = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
        positive?.isEnabled = !state.locked
        positive?.setText(if (state.adding) R.string.pengajar_dialog_menambah else R.string.pengajar_dialog_tambah)
        // Permintaan yang sedang dikirim tidak bisa dibatalkan, sedangkan menunggu 429 boleh
        val cancellable = !state.adding || waiting != null
        dialog.getButton(DialogInterface.BUTTON_NEGATIVE)?.isEnabled = cancellable
        dialog.setCancelable(cancellable)
        form.etEmail.isEnabled = !state.adding

        val error = state.addError
        form.tvError.isVisible = error != null
        if (error != null) form.tvError.setText(error)

        form.tvTunggu.isVisible = waiting != null || cooldown != null
        if (waiting != null) {
            form.tvTunggu.text = getString(R.string.pengajar_dialog_menunggu, formatWaitSeconds(waiting))
        } else if (cooldown != null) {
            form.tvTunggu.text = getString(R.string.pengajar_tunggu, formatWaitSeconds(cooldown))
        }
    }

    private fun showAddDialog(email: String) {
        val form = DialogTambahPengajarBinding.inflate(layoutInflater)
        form.etEmail.setText(email)
        form.etEmail.doAfterTextChanged { viewModel.setAddEmail(it?.toString().orEmpty()) }
        form.etEmail.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) viewModel.submitAdd()
            actionId == EditorInfo.IME_ACTION_DONE
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.pengajar_dialog_judul)
            .setView(form.root)
            .setPositiveButton(R.string.pengajar_dialog_tambah, null)
            .setNegativeButton(R.string.batal, null)
            .setOnDismissListener {
                addDialog = null
                addDialogBinding = null
                viewModel.closeAddDialog()
            }
            .create()
        dialog.setCanceledOnTouchOutside(false)

        // Tombol dipasang sesudah show supaya dialog tidak menutup sendiri saat isian belum benar
        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener { viewModel.submitAdd() }
        }

        addDialog = dialog
        addDialogBinding = form
        dialog.show()
    }

    companion object {
        private const val EXTRA_CLASS_ID = "CLASS_ID"
        private const val EXTRA_CLASS_NAME = "CLASS_NAME"
        private const val EXTRA_ARCHIVED = "CLASS_ARCHIVED"

        fun intent(context: Context, classId: String, className: String, archived: Boolean): Intent =
            Intent(context, PengajarKelasActivity::class.java)
                .putExtra(EXTRA_CLASS_ID, classId)
                .putExtra(EXTRA_CLASS_NAME, className)
                .putExtra(EXTRA_ARCHIVED, archived)
    }
}
