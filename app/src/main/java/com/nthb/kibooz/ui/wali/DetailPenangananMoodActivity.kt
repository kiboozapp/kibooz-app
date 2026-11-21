package com.nthb.kibooz.ui.wali

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nthb.kibooz.R
import com.nthb.kibooz.appContainer
import com.nthb.kibooz.databinding.ActivityDetailPenangananMoodBinding
import com.nthb.kibooz.ml.Emotion
import com.nthb.kibooz.ui.common.displayName
import com.nthb.kibooz.util.InsetsUtil
import kotlinx.coroutines.launch

class DetailPenangananMoodActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailPenangananMoodBinding
    private val viewModel: GuidanceApplyViewModel by viewModels { GuidanceApplyViewModel.Factory(appContainer.waliRepository) }

    private val guidanceId: String? get() = intent.getStringExtra(GuidanceExtras.GUIDANCE_ID)
    private val studentId: String? get() = intent.getStringExtra(GuidanceExtras.STUDENT_ID)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailPenangananMoodBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetsUtil.applySystemBarInsets(
            root = binding.root,
            headerView = binding.clTopBar,
            bottomBar = binding.clBottomCta
        )

        setupListeners()
        showGuidanceHeader()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::render)
            }
        }
    }

    /** Judul dan kategori dari panduan yang dipilih server. Kategori yang bernama emosi ditulis dalam bahasa Indonesia. */
    private fun showGuidanceHeader() {
        val category = intent.getStringExtra(GuidanceExtras.CATEGORY)
        val label = category?.let { name -> Emotion.entries.firstOrNull { it.name == name.uppercase() }?.displayName() ?: name }
        binding.tvKategori.text = label?.let { getString(R.string.panduan_kategori, it) } ?: ""
        binding.tvJudulPanduan.text = intent.getStringExtra(GuidanceExtras.TITLE) ?: ""
    }

    private fun render(state: GuidanceApplyState) {
        val button = binding.btnTerapkanPanduan
        button.isEnabled = !state.sending && !state.applied
        button.alpha = if (button.isEnabled) 1f else 0.5f
        button.text = when {
            state.applied -> getString(R.string.panduan_sudah_dilaporkan)
            state.retryInSeconds != null -> getString(R.string.batas_laju_menunggu, state.retryInSeconds)
            else -> getString(R.string.panduan_terapkan_label)
        }

        if (state.applied) Toast.makeText(this, R.string.panduan_terkirim, Toast.LENGTH_LONG).show()

        state.error?.let {
            Toast.makeText(this, it, Toast.LENGTH_LONG).show()
            viewModel.errorShown()
        }
    }

    /** Catatan untuk guru bersifat opsional. Kosong boleh, lebih dari 500 karakter ditolak sebelum dikirim. */
    private fun askNotesThenApply() {
        val input = EditText(this).apply {
            hint = getString(R.string.panduan_catatan_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = 3
            setPadding(48, 24, 48, 24)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.panduan_catatan_judul)
            .setView(input)
            .setPositiveButton(R.string.panduan_kirim) { _, _ -> viewModel.apply(guidanceId, studentId, input.text.toString()) }
            .setNegativeButton(R.string.batal, null)
            .show()
    }

    private fun setupListeners() {
        binding.ivBack.setOnClickListener {
            finish()
        }

        binding.ivShare.setOnClickListener {
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Panduan Penanganan Emosi Kibooz: Berikan rasa aman pada anak dengan 4 langkah praktis."
                )
                type = "text/plain"
            }
            startActivity(Intent.createChooser(shareIntent, "Bagikan Panduan Emosi"))
        }

        binding.btnTerapkanPanduan.setOnClickListener {
            when {
                guidanceId.isNullOrBlank() || studentId.isNullOrBlank() ->
                    Toast.makeText(this, R.string.panduan_tidak_ada, Toast.LENGTH_LONG).show()
                // Setelah gagal yang boleh diulang, laporan yang sama dikirim lagi tanpa bertanya catatan lagi
                viewModel.state.value.canRetry -> viewModel.apply(guidanceId, studentId, "")
                else -> askNotesThenApply()
            }
        }
    }
}
