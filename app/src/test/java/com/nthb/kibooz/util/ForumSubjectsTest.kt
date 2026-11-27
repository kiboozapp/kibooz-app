package com.nthb.kibooz.util

import com.nthb.kibooz.data.remote.ForumAuthor
import com.nthb.kibooz.data.remote.ForumThreadItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun `resolve falls back to subject display name and defaults initialSubject to SENI for rombel names`() {
        assertEquals(ForumSubject.BAHASA, ForumSubjects.resolve(null, ForumSubject.BAHASA.displayName))
        assertEquals(ForumSubject.MOTORIK, ForumSubjects.resolve("", ForumSubject.MOTORIK.displayName))
        assertNull(ForumSubjects.resolve(null, ROMBEL_MATAHARI))
        assertEquals(ForumSubject.SENI, ForumSubjects.initialSubject(null, ROMBEL_MATAHARI))
        assertEquals(ForumSubject.AGAMA, ForumSubjects.initialSubject(ForumSubject.AGAMA.code, null))
    }

    @Test
    fun `filterSubjects contains only the six curriculum subjects without All or General`() {
        assertEquals(6, ForumSubjects.filterSubjects.size)
        assertEquals(ForumSubject.entries, ForumSubjects.filterSubjects)
    }

    @Test
    fun `filterThreadsBySubject isolates subject threads from untagged or cross-subject server pools`() {
        val author = ForumAuthor("u1", "Guru", "GURU", null)
        val untagged = ForumThreadItem("t1", "Diskusi Kelas", "tes", null, author, 0, "2026-11-24T10:00:00Z")
        val seni = ForumThreadItem("t2", "Seni", "kolase", ForumSubject.SENI.code, author, 0, "2026-11-24T11:00:00Z")
        val logika = ForumThreadItem("t3", "Logika", "pola", ForumSubject.LOGIKA.code, author, 0, "2026-11-24T12:00:00Z")
        val pool = listOf(untagged, seni, logika)

        val seniOnly = ForumSubjects.filterThreadsBySubject(pool, ForumSubject.SENI.code)
        assertEquals(listOf(seni), seniOnly)

        val bahasaOnly = ForumSubjects.filterThreadsBySubject(pool, ForumSubject.BAHASA.code)
        assertEquals(0, bahasaOnly.size)

        assertEquals(0, ForumSubjects.resolveFilteredBannerCount(listOf(untagged), 1, ForumSubject.LOGIKA.code))
        assertEquals(5, ForumSubjects.resolveFilteredBannerCount(listOf(seni), 5, ForumSubject.SENI.code))
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
            ForumSubjects.subtitle(ForumSubject.LOGIKA, 10, 2)
        )
    }

    private companion object {
        const val ROMBEL_MATAHARI = "Kelas Matahari"
    }
}
