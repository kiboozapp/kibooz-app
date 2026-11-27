package com.nthb.kibooz.ui.guru

import com.nthb.kibooz.util.ForumFilter
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
    fun `forum subtitle describes active segment inside the class`() {
        assertEquals(
            "Semua Topik Kelas \u2022 12 Siswa \u2022 2 Guru Pengajar",
            ForumSubjects.subtitle(ForumFilter.All, 12, 2)
        )
        assertEquals(
            "Umum Kelas \u2022 12 Siswa \u2022 2 Guru Pengajar",
            ForumSubjects.subtitle(ForumFilter.General, 12, 2)
        )
        assertEquals(
            "Mapel ${ForumSubject.SENI.displayName} \u2022 12 Siswa \u2022 2 Guru Pengajar",
            ForumSubjects.subtitle(ForumFilter.Subject(ForumSubject.SENI.code), 12, 2)
        )
    }
}
