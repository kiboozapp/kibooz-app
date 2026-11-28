package com.nthb.kibooz.ui.guru

import com.nthb.kibooz.R
import org.junit.Assert.assertEquals
import org.junit.Test

class ClassTeacherTextsTest {

    private val texts = ClassTeacherTexts { id, args ->
        val template = when (id) {
            R.string.class_teacher_default_name -> "Guru Kelas"
            R.string.class_teacher_role -> "Guru Pengajar"
            R.string.class_teacher_role_count -> "%1\$d Guru Pengajar"
            R.string.class_teacher_pair -> "%1\$s, %2\$s"
            R.string.class_teacher_more -> "%1\$s, +%2\$d pengajar"
            R.string.class_teacher_line -> "Pengajar: %1\$s"
            R.string.class_teacher_subtitle_class -> "%1\$s • %2\$s"
            R.string.class_teacher_banner_count -> "%1\$d Guru Pengajar & %2\$s"
            else -> error("unexpected string $id")
        }
        template.format(*args.toTypedArray())
    }

    @Test
    fun `headline covers zero, one, two and many teachers`() {
        assertEquals("Guru Kelas", texts.headline(emptyList()))
        assertEquals(SITI, texts.headline(listOf(SITI)))
        assertEquals("$SITI, $BUDI", texts.headline(listOf(SITI, BUDI)))
        assertEquals("$SITI, +2 pengajar", texts.headline(listOf(SITI, BUDI, "Ibu Rina")))
    }

    @Test
    fun `pengajar line wraps the headline`() {
        assertEquals("Pengajar: Guru Kelas", texts.pengajarLine(emptyList()))
        assertEquals("Pengajar: $SITI", texts.pengajarLine(listOf(SITI)))
    }

    @Test
    fun `subtitle counts teachers above one and adds the prefixed class name`() {
        assertEquals("Guru Pengajar", texts.subtitle(0, null))
        assertEquals("Guru Pengajar", texts.subtitle(1, "  "))
        assertEquals("Guru Pengajar • Kelas Matahari", texts.subtitle(1, "Kelas Matahari"))
        assertEquals("Guru Pengajar • Kelas Matahari", texts.subtitle(1, "Matahari"))
        assertEquals("2 Guru Pengajar • Kelas Matahari", texts.subtitle(2, "Kelas Matahari"))
    }

    @Test
    fun `banner count never shows fewer than one teacher`() {
        assertEquals("1 Guru Pengajar & 12 Siswa Terdaftar", texts.bannerCount(0, "12 Siswa Terdaftar"))
        assertEquals("3 Guru Pengajar & 12 Siswa Terdaftar", texts.bannerCount(3, "12 Siswa Terdaftar"))
    }

    private companion object {
        const val SITI = "Ibu Siti Aminah, S.Pd"
        const val BUDI = "Pak Budi"
    }
}
