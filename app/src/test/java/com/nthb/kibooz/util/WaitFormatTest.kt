package com.nthb.kibooz.util

import org.junit.Assert.assertEquals
import org.junit.Test

class WaitFormatTest {

    @Test
    fun `under a minute is written in seconds`() {
        assertEquals("1 detik", formatWaitSeconds(1))
        assertEquals("30 detik", formatWaitSeconds(30))
        assertEquals("59 detik", formatWaitSeconds(59))
    }

    @Test
    fun `whole minutes drop the seconds`() {
        assertEquals("1 menit", formatWaitSeconds(60))
        assertEquals("5 menit", formatWaitSeconds(300))
    }

    @Test
    fun `minutes and seconds are both written`() {
        assertEquals("1 menit 30 detik", formatWaitSeconds(90))
        assertEquals("4 menit 1 detik", formatWaitSeconds(241))
    }

    @Test
    fun `zero and negative waits are written as zero seconds`() {
        assertEquals("0 detik", formatWaitSeconds(0))
        assertEquals("0 detik", formatWaitSeconds(-5))
    }
}
