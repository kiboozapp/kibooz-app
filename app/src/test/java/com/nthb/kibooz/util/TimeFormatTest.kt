package com.nthb.kibooz.util

import java.util.Date
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        assertNull(TimeFormat.localDate("kemarin", jakarta))
        assertNull(TimeFormat.localDateTime("kemarin", jakarta))
    }

    @Test
    fun `localDate and localDateTime convert UTC ISO timestamps to Indonesian date strings`() {
        val date = TimeFormat.localDate("2026-11-25T02:00:00Z", jakarta)
        val dateTime = TimeFormat.localDateTime("2026-11-25T00:05:30.45Z", jakarta)
        assertEquals("25 November 2026", date)
        assertEquals("25 Nov 2026, 07:05", dateTime)
    }

    @Test
    fun `compactHomeDate renders compact Indonesian weekday and date for home headers`() {
        val formatted = TimeFormat.compactHomeDate(Date(1795572000000L), jakarta)
        assertTrue(formatted.contains("25") && formatted.contains("Nov") && formatted.contains("2026"))
        assertTrue(formatted.length <= 18)
    }
}
