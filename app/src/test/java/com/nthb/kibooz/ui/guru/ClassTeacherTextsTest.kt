package com.nthb.kibooz.ui.guru

import com.nthb.kibooz.R
import org.junit.Assert.assertEquals
import org.junit.Test

class ClassTeacherTextsTest {

    private val strings = object : TeacherStrings {
        override fun get(id: Int): String = when (id) {
            R.string.class_teacher_default_name -> DEFAULT_NAME
            R.string.class_teacher_role -> ROLE
            else -> error("unexpected string $id")
        }

        override fun get(id: Int, first: Any): String = when (id) {
            R.string.class_teacher_role_count -> "$first $ROLE"
            R.string.class_teacher_line -> "Pengajar: $first"
            else -> error("unexpected string $id")
        }

        override fun get(id: Int, first: Any, second: Any): String = when (id) {
            R.string.class_teacher_pair -> "$first, $second"
            R.string.class_teacher_more -> "$first, +$second pengajar"
            R.string.class_teacher_subtitle_class -> "$first \u2022 $second"
            R.string.class_teacher_banner_count -> "$first $ROLE & $second"
            else -> error("unexpected string $id")
        }
    }

    private val texts = ClassTeacherTexts(strings)

    @Test
    fun `headline covers zero, one, two and many teachers`() {
        assertEquals(DEFAULT_NAME, texts.headline(emptyList()))
        assertEquals(SITI, texts.headline(listOf(SITI)))
        assertEquals("$SITI, $BUDI", texts.headline(listOf(SITI, BUDI)))
        assertEquals("$SITI, +2 pengajar", texts.headline(listOf(SITI, BUDI, "Ibu Rina")))
    }

    @Test
    fun `pengajar line wraps the headline`() {
        assertEquals("Pengajar: $DEFAULT_NAME", texts.pengajarLine(emptyList()))
        assertEquals("Pengajar: $SITI", texts.pengajarLine(listOf(SITI)))
    }

    @Test
    fun `subtitle counts teachers above one and adds the prefixed class name`() {
        assertEquals(ROLE, texts.subtitle(0, null))
        assertEquals(ROLE, texts.subtitle(1, "  "))
        assertEquals("$ROLE \u2022 $CLASS_NAME", texts.subtitle(1, CLASS_NAME))
        assertEquals("$ROLE \u2022 $CLASS_NAME", texts.subtitle(1, "Matahari"))
        assertEquals("2 $ROLE \u2022 $CLASS_NAME", texts.subtitle(2, CLASS_NAME))
    }

    @Test
    fun `banner count never shows fewer than one teacher`() {
        assertEquals("1 $ROLE & $STUDENTS", texts.bannerCount(0, STUDENTS))
        assertEquals("3 $ROLE & $STUDENTS", texts.bannerCount(3, STUDENTS))
    }

    private companion object {
        const val DEFAULT_NAME = "Guru Kelas"
        const val ROLE = "Guru Pengajar"
        const val CLASS_NAME = "Kelas Matahari"
        const val STUDENTS = "12 Siswa Terdaftar"
        const val SITI = "Ibu Siti Aminah, S.Pd"
        const val BUDI = "Pak Budi"
    }
}
