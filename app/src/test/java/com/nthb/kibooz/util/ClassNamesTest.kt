package com.nthb.kibooz.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ClassNamesTest {

    @Test
    fun `stripPrefix removes single or repeated leading Kelas keyword`() {
        assertEquals("Matahari", ClassNames.stripPrefix("Kelas Matahari"))
        assertEquals("Bunga", ClassNames.stripPrefix("kelas   Bunga"))
        assertEquals("Melati", ClassNames.stripPrefix("Kelas: Melati"))
        assertEquals("Anggrek", ClassNames.stripPrefix("Kelas Kelas Anggrek"))
        assertEquals("Bintang", ClassNames.stripPrefix("Bintang"))
        assertEquals("", ClassNames.stripPrefix("Kelas"))
        assertEquals("", ClassNames.stripPrefix("   "))
        assertEquals("", ClassNames.stripPrefix(null))
    }

    @Test
    fun `withPrefix keeps exactly one Kelas prefix`() {
        assertEquals("Kelas Matahari", ClassNames.withPrefix("Kelas Matahari"))
        assertEquals("Kelas Bunga", ClassNames.withPrefix("Bunga"))
        assertEquals("Kelas Melati", ClassNames.withPrefix("kelas Melati"))
        assertEquals("Kelas", ClassNames.withPrefix("Kelas"))
        assertEquals("Kelas", ClassNames.withPrefix(""))
    }

    @Test
    fun `chatRoomTitle avoids Ruang kelas Kelas redundancy`() {
        val format: (String) -> String = { stem -> "Ruang kelas $stem" }

        assertEquals("Ruang kelas Matahari", ClassNames.chatRoomTitle("Kelas Matahari", format, "Ruang kelas"))
        assertEquals("Ruang kelas 4A (Seni)", ClassNames.chatRoomTitle("Kelas 4A (Seni)", format, "Ruang kelas"))
        assertEquals("Ruang kelas Bunga", ClassNames.chatRoomTitle("Bunga", format, "Ruang kelas"))
        assertEquals("Ruang kelas", ClassNames.chatRoomTitle("Kelas", format, "Ruang kelas"))
        assertEquals("Ruang kelas", ClassNames.chatRoomTitle("  ", format, "Ruang kelas"))
    }
}
