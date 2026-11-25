package com.nthb.kibooz.notification

import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class ScanReminderSchedulerTest {

    private fun at(hour: Int, minute: Int = 0): Calendar =
        Calendar.getInstance(TimeZone.getTimeZone("Asia/Jakarta")).apply {
            clear()
            set(2026, Calendar.NOVEMBER, 25, hour, minute, 0)
        }

    @Test
    fun `before noon waits until noon today`() {
        assertEquals(TimeUnit.HOURS.toMillis(1), ScanReminderScheduler.delayUntilNoon(at(11)))
        assertEquals(TimeUnit.MINUTES.toMillis(1), ScanReminderScheduler.delayUntilNoon(at(11, 59)))
    }

    @Test
    fun `exactly noon waits until noon tomorrow`() {
        assertEquals(TimeUnit.HOURS.toMillis(24), ScanReminderScheduler.delayUntilNoon(at(12)))
    }

    @Test
    fun `after noon waits until noon tomorrow`() {
        assertEquals(TimeUnit.HOURS.toMillis(23), ScanReminderScheduler.delayUntilNoon(at(13)))
        assertEquals(TimeUnit.HOURS.toMillis(12), ScanReminderScheduler.delayUntilNoon(at(0)))
    }
}
