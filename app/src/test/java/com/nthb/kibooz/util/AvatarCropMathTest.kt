package com.nthb.kibooz.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarCropMathTest {

    @Test
    fun `computeSampleSize reduces large photos by powers of two`() {
        assertEquals(1, AvatarCropMath.computeSampleSize(800, 600, maxSide = 2048))
        assertEquals(1, AvatarCropMath.computeSampleSize(2048, 2048, maxSide = 2048))
        assertEquals(2, AvatarCropMath.computeSampleSize(4096, 3000, maxSide = 2048))
        assertEquals(4, AvatarCropMath.computeSampleSize(8192, 6000, maxSide = 2048))
        assertEquals(1, AvatarCropMath.computeSampleSize(0, 100, maxSide = 2048))
    }

    @Test
    fun `minCoverScale always fills the square crop diameter`() {
        val wideScale = AvatarCropMath.minCoverScale(bitmapW = 2000, bitmapH = 1000, cropSizePx = 500f)
        assertEquals(0.5f, wideScale, 0.0001f)

        val tallScale = AvatarCropMath.minCoverScale(bitmapW = 1000, bitmapH = 2000, cropSizePx = 500f)
        assertEquals(0.5f, tallScale, 0.0001f)

        val fallbackScale = AvatarCropMath.minCoverScale(bitmapW = 0, bitmapH = 1000, cropSizePx = 500f)
        assertEquals(1f, fallbackScale, 0.0001f)
    }

    @Test
    fun `clampAxisOrigin keeps scaled bitmap covering the crop bounds`() {
        // crop = [100..600], scaledLength = 800 -> valid origin range = [-200..100]
        assertEquals(100f, AvatarCropMath.clampAxisOrigin(250f, 800f, 100f, 600f), 0.001f)
        assertEquals(-200f, AvatarCropMath.clampAxisOrigin(-350f, 800f, 100f, 600f), 0.001f)
        assertEquals(-50f, AvatarCropMath.clampAxisOrigin(-50f, 800f, 100f, 600f), 0.001f)
    }

    @Test
    fun `clampCropRect keeps square crop within source dimensions`() {
        val rect = AvatarCropMath.clampCropRect(
            rawLeft = -20f,
            rawTop = 150f,
            rawRight = 380f,
            rawBottom = 550f,
            bitmapW = 500,
            bitmapH = 500
        )
        assertEquals(0, rect.left)
        assertEquals(100, rect.top)
        assertEquals(400, rect.size)

        val oversized = AvatarCropMath.clampCropRect(
            rawLeft = 10f,
            rawTop = 20f,
            rawRight = 900f,
            rawBottom = 910f,
            bitmapW = 600,
            bitmapH = 400
        )
        assertEquals(400, oversized.size)
        assertEquals(0, oversized.top)
        assertTrue(oversized.left + oversized.size <= 600)
    }

    @Test
    fun `outputSidePx caps large crops and preserves small ones`() {
        assertEquals(AvatarCropMath.MAX_OUTPUT_SIDE, AvatarCropMath.outputSidePx(1500))
        assertEquals(320, AvatarCropMath.outputSidePx(320))
        assertEquals(1, AvatarCropMath.outputSidePx(-10))
    }
}
