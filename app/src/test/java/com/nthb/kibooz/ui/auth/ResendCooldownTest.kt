package com.nthb.kibooz.ui.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class ResendCooldownTest {

    private var now = 1_000_000L
    private val cooldown = ResendCooldown { now }

    @Test
    fun `a cooldown that never started is already over`() {
        assertEquals(0, cooldown.secondsLeft())
    }

    @Test
    fun `the seconds left count down from the deadline and round up`() {
        cooldown.start(60)
        assertEquals(60, cooldown.secondsLeft())

        now += 1
        assertEquals(60, cooldown.secondsLeft())

        now += 999
        assertEquals(59, cooldown.secondsLeft())

        now += 58_000
        assertEquals(1, cooldown.secondsLeft())

        now += 1_000
        assertEquals(0, cooldown.secondsLeft())
    }

    @Test
    fun `time that passes while nobody asks still counts`() {
        cooldown.start(60)

        now += 5 * 60_000

        assertEquals(0, cooldown.secondsLeft())
    }

    @Test
    fun `zero and negative waits mean resend is allowed at once`() {
        cooldown.start(0)
        assertEquals(0, cooldown.secondsLeft())

        cooldown.start(-5)
        assertEquals(0, cooldown.secondsLeft())
    }

    @Test
    fun `a wait longer than the hourly limit is capped`() {
        cooldown.start(99_999)

        assertEquals(ResendCooldown.MAX_SECONDS.toInt(), cooldown.secondsLeft())
    }

    @Test
    fun `starting again replaces the old deadline and clear ends it`() {
        cooldown.start(60)
        now += 30_000
        cooldown.start(45)
        assertEquals(45, cooldown.secondsLeft())

        cooldown.clear()
        assertEquals(0, cooldown.secondsLeft())
    }
}
