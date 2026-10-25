package com.nthb.kibooz.ml

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ModelMathTest {

    private fun argb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    private fun bytes(buffer: ByteBuffer): List<Int> = List(buffer.remaining()) { buffer.get(it).toInt() and 0xFF }

    // Nilai emas dari menjalankan modelnya sungguhan di LiteRT (lihat docs/MODEL_PLACEHOLDERS.md)

    @Test
    fun `the emotion model gets the raw grayscale value because its scale is 1 over 255 and zero point is 0`() {
        val pixels = intArrayOf(argb(0, 0, 0), argb(255, 255, 255), argb(128, 128, 128))

        val input = ModelMath.encodeInput(pixels, 1, mean = 0f, std = 255f, TensorKind.UINT8, 0.003921568859368563f, 0)

        assertEquals(listOf(0, 255, 128), bytes(input))
    }

    @Test
    fun `the trash model maps pixels with its own scale and zero point`() {
        val pixels = intArrayOf(argb(0, 0, 0), argb(127, 127, 127), argb(255, 255, 255))

        val input = ModelMath.encodeInput(pixels, 3, mean = 127f, std = 128f, TensorKind.UINT8, 0.012566016986966133f, 131)

        // Tiap piksel menjadi tiga kanal: R, G, B
        assertEquals(listOf(52, 52, 52, 131, 131, 131, 211, 211, 211), bytes(input))
    }

    @Test
    fun `RGB channels keep their order`() {
        val input = ModelMath.encodeInput(intArrayOf(argb(10, 20, 30)), 3, 0f, 1f, TensorKind.UINT8, 1f, 0)

        assertEquals(listOf(10, 20, 30), bytes(input))
    }

    @Test
    fun `grayscale uses the luma weights`() {
        val input = ModelMath.encodeInput(intArrayOf(argb(255, 0, 0)), 1, 0f, 1f, TensorKind.UINT8, 1f, 0)

        assertEquals(76, bytes(input).single()) // 0,299 * 255 dibulatkan
    }

    @Test
    fun `quantized values are clamped to the type range`() {
        val high = ModelMath.encodeInput(intArrayOf(argb(255, 255, 255)), 1, 0f, 1f, TensorKind.UINT8, 1f, 100)
        val low = ModelMath.encodeInput(intArrayOf(argb(0, 0, 0)), 1, 255f, 1f, TensorKind.INT8, 1f, 0)

        assertEquals(255, bytes(high).single())
        assertEquals(-128, low.get(0).toInt())
    }

    @Test
    fun `a float model gets normalized floats in native byte order`() {
        val input = ModelMath.encodeInput(intArrayOf(argb(255, 255, 255)), 1, 0f, 255f, TensorKind.FLOAT32, 0f, 0)

        assertEquals(4, input.remaining())
        assertEquals(1f, input.order(ByteOrder.nativeOrder()).getFloat(0), 0.0001f)
    }

    @Test
    fun `uint8 output is dequantized with its scale`() {
        val raw = ByteBuffer.allocateDirect(3).put(byteArrayOf(0, 64.toByte(), 255.toByte()))

        val out = ModelMath.decodeOutput(raw, 3, TensorKind.UINT8, 0.00390625f, 0)

        assertArrayEquals(floatArrayOf(0f, 0.25f, 0.99609375f), out, 0.00001f)
    }

    @Test
    fun `int8 output honours the zero point`() {
        val raw = ByteBuffer.allocateDirect(2).put(byteArrayOf(-128, 0))

        val out = ModelMath.decodeOutput(raw, 2, TensorKind.INT8, 0.5f, -128)

        assertArrayEquals(floatArrayOf(0f, 64f), out, 0.0001f)
    }

    @Test
    fun `float output is read as is`() {
        val raw = ByteBuffer.allocateDirect(8).order(ByteOrder.nativeOrder()).putFloat(0.25f).putFloat(0.75f)

        assertArrayEquals(floatArrayOf(0.25f, 0.75f), ModelMath.decodeOutput(raw, 2, TensorKind.FLOAT32, 0f, 0), 0f)
    }

    @Test
    fun `softmax sums to one and keeps the order`() {
        val out = ModelMath.softmax(floatArrayOf(1f, 2f, 3f))

        assertEquals(1f, out.sum(), 0.0001f)
        assertTrue(out[2] > out[1] && out[1] > out[0])
    }

    @Test
    fun `softmax does not overflow on large scores`() {
        val out = ModelMath.softmax(floatArrayOf(1000f, 1000f))

        assertArrayEquals(floatArrayOf(0.5f, 0.5f), out, 0.0001f)
    }

    private val labels = listOf("angry", "disgust", "fear", "happy", "sad", "surprise", "neutral")
    private val groups = mapOf(
        "SENANG" to listOf("happy", "neutral"),
        "SEDIH" to listOf("sad"),
        "MARAH" to listOf("angry", "disgust"),
        "BINGUNG" to listOf("fear", "surprise")
    )

    @Test
    fun `the 7 emotions are summed into the 4 of the API`() {
        val probabilities = floatArrayOf(0.1f, 0.05f, 0.1f, 0.2f, 0.05f, 0.1f, 0.4f)

        val mass = ModelMath.groupMass(probabilities, labels, groups)

        assertEquals(0.6f, mass.getValue("SENANG"), 0.0001f)
        assertEquals(0.05f, mass.getValue("SEDIH"), 0.0001f)
        assertEquals(0.15f, mass.getValue("MARAH"), 0.0001f)
        assertEquals(0.2f, mass.getValue("BINGUNG"), 0.0001f)
        assertEquals("SENANG", ModelMath.best(mass).first)
    }

    @Test
    fun `a neutral face reads as SENANG as decided for Kalem`() {
        val neutral = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 1f)

        assertEquals("SENANG", ModelMath.best(ModelMath.groupMass(neutral, labels, groups)).first)
    }

    @Test
    fun `labels outside every group add nothing`() {
        val mass = ModelMath.groupMass(floatArrayOf(0.5f, 0.3f, 0.2f), listOf("a", "b", "c"), mapOf("ORGANIK" to listOf("a")))

        assertEquals(mapOf("ORGANIK" to 0.5f), mass)
    }

    @Test
    fun `an output of the wrong length is refused`() {
        try {
            ModelMath.groupMass(floatArrayOf(1f), labels, groups)
            fail("expected a length mismatch")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("7"))
        }
    }

    @Test
    fun `when nothing matches the confidence is zero and the choice falls to manual`() {
        val mass = ModelMath.groupMass(floatArrayOf(0f, 1f), listOf("a", "b"), mapOf("ORGANIK" to listOf("a"), "B3" to listOf("a2")).filterKeys { it == "ORGANIK" })

        val (group, confidence) = ModelMath.best(mass)

        assertEquals("ORGANIK", group)
        assertEquals(0f, confidence, 0f)
    }
}
