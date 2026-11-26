package com.nthb.kibooz.data.remote

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatFrameTest {

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    private fun parse(text: String) = parseChatFrame(moshi, text)

    @Test
    fun `a ready frame is read`() {
        assertEquals(ChatFrame.Ready, parse("""{"type":"ready","expiresAt":"2026-11-26T10:00:00Z"}"""))
    }

    @Test
    fun `a resync frame is read`() {
        assertEquals(ChatFrame.Resync, parse("""{"type":"resync"}"""))
    }

    @Test
    fun `a message frame gives the conversation and nothing of the message`() {
        val text = """
            {"type":"message","conversationId":"cv1","message":{"id":9,"conversationId":"cv1","body":"rahasia",
            "sender":{"id":"u2","fullName":"Bu Sari"},"media":{"kind":"photo","url":"https://x/y"}}}
        """.trimIndent()

        assertEquals(ChatFrame.NewMessage("cv1"), parse(text))
    }

    @Test
    fun `a message frame without a top level conversation uses the one inside the message`() {
        assertEquals(
            ChatFrame.NewMessage("cv7"),
            parse("""{"type":"message","message":{"id":1,"conversationId":"cv7","body":"x"}}""")
        )
    }

    @Test
    fun `a message frame with no conversation at all is still a message frame`() {
        assertEquals(ChatFrame.NewMessage(null), parse("""{"type":"message"}"""))
    }

    @Test
    fun `a deleted frame and a read frame are read`() {
        assertEquals(
            ChatFrame.MessageDeleted("cv1"),
            parse("""{"type":"message.deleted","conversationId":"cv1","messageId":4}""")
        )
        assertEquals(
            ChatFrame.Read("cv1", "u2"),
            parse("""{"type":"read","conversationId":"cv1","userId":"u2","lastReadMessageId":4}""")
        )
    }

    @Test
    fun `typing and unknown types are ignored`() {
        assertNull(parse("""{"type":"typing","conversationId":"cv1","userId":"u2","typing":true}"""))
        assertNull(parse("""{"type":"presence","conversationId":"cv1"}"""))
        assertNull(parse("""{"conversationId":"cv1"}"""))
    }

    @Test
    fun `unknown fields are ignored`() {
        assertEquals(
            ChatFrame.Ready,
            parse("""{"type":"ready","nanti":{"a":[1,2,3]},"expiresAt":"x","extra":null}""")
        )
    }

    @Test
    fun `a bad frame gives null and never throws`() {
        assertNull(parse(""))
        assertNull(parse("bukan json"))
        assertNull(parse("""{"type":"message""""))
        assertNull(parse("[1,2,3]"))
        assertNull(parse("null"))
        assertNull(parse("123"))
        assertNull(parse("""{"type":{"a":1}}"""))
        assertNull(parse("""{"type":"message","message":"salah"}"""))
    }
}
