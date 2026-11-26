package com.nthb.kibooz.ui.chat

import com.nthb.kibooz.data.remote.CHAT_PHOTO_MAX_BYTES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatPhotoRulesTest {

    @Test
    fun `a small photo of an accepted type is sent as it is`() {
        for (type in listOf("image/jpeg", "image/png", "image/webp")) {
            assertFalse(ChatPhotoRules.needsReencode(type, CHAT_PHOTO_MAX_BYTES, 1600, 900))
        }
    }

    @Test
    fun `a refused type, a large or unknown size and a long side are encoded again`() {
        assertTrue(ChatPhotoRules.needsReencode("image/heic", 1_000, 800, 600))
        assertTrue(ChatPhotoRules.needsReencode("image/gif", 1_000, 800, 600))
        assertTrue(ChatPhotoRules.needsReencode("image/jpeg", CHAT_PHOTO_MAX_BYTES + 1, 800, 600))
        assertTrue(ChatPhotoRules.needsReencode("image/jpeg", null, 800, 600))
        assertTrue(ChatPhotoRules.needsReencode("image/jpeg", 1_000, 600, 1601))
    }

    @Test
    fun `the longest side is limited, the shape stays and small images do not grow`() {
        assertEquals(1600 to 1200, ChatPhotoRules.fitWithin(4000, 3000, 1600))
        assertEquals(900 to 1600, ChatPhotoRules.fitWithin(1800, 3200, 1600))
        assertEquals(640 to 480, ChatPhotoRules.fitWithin(640, 480, 1600))
        assertEquals(1600 to 1, ChatPhotoRules.fitWithin(40_000, 10, 1600))
    }
}
