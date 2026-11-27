package com.nthb.kibooz.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForumSubjectsTest {

    @Test
    fun `resolve matches exact subject code first and ignores case or whitespace`() {
        assertEquals(ForumSubject.LOGIKA, ForumSubjects.resolve(" log-a1 ", ForumSubject.SENI.displayName))
        assertEquals(ForumSubject.LOGIKA.code, ForumSubjects.resolveCode(" log-a1 ", ForumSubject.SENI.displayName))
        assertEquals(ForumSubject.SOSIAL, ForumSubjects.fromCode(ForumSubject.SOSIAL.code))
        assertNull(ForumSubjects.fromCode("INVALID-1"))
    }

    @Test
    fun `resolve falls back to subject display name and leaves rombel names unmapped`() {
        assertEquals(ForumSubject.BAHASA, ForumSubjects.resolve(null, ForumSubject.BAHASA.displayName))
        assertEquals(ForumSubject.MOTORIK, ForumSubjects.resolve("", ForumSubject.MOTORIK.displayName))
        assertNull(ForumSubjects.resolve(null, ROMBEL_MATAHARI))
        assertEquals(ForumFilter.All, ForumSubjects.initialFilter(null, ROMBEL_MATAHARI))
    }

    @Test
    fun `initial filter selects mapel when known and exposes matching query fields`() {
        val seniCode = ForumSubject.SENI.code
        val subjectFilter = ForumSubjects.initialFilter(seniCode, null)
        assertEquals(ForumFilter.Subject(seniCode), subjectFilter)
        assertEquals(seniCode, subjectFilter.subjectCodeQuery)
        assertNull(subjectFilter.generalOnlyQuery)
        assertEquals(seniCode, subjectFilter.postSubjectCode)

        assertNull(ForumFilter.General.subjectCodeQuery)
        assertEquals(true, ForumFilter.General.generalOnlyQuery)
        assertNull(ForumFilter.General.postSubjectCode)

        assertNull(ForumFilter.All.subjectCodeQuery)
        assertNull(ForumFilter.All.generalOnlyQuery)
        assertNull(ForumFilter.All.postSubjectCode)
    }

    @Test
    fun `filterOptions covers All, General, and all six curriculum subjects`() {
        assertEquals(8, ForumSubjects.filterOptions.size)
        assertTrue(ForumSubjects.filterOptions.first() is ForumFilter.All)
        assertTrue(ForumSubjects.filterOptions[1] is ForumFilter.General)
        assertEquals(ForumSubject.SENI.displayName, ForumSubjects.threadBadge(ForumSubject.SENI.code))
        assertEquals(ForumSubjects.GENERAL_BADGE, ForumSubjects.threadBadge(null))
    }

    @Test
    fun `banner and header helpers reflect active subject segmentation cleanly`() {
        val seniCode = ForumSubject.SENI.code
        assertEquals(
            "Diskusi Seni \u2022 $ROMBEL_MATAHARI",
            ForumSubjects.bannerTitle(ROMBEL_MATAHARI, seniCode)
        )
        assertEquals(
            "3 pesan diskusi ${ForumSubject.SENI.displayName}",
            ForumSubjects.bannerSubtitle(3, seniCode)
        )
        assertEquals(
            "Belum ada diskusi ${ForumSubject.BAHASA.displayName}",
            ForumSubjects.bannerSubtitle(0, ForumSubject.BAHASA.code)
        )
        assertEquals(
            "Mapel ${ForumSubject.LOGIKA.displayName} \u2022 10 Siswa \u2022 2 Guru Pengajar",
            ForumSubjects.subtitle(ForumFilter.Subject(ForumSubject.LOGIKA.code), 10, 2)
        )
    }

    private companion object {
        const val ROMBEL_MATAHARI = "Kelas Matahari"
    }
}
