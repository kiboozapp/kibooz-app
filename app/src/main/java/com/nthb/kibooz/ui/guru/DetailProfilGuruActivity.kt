package com.nthb.kibooz.ui.guru

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.nthb.kibooz.R
import com.nthb.kibooz.appContainer
import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.ClassSummary
import com.nthb.kibooz.data.remote.DailyMoodDistribution
import com.nthb.kibooz.databinding.ActivityDetailProfilGuruBinding
import com.nthb.kibooz.ml.Emotion
import com.nthb.kibooz.ui.common.MoodChartMath
import com.nthb.kibooz.ui.profile.EditProfilGuruActivity
import com.nthb.kibooz.util.InsetsUtil
import com.nthb.kibooz.util.TimeFormat
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class DetailProfilGuruActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailProfilGuruBinding
    private var statsJob: Job? = null
    private var cachedClasses: List<ClassSummary> = emptyList()
    private var hasRenderedStats = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailProfilGuruBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetsUtil.applySystemBarInsets(binding.root, headerView = binding.clTopBar)

        binding.ivBack.setOnClickListener {
            finish()
        }

        binding.ivActionEdit.setOnClickListener {
            startActivity(Intent(this, EditProfilGuruActivity::class.java))
        }

        binding.btnEditProfilGuru.setOnClickListener {
            startActivity(Intent(this, EditProfilGuruActivity::class.java))
        }

        bindProfile()
        loadPerformanceStats()
    }

    override fun onResume() {
        super.onResume()
        bindProfile()
        loadPerformanceStats()
    }

    private fun bindProfile() {
        val sessionUser = appContainer.authRepository.session.value?.user
        if (sessionUser != null) {
            if (!sessionUser.fullName.isNullOrBlank()) binding.tvNamaGuru.text = sessionUser.fullName
            if (!sessionUser.email.isNullOrBlank()) binding.tvEmailValue.text = sessionUser.email
            appContainer.avatarLoader.load(binding.ivAvatarGuru, sessionUser.avatarUrl, R.drawable.img_avatar_guru)
        }

        lifecycleScope.launch {
            if (sessionUser == null) {
                val stored = appContainer.sessionStore.readSession()?.user
                if (stored != null) {
                    if (!stored.fullName.isNullOrBlank()) binding.tvNamaGuru.text = stored.fullName
                    if (!stored.email.isNullOrBlank()) binding.tvEmailValue.text = stored.email
                    appContainer.avatarLoader.load(binding.ivAvatarGuru, stored.avatarUrl, R.drawable.img_avatar_guru)
                }
            }

            when (val res = appContainer.guruProfileRepository.profileDetail()) {
                is ApiResult.Success -> {
                    val d = res.data
                    binding.tvNamaGuru.text = d.fullName
                    binding.tvNipValue.text = d.nip?.takeIf { it.isNotBlank() } ?: "-"
                    binding.tvEmailValue.text = d.email
                    binding.tvPhoneValue.text = d.phoneNumber?.takeIf { it.isNotBlank() } ?: "-"
                    binding.tvAddressValue.text = d.address?.takeIf { it.isNotBlank() } ?: "-"
                    val joinDate = TimeFormat.localDate(d.joinedAt)
                    binding.tvJoinDateValue.text = if (joinDate != null) "Bergabung sejak $joinDate" else d.schoolName
                    appContainer.avatarLoader.load(binding.ivAvatarGuru, d.avatarUrl, R.drawable.img_avatar_guru)
                }
                else -> Unit
            }
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

            bindClassSwitcher(
                container = binding.llClassSwitcher,
                labelView = binding.tvSelectedClass,
                actionView = binding.tvSwitchClassBadge,
                classes = cachedClasses,
                selectedClassId = targetId,
                compactLabel = true
            ) { chosen ->
                appContainer.guruClassSelection.select(userId, chosen.id)
                loadPerformanceStats(chosen.id)
            }

            val result = appContainer.guruRepository.dashboard(targetId)
            if (result is ApiResult.Success) {
                val overview = result.data.classOverview
                val distribution = result.data.dailyMoodDistribution
                if (appContainer.guruClassSelection.current(userId) == null) {
                    appContainer.guruClassSelection.select(userId, overview.classId)
                }
                bindClassSwitcher(
                    container = binding.llClassSwitcher,
                    labelView = binding.tvSelectedClass,
                    actionView = binding.tvSwitchClassBadge,
                    classes = cachedClasses,
                    selectedClassId = targetId ?: overview.classId,
                    fallbackClassName = overview.className,
                    compactLabel = true
                ) { chosen ->
                    appContainer.guruClassSelection.select(userId, chosen.id)
                    loadPerformanceStats(chosen.id)
                }
                binding.tvPerformanceClassNote.isVisible = distribution.total == 0
                if (distribution.total == 0) {
                    binding.tvPerformanceClassNote.text =
                        getString(R.string.detail_profil_belum_ada_mood, overview.className)
                }
                bindDistribution(distribution)
                hasRenderedStats = true
            } else if (!hasRenderedStats) {
                binding.tvPerformanceClassNote.isVisible = false
                bindDistribution(DailyMoodDistribution(senang = 0, bingung = 0, sedih = 0, marah = 0))
            }
        }
    }

    private fun bindDistribution(distribution: DailyMoodDistribution) {
        binding.donutMoodChart.setDistribution(distribution)
        val percentages = MoodChartMath.percentages(distribution)
        binding.tvLegendHappy.text = "🟡 Happy ${percentages.getValue(Emotion.SENANG).roundToInt()}%"
        binding.tvLegendBingung.text = "🟠 Bingung ${percentages.getValue(Emotion.BINGUNG).roundToInt()}%"
        binding.tvLegendSedih.text = "🔵 Sedih ${percentages.getValue(Emotion.SEDIH).roundToInt()}%"
        binding.tvLegendMarah.text = "🔴 Marah ${percentages.getValue(Emotion.MARAH).roundToInt()}%"
    }
}
