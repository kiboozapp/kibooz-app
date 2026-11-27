package com.nthb.kibooz.ui.profile

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.nthb.kibooz.R
import com.nthb.kibooz.appContainer
import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.ClassSummary
import com.nthb.kibooz.data.remote.DailyMoodDistribution
import com.nthb.kibooz.ui.guru.EditProfilGuruActivity
import com.nthb.kibooz.ui.guru.loadActiveTeacherClasses
import com.nthb.kibooz.ui.guru.resolveSelectedClass
import com.nthb.kibooz.ui.guru.showGuruClassPickerDialog
import com.nthb.kibooz.ui.theme.KiboozTheme
import com.nthb.kibooz.util.TimeFormat
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class DetailProfilGuruActivity : AppCompatActivity() {

    private var detail by mutableStateOf(GuruDetail())
    private var performance by mutableStateOf(GuruPerformance())
    private var statsJob: Job? = null
    private var profileJob: Job? = null
    private var cachedClasses: List<ClassSummary> = emptyList()
    private var hasRenderedStats = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KiboozTheme {
                DetailProfilGuruScreen(
                    detail = detail,
                    performance = performance,
                    avatarLoader = appContainer.avatarLoader,
                    onBack = ::finish,
                    onEdit = { startActivity(Intent(this, EditProfilGuruActivity::class.java)) },
                    onSwitchClass = ::pickClass
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Layar edit bisa mengganti foto di URL yang sama, jadi foto dimuat ulang setiap kembali ke layar ini
        detail = detail.copy(avatarVersion = detail.avatarVersion + 1)
        bindProfile()
        loadPerformanceStats()
    }

    private fun bindProfile() {
        val sessionUser = appContainer.authRepository.session.value?.user
        if (sessionUser != null) {
            detail = detail.copy(
                name = sessionUser.fullName.takeUnless { it.isNullOrBlank() } ?: detail.name,
                email = sessionUser.email.takeUnless { it.isNullOrBlank() } ?: detail.email,
                avatarUrl = sessionUser.avatarUrl
            )
        }

        profileJob?.cancel()
        profileJob = lifecycleScope.launch {
            if (sessionUser == null) {
                val stored = appContainer.sessionStore.readSession()?.user
                if (stored != null) {
                    detail = detail.copy(
                        name = stored.fullName.takeUnless { it.isNullOrBlank() } ?: detail.name,
                        email = stored.email.takeUnless { it.isNullOrBlank() } ?: detail.email,
                        avatarUrl = stored.avatarUrl
                    )
                }
            }

            when (val res = appContainer.guruProfileRepository.profileDetail()) {
                is ApiResult.Success -> {
                    val d = res.data
                    val joinDate = TimeFormat.localDate(d.joinedAt)
                    detail = detail.copy(
                        name = d.fullName,
                        nip = d.nip?.takeIf { it.isNotBlank() } ?: "-",
                        email = d.email,
                        phone = d.phoneNumber?.takeIf { it.isNotBlank() } ?: "-",
                        address = d.address?.takeIf { it.isNotBlank() } ?: "-",
                        joinLine = if (joinDate != null) "Bergabung sejak $joinDate" else d.schoolName,
                        avatarUrl = d.avatarUrl
                    )
                }
                else -> Unit
            }
        }
    }

    private fun pickClass() {
        val userId = appContainer.authRepository.session.value?.user?.id
        val state = performance
        val activeId = resolveSelectedClass(state.classes, state.selectedClassId)?.id ?: state.selectedClassId
        showGuruClassPickerDialog(this, state.classes, activeId) { chosen ->
            appContainer.guruClassSelection.select(userId, chosen.id)
            loadPerformanceStats(chosen.id)
        }
    }

    private fun loadPerformanceStats(explicitClassId: String? = null) {
        statsJob?.cancel()
        statsJob = lifecycleScope.launch {
            val userId = appContainer.authRepository.session.value?.user?.id
            val fetchedClasses = loadActiveTeacherClasses(appContainer.classRepository)
            if (fetchedClasses.isNotEmpty()) cachedClasses = fetchedClasses

            val desiredId = explicitClassId ?: appContainer.guruClassSelection.current(userId)
            val resolvedClass = resolveSelectedClass(cachedClasses, desiredId)
            val targetId = resolvedClass?.id ?: desiredId
            if (targetId != null && (explicitClassId != null || appContainer.guruClassSelection.current(userId) == null)) {
                appContainer.guruClassSelection.select(userId, targetId)
            }
            // Grafik dan catatan milik kelas sebelumnya tidak boleh tampil di bawah label kelas yang baru
            val changedClass = performance.selectedClassId != null && performance.selectedClassId != targetId
            performance = if (changedClass) {
                GuruPerformance(classes = cachedClasses, selectedClassId = targetId)
            } else {
                performance.copy(classes = cachedClasses, selectedClassId = targetId)
            }

            val result = appContainer.guruRepository.dashboard(targetId)
            if (result is ApiResult.Success) {
                val overview = result.data.classOverview
                val distribution = result.data.dailyMoodDistribution
                if (appContainer.guruClassSelection.current(userId) == null) {
                    appContainer.guruClassSelection.select(userId, overview.classId)
                }
                performance = GuruPerformance(
                    classes = cachedClasses,
                    selectedClassId = targetId ?: overview.classId,
                    fallbackClassName = overview.className,
                    note = if (distribution.total == 0) getString(R.string.detail_profil_belum_ada_mood, overview.className) else null,
                    distribution = distribution
                )
                hasRenderedStats = true
            } else if (!hasRenderedStats) {
                performance = performance.copy(
                    note = null,
                    distribution = DailyMoodDistribution(senang = 0, bingung = 0, sedih = 0, marah = 0)
                )
            }
        }
    }
}
