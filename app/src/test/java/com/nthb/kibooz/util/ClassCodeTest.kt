package com.nthb.kibooz.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClassCodeTest {

    @Test
    fun `a valid code is trimmed and upper cased`() {
        assertEquals("K7M2QX4R", ClassCode.normalize("  k7m2qx4r "))
        assertEquals("ABC23456", ClassCode.normalize("ABC23456"))
    }

    @Test
    fun `the length limits are 4 and 12 characters`() {
        assertNull(ClassCode.normalize("ABC"))
        assertEquals("ABCD", ClassCode.normalize("abcd"))
        assertEquals("ABCDEFGHJKLM", ClassCode.normalize("ABCDEFGHJKLM"))
        assertNull(ClassCode.normalize("ABCDEFGHJKLMN"))
    }

    @Test
    fun `dashes, spaces and symbols are refused`() {
        assertNull(ClassCode.normalize("ABCD-2345"))
        assertNull(ClassCode.normalize("ABCD 2345"))
        assertNull(ClassCode.normalize("ABCD_2345"))
    }

    @Test
    fun `an empty code is refused`() {
        assertNull(ClassCode.normalize(""))
        assertNull(ClassCode.normalize("   "))
    }
}
