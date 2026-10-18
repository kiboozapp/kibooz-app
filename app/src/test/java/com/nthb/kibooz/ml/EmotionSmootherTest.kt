package com.nthb.kibooz.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class EmotionSmootherTest {

    private val smoother = EmotionSmoother(holdMillis = 300)

    private fun result(emotion: Emotion, confidence: Float = 0.9f) = EmotionResult(emotion, confidence)

    @Test
    fun `label is not accepted before it has held long enough`() {
        assertNull(smoother.update(1, result(Emotion.SENANG), 0))
        assertNull(smoother.update(1, result(Emotion.SENANG), 150))
        assertNull(smoother.update(1, result(Emotion.SENANG), 299))
    }

    @Test
    fun `label is accepted once it has held for the full time`() {
        smoother.update(1, result(Emotion.SENANG), 0)

        val accepted = smoother.update(1, result(Emotion.SENANG), 300)

        assertNotNull(accepted)
        assertEquals(Emotion.SENANG, accepted!!.emotion)
    }

    @Test
    fun `a different label restarts the clock`() {
        smoother.update(1, result(Emotion.SENANG), 0)
        smoother.update(1, result(Emotion.SEDIH), 200)

        assertNull(smoother.update(1, result(Emotion.SEDIH), 450))
        assertEquals(Emotion.SEDIH, smoother.update(1, result(Emotion.SEDIH), 500)!!.emotion)
    }

    @Test
    fun `an accepted label stays until another one has held long enough`() {
        smoother.update(1, result(Emotion.SENANG), 0)
        smoother.update(1, result(Emotion.SENANG), 300)

        // Satu frame berbeda tidak boleh menghapus label yang sudah diterima
        assertEquals(Emotion.SENANG, smoother.update(1, result(Emotion.MARAH), 400)!!.emotion)
        assertEquals(Emotion.SENANG, smoother.update(1, result(Emotion.MARAH), 650)!!.emotion)
        assertEquals(Emotion.MARAH, smoother.update(1, result(Emotion.MARAH), 700)!!.emotion)
    }

    @Test
    fun `a flickering label never gets accepted`() {
        var accepted: EmotionResult? = null

        for (frame in 0 until 20) {
            val emotion = if (frame % 2 == 0) Emotion.SENANG else Emotion.BINGUNG
            accepted = smoother.update(1, result(emotion), frame * 100L)
        }

        assertNull(accepted)
    }

    @Test
    fun `confidence is the average over the time the label held`() {
        smoother.update(1, result(Emotion.SENANG, 0.6f), 0)
        smoother.update(1, result(Emotion.SENANG, 0.8f), 150)

        val accepted = smoother.update(1, result(Emotion.SENANG, 1.0f), 300)

        assertEquals(0.8f, accepted!!.confidence, 0.0001f)
    }

    @Test
    fun `faces are tracked independently`() {
        smoother.update(1, result(Emotion.SENANG), 0)
        smoother.update(2, result(Emotion.SEDIH), 100)

        assertEquals(Emotion.SENANG, smoother.update(1, result(Emotion.SENANG), 300)!!.emotion)
        assertNull(smoother.update(2, result(Emotion.SEDIH), 300))
        assertEquals(Emotion.SEDIH, smoother.update(2, result(Emotion.SEDIH), 400)!!.emotion)
    }

    @Test
    fun `a face that left the frame starts over when it returns`() {
        smoother.update(1, result(Emotion.SENANG), 0)
        smoother.update(1, result(Emotion.SENANG), 300)

        smoother.retain(emptySet())

        assertNull(smoother.update(1, result(Emotion.SENANG), 400))
    }

    @Test
    fun `retain keeps the faces that are still in the frame`() {
        smoother.update(1, result(Emotion.SENANG), 0)
        smoother.update(2, result(Emotion.SEDIH), 0)

        smoother.retain(setOf(1))

        assertEquals(Emotion.SENANG, smoother.update(1, result(Emotion.SENANG), 300)!!.emotion)
        assertNull(smoother.update(2, result(Emotion.SEDIH), 300))
    }
}
