package com.nthb.kibooz.ui.common

import com.nthb.kibooz.ml.TrashCategory.ANORGANIK
import com.nthb.kibooz.ml.TrashCategory.B3
import com.nthb.kibooz.ml.TrashCategory.ORGANIK
import org.junit.Assert.assertEquals
import org.junit.Test

class TrashChartMathTest {

    @Test
    fun `no scans gives zero for every category`() {
        assertEquals(mapOf(ORGANIK to 0, ANORGANIK to 0, B3 to 0), TrashChartMath.percentages(emptyMap()))
    }

    @Test
    fun `exact shares stay as they are`() {
        assertEquals(
            mapOf(ORGANIK to 60, ANORGANIK to 30, B3 to 10),
            TrashChartMath.percentages(mapOf(ORGANIK to 6, ANORGANIK to 3, B3 to 1))
        )
    }

    @Test
    fun `three equal shares still add up to 100`() {
        val result = TrashChartMath.percentages(mapOf(ORGANIK to 1, ANORGANIK to 1, B3 to 1))

        assertEquals(mapOf(ORGANIK to 34, ANORGANIK to 33, B3 to 33), result)
        assertEquals(100, result.values.sum())
    }

    @Test
    fun `two halves rounding up do not reach 101`() {
        // 12.5 dan 87.5: dibulatkan sendiri-sendiri menjadi 13 + 88 = 101
        assertEquals(
            mapOf(ORGANIK to 13, ANORGANIK to 87, B3 to 0),
            TrashChartMath.percentages(mapOf(ORGANIK to 1, ANORGANIK to 7))
        )
    }

    @Test
    fun `a single category takes the whole circle`() {
        assertEquals(
            mapOf(ORGANIK to 0, ANORGANIK to 0, B3 to 100),
            TrashChartMath.percentages(mapOf(B3 to 4))
        )
    }
}
