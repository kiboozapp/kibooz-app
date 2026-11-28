package com.nthb.kibooz.ui.wali

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nthb.kibooz.R
import com.nthb.kibooz.appContainer
import com.nthb.kibooz.ui.camera.CameraScanSampahActivity
import com.nthb.kibooz.ui.components.WaliTab
import com.nthb.kibooz.ui.profile.ProfilSiswaActivity
import com.nthb.kibooz.ui.theme.KiboozTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Statistik pilah sampah seorang anak untuk semester yang berjalan. API tidak punya filter harian atau mingguan. */
class StatistikSampahActivity : AppCompatActivity() {

    private val viewModel: TrashStatsViewModel by viewModels {
        TrashStatsViewModel.Factory(appContainer.trashRepository, intent.getStringExtra(EXTRA_STUDENT_ID))
    }

    private val snackbarHost = SnackbarHostState()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            KiboozTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                StatistikSampahScreen(
                    state = state,
                    snackbarHost = snackbarHost,
                    onBack = ::finish,
                    onTab = ::openTab,
                    onScan = ::openScan
                )
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect(::handleError) }
                launch { viewModel.state.map { it.noChild }.distinctUntilChanged().collectLatest(::showNoChildNotice) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Kembali dari pemindai membawa pindaian dan poin yang baru
        if (!viewModel.state.value.loading) viewModel.load()
    }

    /** Galat memuat tampil sampai ditutup, dengan tombol coba lagi. Pesan baru menggantikan yang sedang tampil. */
    private fun handleError(state: TrashStatsState) {
        state.error?.let { message ->
            lifecycleScope.launch {
                snackbarHost.currentSnackbarData?.dismiss()
                val result = snackbarHost.showSnackbar(
                    message = getString(message),
                    actionLabel = getString(R.string.coba_lagi),
                    duration = SnackbarDuration.Indefinite
                )
                if (result == SnackbarResult.ActionPerformed) viewModel.load()
            }
            viewModel.errorShown()
        }
    }

    /** Pesan "belum ada anak" tampil selama Wali belum punya anak. Seperti layar XML, pesan ini tidak punya tombol. */
    private suspend fun showNoChildNotice(noChild: Boolean) {
        if (!noChild) return
        snackbarHost.currentSnackbarData?.dismiss()
        snackbarHost.showSnackbar(message = getString(R.string.error_no_child), duration = SnackbarDuration.Indefinite)
    }

    private fun openTab(tab: WaliTab) {
        when (tab) {
            WaliTab.HOME -> finish()
            // Tab yang sedang terbuka
            WaliTab.WASTE -> Unit
            WaliTab.RANKING -> {
                startActivity(Intent(this, LeaderboardActivity::class.java))
                finish()
            }
            WaliTab.PROFILE -> {
                startActivity(Intent(this, ProfilSiswaActivity::class.java))
                finish()
            }
        }
    }

    private fun openScan() {
        startActivity(
            Intent(this, CameraScanSampahActivity::class.java)
                .putExtra(CameraScanSampahActivity.EXTRA_STUDENT_ID, intent.getStringExtra(EXTRA_STUDENT_ID))
        )
    }

    companion object {
        /** Anak yang ditampilkan. Tanpa extra ini server memakai anak yang paling lama terdaftar. */
        const val EXTRA_STUDENT_ID = "STUDENT_ID"
    }
}
