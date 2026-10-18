package com.nthb.kibooz.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameMappingTest {

    private fun assertBox(expected: ViewBox, actual: ViewBox) {
        assertEquals(expected.left, actual.left, 0.001f)
        assertEquals(expected.top, actual.top, 0.001f)
        assertEquals(expected.right, actual.right, 0.001f)
        assertEquals(expected.bottom, actual.bottom, 0.001f)
    }

    @Test
    fun `same size maps one to one`() {
        val mapping = FrameMapping(720, 1280, 720, 1280)

        assertBox(ViewBox(100f, 200f, 300f, 400f), mapping.map(100, 200, 300, 400))
    }

    @Test
    fun `a taller frame is cropped top and bottom`() {
        // Frame 720x1280 di tampilan 360x600: skala 0,5 dan 20 piksel terpotong di atas dan bawah
        val mapping = FrameMapping(720, 1280, 360, 600)

        assertBox(ViewBox(50f, 80f, 150f, 180f), mapping.map(100, 200, 300, 400))
    }

    @Test
    fun `a wider frame is cropped left and right`() {
        // Frame 1280x720 di tampilan 360x600: skala 600/720 dan sisi kiri kanan terpotong rata
        val mapping = FrameMapping(1280, 720, 360, 600)
        val scale = 600f / 720f
        val offsetX = (360f - 1280f * scale) / 2f

        assertBox(ViewBox(offsetX, 0f, offsetX + 1280f * scale, 600f), mapping.map(0, 0, 1280, 720))
    }

    @Test
    fun `the center of the frame stays at the center of the view`() {
        val mapping = FrameMapping(1280, 720, 360, 600)

        val box = mapping.map(639, 359, 641, 361)

        assertEquals(180f, (box.left + box.right) / 2f, 0.5f)
        assertEquals(300f, (box.top + box.bottom) / 2f, 0.5f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a zero sized view is rejected`() {
        FrameMapping(720, 1280, 0, 600)
    }

    @Test
    fun `contains includes the edges and excludes the outside`() {
        val box = ViewBox(10f, 20f, 30f, 40f)

        assertTrue(box.contains(10f, 20f))
        assertTrue(box.contains(30f, 40f))
        assertTrue(box.contains(20f, 30f))
        assertFalse(box.contains(9.9f, 30f))
        assertFalse(box.contains(20f, 40.1f))
    }
}
