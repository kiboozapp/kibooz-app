package com.nthb.kibooz.ui.common

import com.nthb.kibooz.data.remote.DailyMoodDistribution
import com.nthb.kibooz.ml.Emotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoodChartMathTest {

    @Test
    fun `percentages add up to 100 and follow the counts`() {
        val result = MoodChartMath.percentages(DailyMoodDistribution(senang = 10, sedih = 5, marah = 0, bingung = 5))

        assertEquals(50f, result.getValue(Emotion.SENANG), 0.001f)
        assertEquals(25f, result.getValue(Emotion.SEDIH), 0.001f)
        assertEquals(0f, result.getValue(Emotion.MARAH), 0.001f)
        assertEquals(25f, result.getValue(Emotion.BINGUNG), 0.001f)
        assertEquals(100f, result.values.sum(), 0.001f)
    }

    @Test
    fun `no records gives all zero instead of dividing by zero`() {
        val result = MoodChartMath.percentages(DailyMoodDistribution(0, 0, 0, 0))

        assertTrue(result.values.all { it == 0f })
        assertEquals(4, result.size)
    }

    @Test
    fun `slices run clockwise from the top in the fixed order`() {
        val result = MoodChartMath.percentages(DailyMoodDistribution(senang = 50, sedih = 0, marah = 25, bingung = 25))

        // Senang 0..180, Bingung 180..270, Sedih empty, Marah 270..360
        assertEquals(Emotion.SENANG, MoodChartMath.sliceAt(10f, result))
        assertEquals(Emotion.BINGUNG, MoodChartMath.sliceAt(200f, result))
        assertEquals(Emotion.MARAH, MoodChartMath.sliceAt(300f, result))
        assertNull(MoodChartMath.sliceAt(359.99f, MoodChartMath.percentages(DailyMoodDistribution(0, 0, 0, 0))))
    }

    @Test
    fun `touch angle is measured clockwise from the top`() {
        assertEquals(0f, MoodChartMath.angleFromTop(0f, -10f), 0.001f) // straight up
        assertEquals(90f, MoodChartMath.angleFromTop(10f, 0f), 0.001f) // right
        assertEquals(180f, MoodChartMath.angleFromTop(0f, 10f), 0.001f) // down
        assertEquals(270f, MoodChartMath.angleFromTop(-10f, 0f), 0.001f) // left
    }

    @Test
    fun `weekday names from the API become short Indonesian labels`() {
        assertEquals("Sen", MoodChartMath.dayLabel("Monday"))
        assertEquals("Jum", MoodChartMath.dayLabel("Friday"))
        assertEquals("Saturday", MoodChartMath.dayLabel("Saturday"))
    }

    @Test
    fun `bar fraction is relative to the tallest bar and safe with no data`() {
        assertEquals(1f, MoodChartMath.barFraction(8, 8), 0.001f)
        assertEquals(0.5f, MoodChartMath.barFraction(4, 8), 0.001f)
        assertEquals(0f, MoodChartMath.barFraction(0, 0), 0.001f)
        assertEquals(0f, MoodChartMath.barFraction(5, 0), 0.001f)
    }
}
