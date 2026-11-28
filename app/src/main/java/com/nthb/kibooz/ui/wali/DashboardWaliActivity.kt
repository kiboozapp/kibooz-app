package com.nthb.kibooz.ui.wali

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nthb.kibooz.R
import com.nthb.kibooz.appContainer
import com.nthb.kibooz.notification.ScanReminderNotifier
import com.nthb.kibooz.ui.camera.CameraScanSampahActivity
import com.nthb.kibooz.ui.chat.ChatListActivity
import com.nthb.kibooz.ui.common.NotificationInboxDialog
import com.nthb.kibooz.ui.components.WaliTab
import com.nthb.kibooz.ui.profile.ProfilSiswaActivity
import com.nthb.kibooz.ui.profile.ProfilWaliActivity
import com.nthb.kibooz.ui.profile.TambahAnakActivity
import com.nthb.kibooz.ui.theme.KiboozTheme
import com.nthb.kibooz.util.TimeFormat
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class DashboardWaliActivity : AppCompatActivity() {

    private val viewModel: WaliDashboardViewModel by viewModels {
        WaliDashboardViewModel.Factory(appContainer.waliRepository, appContainer.classRepository, appContainer.trashRepository)
    }

    private val snackbarHost = SnackbarHostState()

    // Harus didaftarkan sebelum activity STARTED, jadi sebagai properti dan bukan di dalam klik
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermission()

        setContent {
            KiboozTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                val date = remember { TimeFormat.compactHomeDate() }
                DashboardWaliScreen(
                    state = state,
                    date = date,
                    avatarLoader = appContainer.avatarLoader,
                    snackbarHost = snackbarHost,
                    actions = DashboardWaliActions(
                        onRetry = ::retryLoad,
                        onHistory = ::openHistory,
                        onNotifications = { NotificationInboxDialog.show(this) },
                        onStudentHeader = ::onStudentHeaderClicked,
                        onLeaderboard = ::openLeaderboard,
                        onTrashStats = ::openTrashStats,
                        onGuidance = ::openGuidance,
                        onClassRoom = ::openClassRoom,
                        onChat = { startActivity(ChatListActivity.intent(this)) },
                        onTab = ::openTab,
                        onScan = ::openScan
                    )
                )
            }
        }

        // Hanya saat dibuka pertama kali. Setelah layar diputar, anak yang dipilih sudah ada di ViewModel
        if (savedInstanceState == null) selectRequestedChild(intent)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect(::handleState) }
                launch { viewModel.state.map { it.noChild }.distinctUntilChanged().collectLatest(::showNoChildNotice) }
            }
        }
    }

    /** Dibuka lagi dari layar lain lewat CLEAR_TOP, misalnya dari profil anak yang dipilih Wali. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        selectRequestedChild(intent)
    }

    /** Memakai anak yang diminta layar sebelumnya. Tanpa extra, anak yang sedang tampil tetap dipakai. */
    private fun selectRequestedChild(intent: Intent) {
        val childId = intent.getStringExtra(ProfilSiswaActivity.EXTRA_STUDENT_ID) ?: return
        if (childId != viewModel.state.value.dashboard?.student?.id) viewModel.selectChild(childId)
    }

    override fun onResume() {
        super.onResume()
        // Kembali dari scan sampah atau panduan membawa poin dan status yang baru. Muat pertama yang gagal dicoba lagi
        if (!viewModel.state.value.loading) viewModel.load()
    }

    /** Tombol coba lagi di layar. Pesan galat yang masih tampil dilepas dulu, karena snackbar tanpa batas tidak hilang sendiri. */
    private fun retryLoad() {
        snackbarHost.currentSnackbarData?.dismiss()
        viewModel.load()
    }

    private fun handleState(state: WaliDashboardState) {
        state.error?.let { message ->
            lifecycleScope.launch {
                // Pesan baru menggantikan yang sedang tampil, seperti Snackbar di layar XML
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

    /** Pesan "belum ada anak" tetap tampil selama Wali belum punya anak, dan hilang begitu ada anak. */
    private suspend fun showNoChildNotice(noChild: Boolean) {
        if (!noChild) return
        snackbarHost.currentSnackbarData?.dismiss()
        val result = snackbarHost.showSnackbar(
            message = getString(R.string.error_no_child),
            actionLabel = getString(R.string.anak_tambah_anak),
            duration = SnackbarDuration.Indefinite
        )
        if (result == SnackbarResult.ActionPerformed) startActivity(Intent(this, TambahAnakActivity::class.java))
    }

    /** Izin pengingat sisa scan pukul 12.00 (Android 13 ke atas). Jika ditolak, pengingat dilewati diam-diam. */
    private fun requestNotificationPermission() {
        if (!ScanReminderNotifier.hasPermission(this)) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /**
     * Kelas anak yang sedang ditampilkan dikirim supaya Wali dengan beberapa anak melihat kelas yang benar, dan namanya
     * dikirim supaya barisnya disorot di daftar peringkat.
     */
    private fun openLeaderboard() {
        val student = viewModel.state.value.dashboard?.student
        startActivity(
            Intent(this, LeaderboardActivity::class.java)
                .putExtra(LeaderboardActivity.EXTRA_CLASS_ID, student?.classId)
                .putExtra(LeaderboardActivity.EXTRA_HIGHLIGHT_NAME, student?.fullName)
        )
    }

    /** Anak yang sedang ditampilkan, supaya statistiknya milik anak yang sama. */
    private fun openTrashStats() {
        val studentId = viewModel.state.value.dashboard?.student?.id
        startActivity(Intent(this, StatistikSampahActivity::class.java).putExtra(StatistikSampahActivity.EXTRA_STUDENT_ID, studentId))
    }

    /** Profil dibuka untuk anak yang sedang ditampilkan, supaya pilihan anak ikut terbawa. */
    private fun openProfile() {
        val studentId = viewModel.state.value.dashboard?.student?.id
        startActivity(Intent(this, ProfilWaliActivity::class.java).putExtra(ProfilSiswaActivity.EXTRA_STUDENT_ID, studentId))
    }

    private fun openGuidance() {
        val data = viewModel.state.value.dashboard
        val guidance = data?.recommendedGuidance
        if (data == null || guidance == null) {
            Toast.makeText(this, R.string.wali_belum_ada_panduan, Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(
            Intent(this, DetailPenangananMoodActivity::class.java)
                .putExtra(GuidanceExtras.STUDENT_ID, data.student.id)
                .putExtra(GuidanceExtras.GUIDANCE_ID, guidance.id)
                .putExtra(GuidanceExtras.TITLE, guidance.title)
                .putExtra(GuidanceExtras.CATEGORY, guidance.category)
        )
    }

    private fun openHistory() {
        val studentId = viewModel.state.value.dashboard?.student?.id
        startActivity(
            Intent(this, RiwayatAktivitasWaliActivity::class.java)
                .putExtra(RiwayatAktivitasWaliActivity.EXTRA_STUDENT_ID, studentId)
        )
    }

    private fun openClassRoom() {
        val studentId = viewModel.state.value.dashboard?.student?.id
        startActivity(
            Intent(this, RuangKelasWaliActivity::class.java)
                .putExtra(RuangKelasWaliActivity.EXTRA_STUDENT_ID, studentId)
        )
    }

    private fun openScan() {
        startActivity(
            Intent(this, CameraScanSampahActivity::class.java)
                .putExtra(CameraScanSampahActivity.EXTRA_STUDENT_ID, viewModel.state.value.dashboard?.student?.id)
        )
    }

    private fun openTab(tab: WaliTab) {
        when (tab) {
            WaliTab.HOME -> Unit
            WaliTab.WASTE -> openTrashStats()
            WaliTab.RANKING -> openLeaderboard()
            WaliTab.PROFILE -> openProfile()
        }
    }

    /** Wali dengan beberapa anak memilih anak yang ditampilkan. Dengan satu anak, ketukan membuka profil. */
    private fun onStudentHeaderClicked() {
        val children = viewModel.state.value.children
        if (children.size < 2) {
            openProfile()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.wali_pilih_anak)
            .setItems(children.map { it.fullName }.toTypedArray()) { _, which -> viewModel.selectChild(children[which].id) }
            .setNegativeButton(R.string.batal, null)
            .show()
    }
}
