package com.nthb.kibooz.ui.common

import com.nthb.kibooz.ml.Emotion
import com.nthb.kibooz.ml.EmotionResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmotionLabelTest {

    @Test
    fun `badge shows the emoji, the name and the percentage`() {
        assertEquals("😊 Senang (92%)", EmotionResult(Emotion.SENANG, 0.92f).badgeText())
        assertEquals("😕 Bingung (66%)", EmotionResult(Emotion.BINGUNG, 0.66f).badgeText())
    }

    @Test
    fun `every emotion has its own name and emoji`() {
        val names = Emotion.values().map { it.displayName() }
        val emojis = Emotion.values().map { it.emoji() }

        assertEquals(Emotion.values().size, names.toSet().size)
        assertEquals(Emotion.values().size, emojis.toSet().size)
        assertTrue(names.all { it.isNotBlank() })
    }
}
