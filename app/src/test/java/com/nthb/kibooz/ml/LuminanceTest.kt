package com.nthb.kibooz.ml

import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LuminanceTest {

    private fun plane(width: Int, height: Int, rowStride: Int = width, value: Int): ByteBuffer {
        val buffer = ByteBuffer.allocate(rowStride * height)
        for (i in 0 until buffer.capacity()) buffer.put(i, value.toByte())
        return buffer
    }

    @Test
    fun `a black frame has zero luma and counts as low light`() {
        val luma = meanLuma(plane(64, 64, value = 0), 64, 64, rowStride = 64, pixelStride = 1)

        assertEquals(0f, luma, 0.001f)
        assertTrue(isLowLight(luma))
    }

    @Test
    fun `a bright frame is not low light`() {
        val luma = meanLuma(plane(64, 64, value = 200), 64, 64, rowStride = 64, pixelStride = 1)

        assertEquals(200f, luma, 0.001f)
        assertFalse(isLowLight(luma))
    }

    @Test
    fun `bytes above 127 are read as unsigned`() {
        // Byte 255 bernilai -1 jika dibaca bertanda, yang akan terlihat seperti frame gelap
        val luma = meanLuma(plane(32, 32, value = 255), 32, 32, rowStride = 32, pixelStride = 1)

        assertEquals(255f, luma, 0.001f)
    }

    @Test
    fun `the threshold is exclusive`() {
        assertTrue(isLowLight(39.9f))
        assertFalse(isLowLight(LOW_LIGHT_LUMA))
        assertFalse(isLowLight(40.1f))
    }

    @Test
    fun `row padding is skipped by the row stride`() {
        // Lebar 20 tetapi baris selebar 32 byte, sisanya padding gelap yang tidak boleh ikut dihitung
        val buffer = ByteBuffer.allocate(32 * 20)
        for (y in 0 until 20) for (x in 0 until 20) buffer.put(y * 32 + x, 180.toByte())

        val luma = meanLuma(buffer, 20, 20, rowStride = 32, pixelStride = 1, step = 4)

        assertEquals(180f, luma, 0.001f)
    }

    @Test
    fun `pixel stride skips interleaved bytes`() {
        // Setiap piksel dipisah satu byte lain, seperti pada bidang Y yang berselang-seling
        val buffer = ByteBuffer.allocate(64 * 2 * 8)
        for (i in 0 until buffer.capacity()) buffer.put(i, if (i % 2 == 0) 100 else 10)

        val luma = meanLuma(buffer, 64, 8, rowStride = 128, pixelStride = 2, step = 4)

        assertEquals(100f, luma, 0.001f)
    }

    @Test
    fun `an empty frame reports zero without failing`() {
        assertEquals(0f, meanLuma(ByteBuffer.allocate(0), 0, 0, rowStride = 0, pixelStride = 1), 0f)
    }

    @Test
    fun `reading does not move the position of the original buffer`() {
        val buffer = plane(16, 16, value = 90)
        buffer.position(5)

        meanLuma(buffer, 16, 16, rowStride = 16, pixelStride = 1)

        assertEquals(5, buffer.position())
    }
}
