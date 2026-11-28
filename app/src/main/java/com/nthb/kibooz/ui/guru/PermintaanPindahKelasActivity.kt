package com.nthb.kibooz.ui.guru

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.nthb.kibooz.R
import com.nthb.kibooz.appContainer
import com.nthb.kibooz.data.remote.ClassMoveRequest
import com.nthb.kibooz.databinding.ActivityPermintaanPindahKelasBinding
import com.nthb.kibooz.databinding.ItemPermintaanPindahBinding
import com.nthb.kibooz.util.InsetsUtil
import com.nthb.kibooz.util.TimeFormat
import kotlinx.coroutines.launch

/**
 * Layar persetujuan pengajuan pindah kelas bagi Guru. Menampilkan daftar siswa yang orang tuanya mengajukan
 * pindah masuk ke kelas terkait, beserta aksi Terima (`accept`) dan Tolak (`reject`).
 */
class PermintaanPindahKelasActivity : AppCompatActivity() {

    private val binding by lazy { ActivityPermintaanPindahKelasBinding.inflate(layoutInflater) }
    private var className: String = "Kelas"

    private val viewModel: PermintaanPindahKelasViewModel by viewModels {
        PermintaanPindahKelasViewModel.Factory(
            repository = appContainer.moveRequestRepository,
            classId = intent.getStringExtra(EXTRA_CLASS_ID).orEmpty()
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        InsetsUtil.applySystemBarInsets(
            root = binding.root,
            headerView = binding.clTopBar
        )

        className = intent.getStringExtra(EXTRA_CLASS_NAME) ?: "Kelas"
        binding.tvSubtitle.text = getString(R.string.guru_pindah_subjudul, className)

        binding.ivBack.setOnClickListener { finish() }
        binding.btnCobaLagi.setOnClickListener { viewModel.load() }
        binding.connectionStatus.onRetry = { viewModel.load() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::render)
            }
        }
    }

    private fun render(state: PermintaanPindahState) {
        binding.pbLoading.isVisible = state.loading && state.requests.isEmpty()

        val hasError = state.loadError != null && state.requests.isEmpty()
        if (hasError && state.outage != null) {
            binding.connectionStatus.show(state.outage)
        } else {
            binding.connectionStatus.hide()
        }
        binding.llError.isVisible = hasError && state.outage == null
        state.loadError?.let { binding.tvError.setText(it) }

        binding.llKosong.isVisible = !state.loading && !hasError && state.requests.isEmpty()
        binding.llRequests.isVisible = state.requests.isNotEmpty()

        renderCards(state.requests, state.busyRequestId)

        state.noticeText?.let { msg ->
            Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show()
            viewModel.noticeShown()
        }
        state.errorNoticeRes?.let { res ->
            Snackbar.make(binding.root, res, Snackbar.LENGTH_LONG).show()
            viewModel.noticeShown()
        }

        if (state.changed) {
            setResult(Activity.RESULT_OK)
        }
    }

    private fun renderCards(requests: List<ClassMoveRequest>, busyId: String?) {
        binding.llRequests.removeAllViews()
        val busy = busyId != null
        requests.forEach { req ->
            val card = ItemPermintaanPindahBinding.inflate(layoutInflater, binding.llRequests, false)
            card.tvNamaSiswa.text = req.studentName
            val timeStr = TimeFormat.localDateTime(req.requestedAt) ?: req.requestedAt
            card.tvWaktu.text = getString(R.string.guru_pindah_diajukan, timeStr)

            card.btnTerima.isEnabled = !busy
            card.btnTolak.isEnabled = !busy

            card.btnTerima.setOnClickListener { confirmAccept(req) }
            card.btnTolak.setOnClickListener { confirmReject(req) }
            binding.llRequests.addView(card.root)
        }
    }

    private fun confirmAccept(req: ClassMoveRequest) {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.guru_pindah_konfirmasi_terima_judul, req.studentName))
            .setMessage(getString(R.string.guru_pindah_konfirmasi_terima_isi, req.studentName, className))
            .setPositiveButton(R.string.guru_pindah_terima) { _, _ ->
                viewModel.accept(req, getString(R.string.guru_pindah_diterima, req.studentName))
            }
            .setNegativeButton(R.string.batal, null)
            .show()
    }

    private fun confirmReject(req: ClassMoveRequest) {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.guru_pindah_konfirmasi_tolak_judul, req.studentName))
            .setMessage(getString(R.string.guru_pindah_konfirmasi_tolak_isi, req.studentName))
            .setPositiveButton(R.string.guru_pindah_tolak) { _, _ ->
                viewModel.reject(req, getString(R.string.guru_pindah_ditolak, req.studentName))
            }
            .setNegativeButton(R.string.batal, null)
            .show()
    }

    companion object {
        const val EXTRA_CLASS_ID = "CLASS_ID"
        const val EXTRA_CLASS_NAME = "CLASS_NAME"

        fun intent(context: Context, classId: String, className: String): Intent =
            Intent(context, PermintaanPindahKelasActivity::class.java)
                .putExtra(EXTRA_CLASS_ID, classId)
                .putExtra(EXTRA_CLASS_NAME, className)
    }
}
