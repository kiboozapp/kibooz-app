package com.nthb.kibooz.util

import com.nthb.kibooz.data.remote.ChatContact
import com.nthb.kibooz.data.remote.ChatContactClass
import com.nthb.kibooz.data.remote.ClassTeacher
import com.nthb.kibooz.data.session.Role
import com.nthb.kibooz.data.session.SessionUser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClassTeachersTest {

    @Test
    fun `fromClassTeachers sorts owner first and removes blanks and duplicates`() {
        val raw = listOf(
            ClassTeacher(id = "t2", isSelf = false, fullName = "Pak Budi", isOwner = false),
            ClassTeacher(id = "t3", isSelf = false, fullName = "  ", isOwner = false),
            ClassTeacher(id = "t1", isSelf = true, fullName = " Ibu Siti Aminah, S.Pd ", avatarUrl = "https://cdn/siti.png", isOwner = true),
            ClassTeacher(id = "t4", isSelf = false, fullName = "ibu siti aminah, s.pd", isOwner = false)
        )

        val result = ClassTeachers.fromClassTeachers(raw)

        assertEquals(2, result.size)
        assertEquals("Ibu Siti Aminah, S.Pd", result[0].fullName)
        assertEquals("https://cdn/siti.png", result[0].avatarUrl)
        assertEquals("Pak Budi", result[1].fullName)
    }

    @Test
    fun `fromChatContacts filters active GURU matching target classId`() {
        val contacts = listOf(
            ChatContact(
                id = "u-wali",
                fullName = "Bapak Hendra Gunawan",
                role = "WALI",
                classes = listOf(ChatContactClass("c-matahari", "Kelas Matahari"))
            ),
            ChatContact(
                id = "u-other",
                fullName = "Ibu Guru Kelas Lain",
                role = "GURU",
                classes = listOf(ChatContactClass("c-bulan", "Kelas Bulan"))
            ),
            ChatContact(
                id = "u-deleted",
                fullName = "Akun Dihapus",
                role = "GURU",
                deleted = true,
                classes = listOf(ChatContactClass("c-matahari", "Kelas Matahari"))
            ),
            ChatContact(
                id = "u-guru",
                fullName = "Ibu Siti Aminah, S.Pd",
                role = "GURU",
                avatarUrl = "https://cdn/siti.jpg",
                classes = listOf(ChatContactClass("c-matahari", "Kelas Matahari"))
            )
        )

        val result = ClassTeachers.fromChatContacts(contacts, "c-matahari")

        assertEquals(1, result.size)
        assertEquals("Ibu Siti Aminah, S.Pd", result[0].fullName)
        assertEquals("https://cdn/siti.jpg", result[0].avatarUrl)
    }

    @Test
    fun `mergeWithSessionFallback uses Guru session user when roster unavailable`() {
        val guru = SessionUser("g1", "siti@kibooz.id", "Ibu Siti Aminah, S.Pd", Role.GURU, "https://cdn/a.png")
        val wali = SessionUser("w1", "hendra@kibooz.id", "Bapak Hendra", Role.WALI, null)

        val fromGuru = ClassTeachers.mergeWithSessionFallback(emptyList(), guru)
        val fromWali = ClassTeachers.mergeWithSessionFallback(emptyList(), wali)

        assertEquals(1, fromGuru.size)
        assertEquals("Ibu Siti Aminah, S.Pd", fromGuru[0].fullName)
        assertEquals(0, fromWali.size)
    }

    @Test
    fun `format helpers handle zero, one, two, and many teachers cleanly`() {
        assertNull(ClassTeachers.formatHeadlineOrNull(emptyList()))
        assertEquals("Guru Kelas", ClassTeachers.formatHeadline(emptyList()))
        assertEquals("Pengajar: Guru Kelas", ClassTeachers.formatPengajarLine(emptyList()))

        val one = listOf(ClassTeacherSummary("Ibu Siti Aminah, S.Pd"))
        assertEquals("Ibu Siti Aminah, S.Pd", ClassTeachers.formatHeadline(one))
        assertEquals("Pengajar: Ibu Siti Aminah, S.Pd", ClassTeachers.formatPengajarLine(one))
        assertEquals("Guru Pengajar • Kelas Matahari", ClassTeachers.formatSubtitle(one, "Kelas Matahari"))
        assertEquals("Guru Pengajar • Kelas Matahari", ClassTeachers.formatSubtitle(one, "Matahari"))

        val two = one + ClassTeacherSummary("Pak Budi")
        assertEquals("Ibu Siti Aminah, S.Pd, Pak Budi", ClassTeachers.formatHeadline(two))
        assertEquals("2 Guru Pengajar • Kelas Matahari", ClassTeachers.formatSubtitle(two, "Kelas Matahari"))

        val three = two + ClassTeacherSummary("Ibu Rina")
        assertEquals("Ibu Siti Aminah, S.Pd, +2 pengajar", ClassTeachers.formatHeadline(three))
        assertEquals("3 Guru Pengajar & 12 Siswa Terdaftar", ClassTeachers.formatBannerCount(three.size, "12 Siswa Terdaftar"))
    }
}
