package com.nthb.kibooz.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ClassNamesTest {

    @Test
    fun `stripPrefix removes single or repeated leading Kelas keyword`() {
        assertEquals(STEM_MATAHARI, ClassNames.stripPrefix(CLASS_MATAHARI))
        assertEquals(STEM_BUNGA, ClassNames.stripPrefix("kelas   $STEM_BUNGA"))
        assertEquals(STEM_MELATI, ClassNames.stripPrefix("$WORD_KELAS: $STEM_MELATI"))
        assertEquals("Anggrek", ClassNames.stripPrefix("$WORD_KELAS $WORD_KELAS Anggrek"))
        assertEquals("Bintang", ClassNames.stripPrefix("Bintang"))
        assertEquals("", ClassNames.stripPrefix(WORD_KELAS))
        assertEquals("", ClassNames.stripPrefix("   "))
        assertEquals("", ClassNames.stripPrefix(null))
    }

    @Test
    fun `withPrefix keeps exactly one Kelas prefix`() {
        assertEquals(CLASS_MATAHARI, ClassNames.withPrefix(CLASS_MATAHARI))
        assertEquals("$WORD_KELAS $STEM_BUNGA", ClassNames.withPrefix(STEM_BUNGA))
        assertEquals("$WORD_KELAS $STEM_MELATI", ClassNames.withPrefix("kelas $STEM_MELATI"))
        assertEquals(WORD_KELAS, ClassNames.withPrefix(WORD_KELAS))
        assertEquals(WORD_KELAS, ClassNames.withPrefix(""))
    }

    @Test
    fun `chatRoomTitle avoids Ruang kelas Kelas redundancy`() {
        val format: (String) -> String = { stem -> "$ROOM_PREFIX $stem" }

        assertEquals("$ROOM_PREFIX $STEM_MATAHARI", ClassNames.chatRoomTitle(CLASS_MATAHARI, format, ROOM_PREFIX))
        assertEquals("$ROOM_PREFIX 4A (Seni)", ClassNames.chatRoomTitle("$WORD_KELAS 4A (Seni)", format, ROOM_PREFIX))
        assertEquals("$ROOM_PREFIX $STEM_BUNGA", ClassNames.chatRoomTitle(STEM_BUNGA, format, ROOM_PREFIX))
        assertEquals(ROOM_PREFIX, ClassNames.chatRoomTitle(WORD_KELAS, format, ROOM_PREFIX))
        assertEquals(ROOM_PREFIX, ClassNames.chatRoomTitle("  ", format, ROOM_PREFIX))
    }

    private companion object {
        const val WORD_KELAS = "Kelas"
        const val STEM_MATAHARI = "Matahari"
        const val STEM_BUNGA = "Bunga"
        const val STEM_MELATI = "Melati"
        const val CLASS_MATAHARI = "$WORD_KELAS $STEM_MATAHARI"
        const val ROOM_PREFIX = "Ruang kelas"
    }
}
