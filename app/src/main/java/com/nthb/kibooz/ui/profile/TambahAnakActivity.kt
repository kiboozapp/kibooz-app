package com.nthb.kibooz.ui.profile

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nthb.kibooz.R
import com.nthb.kibooz.appContainer
import com.nthb.kibooz.ui.theme.KiboozTheme
import com.nthb.kibooz.ui.wali.TambahAnakState
import com.nthb.kibooz.ui.wali.TambahAnakViewModel
import kotlinx.coroutines.launch

/**
 * Menambah anak dengan kode kelas dari guru. Wali boleh punya sampai 10 anak, dan tiap anak bisa di kelas yang berbeda.
 * Isi layarnya digambar oleh [TambahAnakScreen]. Kelas ini memegang pesan sekali pakai dan menutup layar setelah berhasil.
 */
class TambahAnakActivity : AppCompatActivity() {

    private val viewModel: TambahAnakViewModel by viewModels {
        TambahAnakViewModel.Factory(appContainer.waliProfileRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KiboozTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                TambahAnakScreen(state = state, onBack = ::finish, onSubmit = viewModel::submit)
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::handleEvents)
            }
        }
    }

    private fun handleEvents(state: TambahAnakState) {
        state.error?.let {
            // Saat server tidak terjangkau, kartu gangguan sudah menjelaskan dan menawarkan coba lagi
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
