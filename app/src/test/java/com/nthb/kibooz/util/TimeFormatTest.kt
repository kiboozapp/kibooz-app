package com.nthb.kibooz.util

import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimeFormatTest {

    private val jakarta = TimeZone.getTimeZone("Asia/Jakarta")

    @Test
    fun `a UTC time is shown in the zone of the user`() {
        assertEquals("14:30", TimeFormat.localTime("2026-11-03T07:30:00Z", jakarta))
    }

    @Test
    fun `an offset time and fractional seconds are understood`() {
        assertEquals("07:30", TimeFormat.localTime("2026-11-03T07:30:15.123456+07:00", jakarta))
        assertEquals("07:30", TimeFormat.localTime("2026-11-03T00:30:15.5Z", jakarta))
    }

    @Test
    fun `a time that cannot be read gives null instead of a crash`() {
        assertNull(TimeFormat.localTime("kemarin", jakarta))
        assertNull(TimeFormat.localTime("", jakarta))
    }
}
