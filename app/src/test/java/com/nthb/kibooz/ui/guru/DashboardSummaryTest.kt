package com.nthb.kibooz.ui.guru

import com.nthb.kibooz.R
import com.nthb.kibooz.data.remote.ClassOverview
import com.nthb.kibooz.data.remote.DailyMoodDistribution
import com.nthb.kibooz.data.remote.DashboardTeacher
import com.nthb.kibooz.data.remote.GuruDashboard
import com.nthb.kibooz.ml.Emotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DashboardSummaryTest {

    private fun dashboard(
        total: Int = 24,
        present: Int = 22,
        dominant: Emotion? = Emotion.SENANG,
        mood: DailyMoodDistribution = DailyMoodDistribution(senang = 14, sedih = 3, marah = 1, bingung = 4)
    ) = GuruDashboard(
        teacher = DashboardTeacher("Siti Rahayu, S.Pd.", null, null),
        classOverview = ClassOverview("c1", "Class A (Mawar)", "KBZ48R2M", true, total, present, dominant),
        dailyMoodDistribution = mood
    )

    @Test
    fun `attendance is the share of present students rounded to a whole percent`() {
        assertEquals(92, dashboard(total = 24, present = 22).toSummary().attendancePercent)
        assertEquals(67, dashboard(total = 3, present = 2).toSummary().attendancePercent)
        assertEquals(100, dashboard(total = 24, present = 24).toSummary().attendancePercent)
        assertEquals(0, dashboard(total = 24, present = 0).toSummary().attendancePercent)
    }

    @Test
    fun `attendance stays within 0 to 100 if the server counts more present than enrolled`() {
        assertEquals(100, dashboard(total = 20, present = 25).toSummary().attendancePercent)
    }

    @Test
    fun `a class without students has no attendance percent`() {
        val summary = dashboard(total = 0, present = 0, dominant = null, mood = DailyMoodDistribution(0, 0, 0, 0)).toSummary()

        assertNull(summary.attendancePercent)
        assertEquals(0, summary.totalStudents)
    }

    @Test
    fun `counts the students whose mood is recorded`() {
        val summary = dashboard().toSummary()

        assertEquals(22, summary.recordedMoods)
        assertEquals(14, summary.moodCounts[Emotion.SENANG])
        assertEquals(3, summary.moodCounts[Emotion.SEDIH])
        assertEquals(Emotion.SENANG, summary.dominantMood)
    }

    @Test
    fun `mood percent is the share of the recorded moods`() {
        val summary = dashboard(mood = DailyMoodDistribution(senang = 3, sedih = 1, marah = 0, bingung = 0)).toSummary()

        assertEquals(75f, summary.moodPercent(Emotion.SENANG), 0.001f)
        assertEquals(25f, summary.moodPercent(Emotion.SEDIH), 0.001f)
        assertEquals(0f, summary.moodPercent(Emotion.MARAH), 0.001f)
    }

    @Test
    fun `mood percent is zero while no mood was recorded`() {
        val summary = dashboard(dominant = null, mood = DailyMoodDistribution(0, 0, 0, 0)).toSummary()

        assertNull(summary.dominantMood)
        assertEquals(0f, summary.moodPercent(Emotion.SENANG), 0.001f)
    }

    @Test
    fun `greeting follows the hour of the day`() {
        assertEquals(R.string.sapaan_malam, greetingFor(3))
        assertEquals(R.string.sapaan_pagi, greetingFor(4))
        assertEquals(R.string.sapaan_pagi, greetingFor(10))
        assertEquals(R.string.sapaan_siang, greetingFor(11))
        assertEquals(R.string.sapaan_siang, greetingFor(14))
        assertEquals(R.string.sapaan_sore, greetingFor(15))
        assertEquals(R.string.sapaan_sore, greetingFor(17))
        assertEquals(R.string.sapaan_malam, greetingFor(18))
        assertEquals(R.string.sapaan_malam, greetingFor(23))
    }
}
