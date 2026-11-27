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
    fun `fromClassTeachers sorts owner first, dedupes by id, and keeps distinct teachers with equal names`() {
        val raw = listOf(
            ClassTeacher(id = "t2", isSelf = false, fullName = TEACHER_BUDI, isOwner = false),
            ClassTeacher(id = "t3", isSelf = false, fullName = "  ", isOwner = false),
            ClassTeacher(id = "t1", isSelf = true, fullName = " $TEACHER_SITI ", avatarUrl = "https://cdn/siti.png", isOwner = true),
            ClassTeacher(id = "t1", isSelf = false, fullName = TEACHER_SITI, isOwner = false),
            ClassTeacher(id = "t4", isSelf = false, fullName = TEACHER_SITI, isOwner = false)
        )

        val result = ClassTeachers.fromClassTeachers(raw)

        assertEquals(3, result.size)
        assertEquals(TEACHER_SITI, result[0].fullName)
        assertEquals("https://cdn/siti.png", result[0].avatarUrl)
        assertEquals(TEACHER_BUDI, result[1].fullName)
        assertEquals(TEACHER_SITI, result[2].fullName)
    }

    @Test
    fun `fromChatContacts filters active GURU matching target classId`() {
        val contacts = listOf(
            ChatContact(
                id = "u-wali",
                fullName = "Bapak Hendra Gunawan",
                role = "WALI",
                classes = listOf(ChatContactClass(CLASS_MATAHARI_ID, CLASS_MATAHARI_NAME))
            ),
            ChatContact(
                id = "u-other",
                fullName = "Ibu Guru Kelas Lain",
                role = ROLE_GURU,
                classes = listOf(ChatContactClass("c-bulan", "Kelas Bulan"))
            ),
            ChatContact(
                id = "u-deleted",
                fullName = "Akun Dihapus",
                role = ROLE_GURU,
                deleted = true,
                classes = listOf(ChatContactClass(CLASS_MATAHARI_ID, CLASS_MATAHARI_NAME))
            ),
            ChatContact(
                id = "u-guru",
                fullName = TEACHER_SITI,
                role = ROLE_GURU,
                avatarUrl = "https://cdn/siti.jpg",
                classes = listOf(ChatContactClass(CLASS_MATAHARI_ID, CLASS_MATAHARI_NAME))
            ),
            ChatContact(
                id = "u-guru",
                fullName = TEACHER_SITI,
                role = ROLE_GURU,
                classes = listOf(ChatContactClass(CLASS_MATAHARI_ID, CLASS_MATAHARI_NAME))
            ),
            ChatContact(
                id = "u-guru-2",
                fullName = TEACHER_SITI,
                role = ROLE_GURU,
                classes = listOf(ChatContactClass(CLASS_MATAHARI_ID, CLASS_MATAHARI_NAME))
            )
        )

        val result = ClassTeachers.fromChatContacts(contacts, CLASS_MATAHARI_ID)

        assertEquals(2, result.size)
        assertEquals(TEACHER_SITI, result[0].fullName)
        assertEquals("https://cdn/siti.jpg", result[0].avatarUrl)
        assertEquals(TEACHER_SITI, result[1].fullName)
    }

    @Test
    fun `fromChatContacts returns no teachers without a target classId`() {
        val contacts = listOf(
            ChatContact(
                id = "u-guru",
                fullName = TEACHER_SITI,
                role = ROLE_GURU,
                classes = listOf(ChatContactClass(CLASS_MATAHARI_ID, CLASS_MATAHARI_NAME))
            )
        )

        assertEquals(0, ClassTeachers.fromChatContacts(contacts, null).size)
        assertEquals(0, ClassTeachers.fromChatContacts(contacts, "  ").size)
    }

    @Test
    fun `mergeWithSessionFallback uses Guru session user when roster unavailable`() {
        val guru = SessionUser("g1", "siti@kibooz.id", TEACHER_SITI, Role.GURU, "https://cdn/a.png")
        val wali = SessionUser("w1", "hendra@kibooz.id", "Bapak Hendra", Role.WALI, null)

        val fromGuru = ClassTeachers.mergeWithSessionFallback(emptyList(), guru)
        val fromWali = ClassTeachers.mergeWithSessionFallback(emptyList(), wali)

        assertEquals(1, fromGuru.size)
        assertEquals(TEACHER_SITI, fromGuru[0].fullName)
        assertEquals(0, fromWali.size)
    }

    @Test
    fun `format helpers handle zero, one, two, and many teachers cleanly`() {
        assertNull(ClassTeachers.formatHeadlineOrNull(emptyList()))
        assertEquals("Guru Kelas", ClassTeachers.formatHeadline(emptyList()))
        assertEquals("Pengajar: Guru Kelas", ClassTeachers.formatPengajarLine(emptyList()))

        val one = listOf(ClassTeacherSummary(TEACHER_SITI))
        val expectedSingleRole = "Guru Pengajar • $CLASS_MATAHARI_NAME"
        assertEquals(TEACHER_SITI, ClassTeachers.formatHeadline(one))
        assertEquals("Pengajar: $TEACHER_SITI", ClassTeachers.formatPengajarLine(one))
        assertEquals(expectedSingleRole, ClassTeachers.formatSubtitle(one, CLASS_MATAHARI_NAME))
        assertEquals(expectedSingleRole, ClassTeachers.formatSubtitle(one, "Matahari"))

        val two = one + ClassTeacherSummary(TEACHER_BUDI)
        assertEquals("$TEACHER_SITI, $TEACHER_BUDI", ClassTeachers.formatHeadline(two))
        assertEquals("2 Guru Pengajar • $CLASS_MATAHARI_NAME", ClassTeachers.formatSubtitle(two, CLASS_MATAHARI_NAME))

        val three = two + ClassTeacherSummary("Ibu Rina")
        assertEquals("$TEACHER_SITI, +2 pengajar", ClassTeachers.formatHeadline(three))
        assertEquals("3 Guru Pengajar & 12 Siswa Terdaftar", ClassTeachers.formatBannerCount(three.size, "12 Siswa Terdaftar"))
    }

    private companion object {
        const val TEACHER_SITI = "Ibu Siti Aminah, S.Pd"
        const val TEACHER_BUDI = "Pak Budi"
        const val CLASS_MATAHARI_ID = "c-matahari"
        const val CLASS_MATAHARI_NAME = "Kelas Matahari"
        const val ROLE_GURU = "GURU"
    }
}
