package com.nthb.kibooz.ui.guru

import com.nthb.kibooz.util.ForumSubject
import com.nthb.kibooz.util.ForumSubjects
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

    @Test
    fun `forum subtitle describes active curriculum subject inside the class`() {
        assertEquals(
            "Mapel ${ForumSubject.SENI.displayName} \u2022 12 Siswa \u2022 2 Guru Pengajar",
            ForumSubjects.subtitle(ForumSubject.SENI, 12, 2)
        )
        assertEquals(
            "Mapel ${ForumSubject.LOGIKA.displayName} \u2022 8 Siswa \u2022 1 Guru Pengajar",
            ForumSubjects.subtitle(ForumSubject.LOGIKA, 8, 1)
        )
    }
}
