package com.nthb.kibooz.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrashSmootherTest {

    private fun result(category: TrashCategory, confidence: Float = 0.9f) = TrashResult(category, confidence)

    @Test
    fun `nothing is accepted on the first frames`() {
        val smoother = TrashSmoother()

        assertNull(smoother.update(result(TrashCategory.ORGANIK), 0))
        assertNull(smoother.update(result(TrashCategory.ORGANIK), 100))
    }

    @Test
    fun `a category is accepted after three frames held for 300 ms`() {
        val smoother = TrashSmoother()
        smoother.update(result(TrashCategory.ORGANIK), 0)
        smoother.update(result(TrashCategory.ORGANIK), 150)

        val accepted = smoother.update(result(TrashCategory.ORGANIK), 300)

        assertEquals(TrashCategory.ORGANIK, accepted?.category)
    }

    @Test
    fun `three frames in under 300 ms are not enough`() {
        val smoother = TrashSmoother()
        smoother.update(result(TrashCategory.B3), 0)
        smoother.update(result(TrashCategory.B3), 50)

        assertNull(smoother.update(result(TrashCategory.B3), 100))
    }

    @Test
    fun `the confidence is the average over the frames`() {
        val smoother = TrashSmoother()
        smoother.update(result(TrashCategory.ANORGANIK, 0.6f), 0)
        smoother.update(result(TrashCategory.ANORGANIK, 0.9f), 200)

        val accepted = smoother.update(result(TrashCategory.ANORGANIK, 0.9f), 400)!!

        assertEquals(0.8f, accepted.confidence, 0.001f)
    }

    @Test
    fun `a flicker to another category does not replace an accepted one until it holds`() {
        val smoother = TrashSmoother()
        smoother.update(result(TrashCategory.ORGANIK), 0)
        smoother.update(result(TrashCategory.ORGANIK), 200)
        smoother.update(result(TrashCategory.ORGANIK), 400)

        val flicker = smoother.update(result(TrashCategory.ANORGANIK), 500)

        assertEquals(TrashCategory.ORGANIK, flicker?.category)
    }

    @Test
    fun `a new category replaces the old one once it holds long enough`() {
        val smoother = TrashSmoother()
        smoother.update(result(TrashCategory.ORGANIK), 0)
        smoother.update(result(TrashCategory.ORGANIK), 200)
        smoother.update(result(TrashCategory.ORGANIK), 400)
        smoother.update(result(TrashCategory.ANORGANIK), 500)
        smoother.update(result(TrashCategory.ANORGANIK), 700)

        val accepted = smoother.update(result(TrashCategory.ANORGANIK), 900)

        assertEquals(TrashCategory.ANORGANIK, accepted?.category)
    }

    @Test
    fun `reset forgets the accepted category`() {
        val smoother = TrashSmoother()
        smoother.update(result(TrashCategory.ORGANIK), 0)
        smoother.update(result(TrashCategory.ORGANIK), 200)
        smoother.update(result(TrashCategory.ORGANIK), 400)

        smoother.reset()

        assertNull(smoother.update(result(TrashCategory.ORGANIK), 500))
    }
}
