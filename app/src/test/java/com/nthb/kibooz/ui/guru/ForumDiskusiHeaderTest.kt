package com.nthb.kibooz.ui.guru

import org.junit.Assert.assertEquals
import org.junit.Test

class ForumDiskusiHeaderTest {

    @Test
    fun `forum header uses class name directly instead of subject name`() {
        assertEquals("Kelas Matahari", formatForumHeaderTitle("Kelas Matahari"))
        assertEquals("Kelas SakuraTK-A", formatForumHeaderTitle("  Kelas SakuraTK-A  "))
    }

    @Test
    fun `forum header falls back to neutral class forum title when class name is absent`() {
        assertEquals("Forum Diskusi Kelas", formatForumHeaderTitle(null))
        assertEquals("Forum Diskusi Kelas", formatForumHeaderTitle("   "))
    }
}
