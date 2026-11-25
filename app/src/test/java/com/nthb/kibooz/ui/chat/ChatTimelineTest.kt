package com.nthb.kibooz.ui.chat

import com.nthb.kibooz.ui.chat.ChatFixtures.message
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatTimelineTest {

    private fun ids(timeline: ChatTimeline) = timeline.messages.map { it.id }

    @Test
    fun `an empty timeline has no cursors`() {
        val timeline = ChatTimeline()

        assertNull(timeline.newestId)
        assertNull(timeline.oldestId)
    }

    @Test
    fun `the first load is kept in id order whatever order it arrives in`() {
        val timeline = ChatTimeline().merge(listOf(message(5), message(3), message(4)))

        assertEquals(listOf(3L, 4L, 5L), ids(timeline))
        assertEquals(5L, timeline.newestId)
        assertEquals(3L, timeline.oldestId)
    }

    @Test
    fun `older messages go in front`() {
        val timeline = ChatTimeline().merge(listOf(message(5), message(6))).merge(listOf(message(3), message(4)))

        assertEquals(listOf(3L, 4L, 5L, 6L), ids(timeline))
        assertEquals(3L, timeline.oldestId)
    }

    @Test
    fun `a polling answer that overlaps what is on screen adds no duplicate`() {
        val timeline = ChatTimeline()
            .merge(listOf(message(1), message(2), message(3)))
            .merge(listOf(message(3), message(4)))
            .merge(listOf(message(2), message(3), message(4), message(5)))

        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), ids(timeline))
    }

    @Test
    fun `the same message twice in one answer is kept once`() {
        val timeline = ChatTimeline().merge(listOf(message(1), message(1)))

        assertEquals(listOf(1L), ids(timeline))
    }

    @Test
    fun `a newer copy of a message replaces the older copy`() {
        val timeline = ChatTimeline()
            .merge(listOf(message(1, body = "lama")))
            .merge(listOf(message(1, body = "baru")))

        assertEquals("baru", timeline.messages.single().body)
    }

    @Test
    fun `a message that was deleted is replaced by its deleted copy`() {
        val timeline = ChatTimeline()
            .merge(listOf(message(1, body = "rahasia")))
            .merge(listOf(message(1, deleted = true)))

        assertTrue(timeline.messages.single().deleted)
        assertEquals("", timeline.messages.single().body)
    }

    @Test
    fun `a late copy from before the deletion does not bring a deleted message back`() {
        val timeline = ChatTimeline()
            .merge(listOf(message(1, deleted = true)))
            .merge(listOf(message(1, body = "rahasia")))

        assertTrue(timeline.messages.single().deleted)
        assertEquals("", timeline.messages.single().body)
    }

    @Test
    fun `merging nothing returns the same timeline`() {
        val timeline = ChatTimeline().merge(listOf(message(1)))

        assertSame(timeline, timeline.merge(emptyList()))
    }

    @Test
    fun `merging does not change the timeline it started from`() {
        val before = ChatTimeline().merge(listOf(message(1)))

        before.merge(listOf(message(2)))

        assertEquals(listOf(1L), ids(before))
        assertFalse(before.messages.any { it.id == 2L })
    }

    @Test
    fun `a message of the other person and one of mine keep their order by id`() {
        val timeline = ChatTimeline().merge(listOf(message(1, senderId = "me"), message(3, senderId = "u2")))
            .merge(listOf(message(2, senderId = "me")))

        assertEquals(listOf(1L, 2L, 3L), ids(timeline))
    }
}
