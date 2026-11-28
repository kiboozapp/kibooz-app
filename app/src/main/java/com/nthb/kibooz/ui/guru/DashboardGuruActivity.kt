package com.nthb.kibooz.ui.guru

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nthb.kibooz.R
import com.nthb.kibooz.appContainer
import com.nthb.kibooz.data.remote.GuruDashboard
import com.nthb.kibooz.data.remote.ServiceOutage
import com.nthb.kibooz.ui.camera.CameraScanMoodActivity
import com.nthb.kibooz.ui.chat.ChatListActivity
import com.nthb.kibooz.ui.common.NotificationInboxDialog
import com.nthb.kibooz.ui.components.GuruTab
import com.nthb.kibooz.ui.profile.ProfilGuruActivity
import com.nthb.kibooz.ui.theme.KiboozTheme
import com.nthb.kibooz.ui.wali.LeaderboardActivity
import com.nthb.kibooz.util.TimeFormat
import java.util.Calendar
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class DashboardGuruActivity : AppCompatActivity() {

    private val viewModel: GuruDashboardViewModel by viewModels {
        GuruDashboardViewModel.Factory(
            repository = appContainer.guruRepository,
            classRepository = appContainer.classRepository,
            initialClassId = appContainer.guruClassSelection.current(currentUserId())
        )
    }

    private val snackbarHost = SnackbarHostState()

    // Data terakhir yang diterima. Angka tetap tampil selagi kelas lain dimuat, seperti layar XML sebelumnya
    private var lastDashboard by mutableStateOf<GuruDashboard?>(null)

    // Sisa detik hitung mundur gangguan server. Null selama tidak ada gangguan
    private var outageSecondsLeft by mutableStateOf<Int?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            KiboozTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                val session by appContainer.authRepository.session.collectAsStateWithLifecycle()
                val greeting = remember { greetingRes(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) }
                val date = remember { TimeFormat.compactHomeDate() }
                DashboardGuruScreen(
                    state = state,
                    dashboard = state.dashboard ?: lastDashboard,
                    info = GuruDashboardHeaderInfo(greeting, date, session?.user?.avatarUrl),
                    avatarLoader = appContainer.avatarLoader,
                    outageSecondsLeft = outageSecondsLeft,
                    snackbarHost = snackbarHost,
                    onRetry = { viewModel.load() },
                    onPickClass = ::pickClass,
                    onHistory = ::openHistory,
                    onNotifications = { NotificationInboxDialog.show(this) },
                    onProfile = { startActivity(Intent(this, ProfilGuruActivity::class.java)) },
                    onMoodStats = ::openMoodStats,
                    onScan = ::openScan,
                    onChat = { startActivity(ChatListActivity.intent(this)) },
                    onRanking = ::openRanking,
                    onTab = ::openTab
                )
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect(::handleState) }
                launch { viewModel.state.map { it.outage }.distinctUntilChanged().collectLatest(::runOutageCountdown) }
            }
        }
    }

    /**
     * Hitung mundur sampai percobaan otomatis berikutnya saat server tidak terjangkau (FE-12). Berhenti sendiri
     * saat layar tidak terlihat, dan dimulai lagi dari awal saat layar tampil kembali.
     */
    private suspend fun runOutageCountdown(outage: ServiceOutage?) {
        if (outage == null) {
            outageSecondsLeft = null
            return
        }

        for (secondsLeft in outage.waitSeconds downTo 1) {
            outageSecondsLeft = secondsLeft.toInt()
            delay(1_000)
        }
        viewModel.retryAfterOutage()
    }

    private fun currentUserId(): String? = appContainer.authRepository.session.value?.user?.id

    private fun activeClassId(): String? =
        appContainer.guruClassSelection.current(currentUserId())
            ?: viewModel.state.value.selectedClassId
            ?: viewModel.state.value.dashboard?.classOverview?.classId

    override fun onResume() {
        super.onResume()
        // Kembali dari layar scan, statistik, ranking, atau profil membawa kelas terpilih dan angka terbaru
        val sharedClassId = appContainer.guruClassSelection.current(currentUserId())
        val state = viewModel.state.value
        if (sharedClassId != null && sharedClassId != state.selectedClassId) {
            viewModel.selectClass(sharedClassId)
        } else if (!state.loading && state.dashboard != null) {
            viewModel.load(sharedClassId)
        }
    }

    private fun handleState(state: GuruDashboardState) {
        state.dashboard?.let { lastDashboard = it }
        val userId = currentUserId()
        val resolvedClassId = state.selectedClassId ?: state.dashboard?.classOverview?.classId
        if (resolvedClassId != null && appContainer.guruClassSelection.current(userId) == null) {
            appContainer.guruClassSelection.select(userId, resolvedClassId)
        }

        state.error?.let { message ->
            // Saat server tidak terjangkau, kartu gangguan sudah menjelaskan dan menawarkan coba lagi
            if (state.outage == null) {
                lifecycleScope.launch {
                    // Pesan baru menggantikan yang sedang tampil, seperti Snackbar di layar XML
                    snackbarHost.currentSnackbarData?.dismiss()
                    val result = snackbarHost.showSnackbar(
                        message = getString(message),
                        actionLabel = getString(R.string.coba_lagi),
                        duration = SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.load()
                }
            }
            viewModel.errorShown()
        }
    }

    private fun pickClass() {
        val state = viewModel.state.value
        val userId = currentUserId()
        val resolvedClassId = state.selectedClassId ?: state.dashboard?.classOverview?.classId
        val activeClass = resolveSelectedClass(state.classes, resolvedClassId)
        showGuruClassPickerDialog(this, state.classes, activeClass?.id ?: resolvedClassId) { chosen ->
            appContainer.guruClassSelection.select(userId, chosen.id)
            viewModel.selectClass(chosen.id)
        }
    }

    private fun greetingRes(hour: Int) = when (hour) {
        in 4..10 -> R.string.sapaan_pagi
        in 11..14 -> R.string.sapaan_siang
        in 15..17 -> R.string.sapaan_sore
        else -> R.string.sapaan_malam
    }

    private fun openMoodStats() {
        startActivity(
            Intent(this, StatistikMoodActivity::class.java)
                .putExtra(StatistikMoodActivity.EXTRA_CLASS_ID, activeClassId())
        )
    }

    private fun openScan() {
        startActivity(
            Intent(this, CameraScanMoodActivity::class.java)
                .putExtra(CameraScanMoodActivity.EXTRA_CLASS_ID, activeClassId())
        )
    }

    private fun openRanking() {
        startActivity(
            Intent(this, LeaderboardActivity::class.java)
                .putExtra(LeaderboardActivity.EXTRA_CLASS_ID, activeClassId())
        )
    }

    private fun openHistory() {
        startActivity(
            Intent(this, GuruHistoryActivity::class.java)
                .putExtra(GuruHistoryActivity.EXTRA_CLASS_ID, activeClassId())
                .putExtra(GuruHistoryActivity.EXTRA_CLASS_NAME, viewModel.state.value.dashboard?.classOverview?.className)
        )
    }

    private fun openTab(tab: GuruTab) {
        when (tab) {
            GuruTab.HOME -> Unit
            GuruTab.STATS -> openMoodStats()
            GuruTab.CLASS -> startActivity(Intent(this, RuangKelasGuruActivity::class.java))
            GuruTab.PROFILE -> startActivity(Intent(this, ProfilGuruActivity::class.java))
        }
    }
}
