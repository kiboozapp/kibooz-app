package com.nthb.kibooz.ui.guru

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DashboardMathTest {

    @Test
    fun `attendance is the share of present students rounded to a whole percent`() {
        assertEquals(92, DashboardMath.attendancePercent(present = 22, total = 24))
        assertEquals(67, DashboardMath.attendancePercent(present = 2, total = 3))
        assertEquals(100, DashboardMath.attendancePercent(present = 24, total = 24))
        assertEquals(0, DashboardMath.attendancePercent(present = 0, total = 24))
    }

    @Test
    fun `attendance stays within 0 to 100 if the server counts more present than enrolled`() {
        assertEquals(100, DashboardMath.attendancePercent(present = 25, total = 20))
        assertEquals(0, DashboardMath.attendancePercent(present = -1, total = 20))
    }

    @Test
    fun `a class without students has no attendance percent`() {
        assertNull(DashboardMath.attendancePercent(present = 0, total = 0))
        assertNull(DashboardMath.attendancePercent(present = 3, total = 0))
    }
}
