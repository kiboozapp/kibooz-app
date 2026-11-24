package com.nthb.kibooz.ui.wali

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.textfield.TextInputLayout
import com.nthb.kibooz.R
import com.nthb.kibooz.appContainer
import com.nthb.kibooz.databinding.ActivityTambahAnakBinding
import com.nthb.kibooz.util.InsetsUtil
import kotlinx.coroutines.launch

/** Menambah anak dengan kode kelas dari guru. Wali boleh punya sampai 10 anak, dan tiap anak bisa di kelas yang berbeda. */
class TambahAnakActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTambahAnakBinding
    private val viewModel: TambahAnakViewModel by viewModels {
        TambahAnakViewModel.Factory(appContainer.waliProfileRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTambahAnakBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetsUtil.applySystemBarInsets(
            root = binding.root,
            headerView = binding.clTopBar
        )

        binding.ivBack.setOnClickListener { finish() }
        binding.btnTambah.setOnClickListener { submit() }
        binding.connectionStatus.onRetry = { submit() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::render)
            }
        }
    }

    private fun submit() {
        viewModel.submit(
            binding.etKodeKelas.text.toString(),
            binding.etNamaAnak.text.toString(),
            binding.etNisn.text.toString()
        )
    }

    private fun render(state: TambahAnakState) {
        binding.btnTambah.isEnabled = !state.loading

        val fields: Map<AddChildField, TextInputLayout> = mapOf(
            AddChildField.CLASS_CODE to binding.tilKodeKelas,
            AddChildField.CHILD_NAME to binding.tilNamaAnak,
            AddChildField.NISN to binding.tilNisn
        )
        fields.forEach { (field, layout) -> layout.error = state.fieldErrors[field]?.let { getString(it) } }

        // Saat server tidak terjangkau, kartu gangguan sudah menjelaskan dan menawarkan coba lagi
        state.outage?.let { binding.connectionStatus.show(it) } ?: binding.connectionStatus.hide()

        state.error?.let {
            if (state.outage == null) Toast.makeText(this, it, Toast.LENGTH_LONG).show()
            viewModel.errorShown()
        }

        if (state.added != null) {
            Toast.makeText(this, R.string.anak_ditambahkan, Toast.LENGTH_SHORT).show()
            setResult(Activity.RESULT_OK)
            finish()
        }
    }
}
