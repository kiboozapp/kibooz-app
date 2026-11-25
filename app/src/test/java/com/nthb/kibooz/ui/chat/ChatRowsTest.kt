package com.nthb.kibooz.ui.chat

import com.nthb.kibooz.R
import com.nthb.kibooz.data.repository.PendingMessage
import com.nthb.kibooz.ui.chat.ChatFixtures.message
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRowsTest {

    private val utc = TimeZone.getTimeZone("UTC")
    private val jakarta = TimeZone.getTimeZone("Asia/Jakarta")

    private fun stateOf(vararg messages: com.nthb.kibooz.data.remote.ChatMessage, block: ChatState.() -> ChatState = { this }) =
        ChatState(loaded = true, loading = false, timeline = ChatTimeline().merge(messages.toList())).block()

    private fun build(state: ChatState, isRoom: Boolean = false, zone: TimeZone = utc) =
        buildChatRows(state, ownUserId = "me", isRoom = isRoom, zone = zone)

    @Test
    fun `an empty conversation has no rows`() {
        assertTrue(build(stateOf()).isEmpty())
    }

    @Test
    fun `a date row comes before the first message and again each time the day changes`() {
        val rows = build(
            stateOf(
                message(1, createdAt = "2026-11-24T08:00:00Z"),
                message(2, createdAt = "2026-11-24T09:00:00Z"),
                message(3, createdAt = "2026-11-25T01:00:00Z")
            )
        )

        assertEquals(
            listOf("d:24 November 2026", "m:1", "m:2", "d:25 November 2026", "m:3"),
            rows.map { it.key }
        )
    }

    @Test
    fun `the day follows the zone of the person and not the server`() {
        val state = stateOf(message(1, createdAt = "2026-11-24T23:30:00Z"))

        assertEquals("24 November 2026", (build(state, zone = utc)[0] as DateRow).label)
        assertEquals("25 November 2026", (build(state, zone = jakarta)[0] as DateRow).label)
    }

    @Test
    fun `each message carries its local time and who wrote it`() {
        val rows = build(
            stateOf(
                message(1, senderId = "u2", createdAt = "2026-11-25T07:05:00Z"),
                message(2, senderId = "me", createdAt = "2026-11-25T07:06:00Z")
            ),
            zone = jakarta
        )

        val theirs = rows[1] as MessageRow
        val mine = rows[2] as MessageRow
        assertFalse(theirs.own)
        assertEquals("14:05", theirs.time)
        assertTrue(mine.own)
        assertEquals("14:06", mine.time)
    }

    @Test
    fun `a time that cannot be read gives an empty time and no date row`() {
        val rows = build(stateOf(message(1, createdAt = "kemarin")))

        assertEquals(listOf("m:1"), rows.map { it.key })
        assertEquals("", (rows[0] as MessageRow).time)
    }

    @Test
    fun `my message is read when the other person has read up to it, in a direct chat only`() {
        val messages = arrayOf(message(1, senderId = "me"), message(2, senderId = "me"), message(3, senderId = "u2"))

        val direct = build(stateOf(*messages) { copy(peerLastReadMessageId = 1) }).filterIsInstance<MessageRow>()
        assertTrue(direct[0].readByPeer)
        assertFalse(direct[1].readByPeer)
        assertFalse(direct[2].readByPeer)

        val room = build(stateOf(*messages) { copy(peerLastReadMessageId = 5) }, isRoom = true).filterIsInstance<MessageRow>()
        assertTrue(room.none { it.readByPeer })
    }

    @Test
    fun `only my own messages that are not deleted can be deleted, and not while another delete runs`() {
        val state = stateOf(message(1, senderId = "me"), message(2, senderId = "u2"), message(3, senderId = "me", deleted = true))

        val idle = build(state).filterIsInstance<MessageRow>()
        assertTrue(idle[0].deletable)
        assertFalse(idle[1].deletable)
        assertFalse(idle[2].deletable)

        val busy = build(state.copy(deletingId = 1)).filterIsInstance<MessageRow>()
        assertTrue(busy[0].deleting)
        assertTrue(busy.none { it.deletable })
    }

    @Test
    fun `a deleted message that was read is not marked as read`() {
        val rows = build(stateOf(message(1, senderId = "me", deleted = true)) { copy(peerLastReadMessageId = 9) })

        assertFalse((rows[1] as MessageRow).readByPeer)
    }

    @Test
    fun `a message that is waiting or failed comes last, after every message from the server`() {
        val queued = LocalMessage(PendingMessage("belum sampai"), LocalStatus.Failed(R.string.error_network))
        val rows = build(stateOf(message(1), message(2, senderId = "me")) { copy(local = queued) })

        assertEquals("local", rows.last().key)
        assertEquals(queued, (rows.last() as LocalRow).local)
        assertEquals(1, rows.count { it is LocalRow })
    }

    @Test
    fun `the row for older messages shows only while loading or after an error`() {
        val quiet = build(stateOf(message(2)) { copy(hasMoreOlder = true) })
        assertTrue(quiet.none { it is OlderRow })

        val loading = build(stateOf(message(2)) { copy(hasMoreOlder = true, loadingOlder = true) })
        assertEquals(OlderRow(loading = true, error = null), loading.first())

        val failed = build(stateOf(message(2)) { copy(hasMoreOlder = true, olderError = R.string.error_network) })
        assertEquals(OlderRow(loading = false, error = R.string.error_network), failed.first())

        val nothingMore = build(stateOf(message(2)) { copy(hasMoreOlder = false, olderError = R.string.error_network) })
        assertNull(nothingMore.firstOrNull { it is OlderRow })
    }

    @Test
    fun `keys are unique so the list can diff them`() {
        val rows = build(
            stateOf(message(1, createdAt = "2026-11-24T08:00:00Z"), message(2, createdAt = "2026-11-25T08:00:00Z")) {
                copy(hasMoreOlder = true, loadingOlder = true, local = LocalMessage(PendingMessage("x"), LocalStatus.Sending()))
            }
        )

        assertEquals(rows.size, rows.map { it.key }.toSet().size)
    }
}
