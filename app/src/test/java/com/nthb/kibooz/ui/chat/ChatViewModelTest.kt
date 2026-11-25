package com.nthb.kibooz.ui.chat

import com.nthb.kibooz.R
import com.nthb.kibooz.data.remote.ChatConversationList
import com.nthb.kibooz.data.remote.ChatReadState
import com.nthb.kibooz.data.remote.OutageKind
import com.nthb.kibooz.data.repository.ChatRepository
import com.nthb.kibooz.ui.chat.ChatFixtures.direct
import com.nthb.kibooz.ui.chat.ChatFixtures.failure
import com.nthb.kibooz.ui.chat.ChatFixtures.message
import com.nthb.kibooz.ui.chat.ChatFixtures.messages
import com.nthb.kibooz.ui.chat.ChatFixtures.ok
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private val api = FakeChatApi()
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val repository = ChatRepository(api, moshi)
    private val pauses = mutableListOf<Long>()

    private val openInfo = ChatInfo("cv1", isRoom = false, peerDeleted = false, canSend = true, peerLastReadMessageId = 0)

    private fun viewModel(info: ChatInfo = openInfo) =
        ChatViewModel(repository, info, ownUserId = "me", pause = { pauses += it }, jitter = { 0L })

    private fun ids(viewModel: ChatViewModel) = viewModel.state.value.timeline.messages.map { it.id }

    /** Jawaban kirim yang menyimpan pesan sebagai id 3, 4, dan seterusnya, seperti server. */
    private fun acceptSends() {
        var next = 3L
        api.sendReply = { body -> ok(message(next++, senderId = "me", body = body)) }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        api.messagesReply = { _, _ -> messages(message(1, senderId = "u2"), message(2, senderId = "me")) }
        acceptSends()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // Muat awal

    @Test
    fun `it loads the latest messages on creation`() {
        api.messagesReply = { _, _ -> messages(message(1), message(2, senderId = "me"), hasMore = true) }

        val state = viewModel().state.value

        assertFalse(state.loading)
        assertTrue(state.loaded)
        assertEquals(listOf(1L, 2L), state.timeline.messages.map { it.id })
        assertTrue(state.hasMoreOlder)
        assertEquals(listOf<Pair<Long?, Long?>>(null to null), api.messageCalls)
    }

    @Test
    fun `a conversation with no messages loads as empty and has nothing older`() {
        api.messagesReply = { _, _ -> messages(hasMore = true) }

        val state = viewModel().state.value

        assertTrue(state.loaded)
        assertTrue(state.timeline.messages.isEmpty())
        assertFalse(state.hasMoreOlder)
    }

    @Test
    fun `offline on the first load shows a countdown that grows, and the person can retry`() {
        api.messagesReply = { _, _ -> throw IOException("putus") }
        val viewModel = viewModel()

        var state = viewModel.state.value
        assertFalse(state.loaded)
        assertEquals(R.string.error_network, state.loadError)
        assertEquals(OutageKind.OFFLINE, state.outage?.kind)
        assertEquals(30L, state.outage?.waitSeconds)

        viewModel.retryAfterOutage()
        state = viewModel.state.value
        assertEquals(60L, state.outage?.waitSeconds)

        viewModel.load()
        assertEquals(30L, viewModel.state.value.outage?.waitSeconds)

        api.messagesReply = { _, _ -> messages(message(1)) }
        viewModel.load()
        state = viewModel.state.value
        assertTrue(state.loaded)
        assertNull(state.loadError)
        assertNull(state.outage)
        assertEquals(listOf(1L), ids(viewModel))
    }

    @Test
    fun `a slow server shows the unavailable countdown from Retry-After`() {
        api.messagesReply = { _, _ -> failure(503, "SERVER_TIMEOUT", retryAfter = "1") }

        val state = viewModel().state.value

        assertEquals(OutageKind.UNAVAILABLE, state.outage?.kind)
        assertEquals(1L, state.outage?.waitSeconds)
        assertEquals(R.string.error_unavailable, state.loadError)
    }

    @Test
    fun `a conversation that is gone is final and has no countdown`() {
        api.messagesReply = { _, _ -> failure(404, "CHAT_CONVERSATION_NOT_FOUND") }

        val state = viewModel().state.value

        assertTrue(state.gone)
        assertEquals(R.string.error_chat_conversation_not_found, state.loadError)
        assertNull(state.outage)
        assertFalse(state.loaded)
    }

    @Test
    fun `another server error shows its message and no countdown`() {
        api.messagesReply = { _, _ -> failure(500, "INTERNAL_ERROR") }

        val state = viewModel().state.value

        assertEquals(R.string.error_server, state.loadError)
        assertNull(state.outage)
        assertFalse(state.gone)
    }

    // Riwayat

    @Test
    fun `older messages are asked for before the oldest id and go in front`() {
        api.messagesReply = { _, before ->
            if (before == null) messages(message(5), message(6), hasMore = true) else messages(message(3), message(4), hasMore = false)
        }
        val viewModel = viewModel()

        viewModel.loadOlder()

        assertEquals(listOf(null to null, null to 5L), api.messageCalls)
        assertEquals(listOf(3L, 4L, 5L, 6L), ids(viewModel))
        assertFalse(viewModel.state.value.hasMoreOlder)
        assertFalse(viewModel.state.value.loadingOlder)

        viewModel.loadOlder()
        assertEquals(2, api.messageCalls.size)
    }

    @Test
    fun `older pages can be loaded one after another until nothing is left`() {
        api.messagesReply = { _, before ->
            when (before) {
                null -> messages(message(7), message(8), hasMore = true)
                7L -> messages(message(5), message(6), hasMore = true)
                else -> messages(message(3), message(4), hasMore = false)
            }
        }
        val viewModel = viewModel()

        viewModel.loadOlder()
        viewModel.loadOlder()
        viewModel.loadOlder()

        assertEquals(listOf(3L, 4L, 5L, 6L, 7L, 8L), ids(viewModel))
        assertEquals(listOf<Pair<Long?, Long?>>(null to null, null to 7L, null to 5L), api.messageCalls)
    }

    @Test
    fun `an error while loading older stays until the person taps it, and then it tries again`() {
        var failing = true
        api.messagesReply = { _, before ->
            when {
                before == null -> messages(message(5), hasMore = true)
                failing -> throw IOException("putus")
                else -> messages(message(4), hasMore = false)
            }
        }
        val viewModel = viewModel()

        viewModel.loadOlder()
        assertEquals(R.string.error_network, viewModel.state.value.olderError)
        assertFalse(viewModel.state.value.loadingOlder)

        // Gulir otomatis tidak mengulang sendiri setelah galat
        viewModel.loadOlder()
        assertEquals(2, api.messageCalls.size)

        failing = false
        viewModel.loadOlder(retry = true)
        assertNull(viewModel.state.value.olderError)
        assertEquals(listOf(4L, 5L), ids(viewModel))
    }

    @Test
    fun `nothing older is asked for when the first load had no more`() {
        val viewModel = viewModel()

        viewModel.loadOlder()

        assertEquals(1, api.messageCalls.size)
    }

    @Test
    fun `nothing older is asked for before the first load worked`() {
        api.messagesReply = { _, _ -> throw IOException("putus") }
        val viewModel = viewModel()

        viewModel.loadOlder()

        assertEquals(1, api.messageCalls.size)
    }

    // Polling

    @Test
    fun `polling asks after the newest id and merges the answer in order with no duplicate`() = runBlocking {
        api.messagesReply = { after, _ ->
            when (after) {
                null -> messages(message(1), message(2, senderId = "me"))
                2L -> messages(message(2, senderId = "me"), message(3), message(4))
                else -> messages()
            }
        }
        val viewModel = viewModel()

        viewModel.poll()
        assertEquals(listOf(1L, 2L, 3L, 4L), ids(viewModel))

        viewModel.poll()
        assertEquals(listOf(1L, 2L, 3L, 4L), ids(viewModel))
        assertEquals(listOf<Pair<Long?, Long?>>(null to null, 2L to null, 4L to null), api.messageCalls)
    }

    @Test
    fun `a message of the other person that arrived before ours is still fetched after we send`() = runBlocking {
        // Lawan bicara menulis id 3, lalu kita mengirim dan mendapat id 4 sebelum polling sempat mengambil id 3
        api.sendReply = { body -> ok(message(4, senderId = "me", body = body)) }
        api.messagesReply = { after, _ ->
            if (after == 2L) messages(message(3, senderId = "u2"), message(4, senderId = "me")) else messages(message(1), message(2, senderId = "me"))
        }
        val viewModel = viewModel()

        viewModel.send("halo")
        assertEquals(listOf(1L, 2L, 4L), ids(viewModel))

        viewModel.poll()

        assertEquals(listOf(1L, 2L, 3L, 4L), ids(viewModel))
        assertEquals(2L to null, api.messageCalls.last())
    }

    @Test
    fun `a poll that arrives out of order is still shown in id order`() = runBlocking {
        api.messagesReply = { after, _ -> if (after == null) messages(message(1)) else messages(message(4), message(2), message(3)) }
        val viewModel = viewModel()

        viewModel.poll()

        assertEquals(listOf(1L, 2L, 3L, 4L), ids(viewModel))
    }

    @Test
    fun `a poll with more pages follows the cursor in the same tick`() = runBlocking {
        api.messagesReply = { after, _ ->
            when (after) {
                null -> messages(message(1), message(2))
                2L -> messages(message(3), message(4), hasMore = true)
                4L -> messages(message(5), hasMore = false)
                else -> messages()
            }
        }
        val viewModel = viewModel()

        viewModel.poll()

        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), ids(viewModel))
        assertEquals(listOf<Pair<Long?, Long?>>(null to null, 2L to null, 4L to null), api.messageCalls)
    }

    @Test
    fun `polling starts from zero when the conversation has no message yet`() = runBlocking {
        api.messagesReply = { after, _ -> if (after == null) messages() else messages(message(1)) }
        val viewModel = viewModel()

        viewModel.poll()

        assertEquals(0L to null, api.messageCalls.last())
        assertEquals(listOf(1L), ids(viewModel))
    }

    @Test
    fun `polling does nothing before the first load worked`() = runBlocking {
        api.messagesReply = { _, _ -> throw IOException("putus") }
        val viewModel = viewModel()

        viewModel.poll()

        assertEquals(1, api.messageCalls.size)
    }

    @Test
    fun `polling waits while a message is being sent and then carries on`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        api.sendGate = gate
        val viewModel = viewModel()
        viewModel.send("halo")

        viewModel.poll()
        assertEquals(1, api.messageCalls.size)

        gate.complete(Unit)
        assertEquals(listOf(1L, 2L, 3L), ids(viewModel))
        assertNull(viewModel.state.value.local)

        viewModel.poll()
        assertEquals(2, api.messageCalls.size)
    }

    @Test
    fun `a message that polling already brought is not shown twice when the retry brings the same answer`() = runBlocking {
        var attempts = 0
        api.sendReply = { body ->
            if (++attempts == 1) throw IOException("jawaban hilang") else ok(message(3, senderId = "me", body = body))
        }
        api.messagesReply = { after, _ ->
            if (after == 2L) messages(message(3, senderId = "me", body = "halo")) else messages(message(1), message(2, senderId = "me"))
        }
        val viewModel = viewModel()
        viewModel.send("halo")
        assertTrue(viewModel.state.value.local?.status is LocalStatus.Failed)

        viewModel.poll()
        assertEquals(listOf(1L, 2L, 3L), ids(viewModel))

        viewModel.retry()

        assertEquals(listOf(1L, 2L, 3L), ids(viewModel))
        assertNull(viewModel.state.value.local)
        assertEquals(api.sendKeys[0], api.sendKeys[1])
    }

    @Test
    fun `a lost connection shows the offline note and the next poll that works clears it`() = runBlocking {
        var online = true
        api.messagesReply = { after, _ ->
            when {
                after == null -> messages(message(1))
                !online -> throw IOException("putus")
                else -> messages(message(2))
            }
        }
        val viewModel = viewModel()

        online = false
        viewModel.poll()
        assertTrue(viewModel.state.value.offline)
        assertEquals(listOf(1L), ids(viewModel))

        online = true
        viewModel.poll()
        assertFalse(viewModel.state.value.offline)
        assertEquals(listOf(1L, 2L), ids(viewModel))
    }

    @Test
    fun `a rate limit pauses polling for Retry-After and then it carries on`() = runBlocking {
        var limited = true
        api.messagesReply = { after, _ ->
            when {
                after == null -> messages(message(1))
                limited -> failure(429, "RATE_LIMITED", retryAfter = "10")
                else -> messages(message(2))
            }
        }
        val viewModel = viewModel()

        viewModel.poll()
        assertEquals(2, api.messageCalls.size)
        assertFalse(viewModel.state.value.offline)

        limited = false
        // 10 detik dibagi tick 5 detik, ditambah satu, jadi tiga tick dilewati
        repeat(3) { viewModel.poll() }
        assertEquals(2, api.messageCalls.size)

        viewModel.poll()
        assertEquals(3, api.messageCalls.size)
        assertEquals(listOf(1L, 2L), ids(viewModel))
    }

    @Test
    fun `a conversation that disappears while polling is reported and polling stops`() = runBlocking {
        api.messagesReply = { after, _ -> if (after == null) messages(message(1)) else failure(404, "CHAT_CONVERSATION_NOT_FOUND") }
        val viewModel = viewModel()

        viewModel.poll()
        val state = viewModel.state.value
        assertTrue(state.gone)
        assertEquals(R.string.error_chat_conversation_not_found, state.loadError)

        viewModel.poll()
        assertEquals(2, api.messageCalls.size)
    }

    @Test
    fun `every sixth poll also reloads the latest messages and the state of the conversation`() = runBlocking {
        var changed = false
        api.messagesReply = { after, before ->
            when {
                after == null && before == null && changed -> messages(message(1, deleted = true), message(2, senderId = "me"))
                after == null && before == null -> messages(message(1), message(2, senderId = "me"))
                else -> messages()
            }
        }
        api.conversationsReply = { page ->
            ok(ChatConversationList(page, 50, 1, listOf(direct("cv1", canSend = false, peerRead = 2))))
        }
        val viewModel = viewModel()
        changed = true

        repeat(5) { viewModel.poll() }
        assertFalse(viewModel.state.value.timeline.messages[0].deleted)
        assertTrue(viewModel.state.value.canSend)
        assertTrue(api.conversationPages.isEmpty())

        viewModel.poll()

        assertTrue(viewModel.state.value.timeline.messages[0].deleted)
        assertFalse(viewModel.state.value.canSend)
        assertEquals(2L, viewModel.state.value.peerLastReadMessageId)
        assertEquals(listOf(1), api.conversationPages)
        assertFalse(viewModel.state.value.canCompose)
    }

    @Test
    fun `a conversation that is missing from the list leaves canSend as it was`() = runBlocking {
        api.conversationsReply = { page -> ok(ChatConversationList(page, 50, 0, emptyList())) }
        val viewModel = viewModel()

        repeat(6) { viewModel.poll() }

        assertTrue(viewModel.state.value.canSend)
    }

    // Kirim

    @Test
    fun `a message is sent trimmed with a key and appears in order once the server accepts it`() {
        val viewModel = viewModel()

        val accepted = viewModel.send("  halo, apa kabar  ")

        assertTrue(accepted)
        assertEquals(listOf("halo, apa kabar"), api.sendBodies)
        assertEquals(1, api.sendKeys.size)
        assertTrue(api.sendKeys[0].matches(Regex("^[A-Za-z0-9_-]{8,64}$")))
        assertNull(viewModel.state.value.local)
        assertEquals(listOf(1L, 2L, 3L), ids(viewModel))
        assertEquals("halo, apa kabar", viewModel.state.value.timeline.messages.last().body)
        assertEquals("me", viewModel.state.value.timeline.messages.last().senderId)
    }

    @Test
    fun `the text inside is kept as the person wrote it`() {
        val viewModel = viewModel()

        viewModel.send("  baris satu\n  baris dua  ")

        assertEquals(listOf("baris satu\n  baris dua"), api.sendBodies)
    }

    @Test
    fun `an empty or blank message is not sent`() {
        val viewModel = viewModel()

        assertFalse(viewModel.send(""))
        assertFalse(viewModel.send("   \n "))

        assertTrue(api.sendKeys.isEmpty())
    }

    @Test
    fun `each new message gets a new key`() {
        val viewModel = viewModel()

        viewModel.send("satu")
        viewModel.send("dua")

        assertEquals(2, api.sendKeys.size)
        assertNotEquals(api.sendKeys[0], api.sendKeys[1])
        assertEquals(listOf(1L, 2L, 3L, 4L), ids(viewModel))
    }

    @Test
    fun `a second message is refused while the first is being sent, and works after it ends`() {
        val gate = CompletableDeferred<Unit>()
        api.sendGate = gate
        val viewModel = viewModel()

        assertTrue(viewModel.send("satu"))
        assertTrue(viewModel.state.value.local?.status is LocalStatus.Sending)
        assertFalse(viewModel.state.value.canCompose)
        assertFalse(viewModel.send("dua"))
        assertEquals(1, api.sendKeys.size)

        gate.complete(Unit)
        api.sendGate = null

        assertTrue(viewModel.state.value.canCompose)
        assertTrue(viewModel.send("dua"))
        assertEquals(listOf("satu", "dua"), api.sendBodies)
        assertEquals(listOf(1L, 2L, 3L, 4L), ids(viewModel))
    }

    @Test
    fun `a failed message stays, blocks the next one, and retry sends it again with the same key`() {
        var attempts = 0
        api.sendReply = { body -> if (++attempts == 1) throw IOException("putus") else ok(message(3, senderId = "me", body = body)) }
        val viewModel = viewModel()

        viewModel.send("halo")

        val local = viewModel.state.value.local
        assertEquals("halo", local?.pending?.body)
        assertEquals(LocalStatus.Failed(R.string.error_network), local?.status)
        assertEquals(listOf(1L, 2L), ids(viewModel))
        assertFalse(viewModel.state.value.canCompose)
        assertFalse(viewModel.send("lain"))
        assertEquals(1, api.sendKeys.size)

        viewModel.retry()

        assertEquals(listOf(api.sendKeys[0], api.sendKeys[0]), api.sendKeys)
        assertEquals(listOf("halo", "halo"), api.sendBodies)
        assertNull(viewModel.state.value.local)
        assertEquals(listOf(1L, 2L, 3L), ids(viewModel))
        assertTrue(viewModel.state.value.canCompose)
    }

    @Test
    fun `retry keeps failing with the same key until the server answers`() {
        api.sendReply = { throw IOException("putus") }
        val viewModel = viewModel()

        viewModel.send("halo")
        viewModel.retry()
        viewModel.retry()

        assertEquals(3, api.sendKeys.size)
        assertEquals(1, api.sendKeys.toSet().size)
        assertTrue(viewModel.state.value.local?.status is LocalStatus.Failed)
    }

    @Test
    fun `retry does nothing when no message failed`() {
        val viewModel = viewModel()

        viewModel.retry()

        assertTrue(api.sendKeys.isEmpty())
    }

    @Test
    fun `a failed message that is discarded lets the next message go out with a new key`() {
        var attempts = 0
        api.sendReply = { body -> if (++attempts == 1) throw IOException("putus") else ok(message(3, senderId = "me", body = body)) }
        val viewModel = viewModel()
        viewModel.send("pertama")

        viewModel.discardFailed()
        assertNull(viewModel.state.value.local)
        assertTrue(viewModel.send("kedua"))

        assertNotEquals(api.sendKeys[0], api.sendKeys[1])
        assertEquals("kedua", viewModel.state.value.timeline.messages.last().body)
    }

    @Test
    fun `a message that is being sent cannot be discarded`() {
        val gate = CompletableDeferred<Unit>()
        api.sendGate = gate
        val viewModel = viewModel()
        viewModel.send("halo")

        viewModel.discardFailed()

        assertNotNull(viewModel.state.value.local)
        gate.complete(Unit)
    }

    @Test
    fun `a server error keeps the message for a retry with the same key`() {
        var attempts = 0
        api.sendReply = { body ->
            if (++attempts == 1) failure(503, "SERVER_TIMEOUT") else ok(message(3, senderId = "me", body = body))
        }
        val viewModel = viewModel()

        viewModel.send("halo")
        assertEquals(LocalStatus.Failed(R.string.error_unavailable), viewModel.state.value.local?.status)

        viewModel.retry()
        assertNull(viewModel.state.value.local)
        assertEquals(api.sendKeys[0], api.sendKeys[1])
    }

    @Test
    fun `a closed conversation gives the text back, closes sending and says why`() {
        api.sendReply = { failure(409, "CHAT_SENDING_CLOSED") }
        val viewModel = viewModel()

        viewModel.send("halo")

        val state = viewModel.state.value
        assertNull(state.local)
        assertFalse(state.canSend)
        assertFalse(state.canCompose)
        assertEquals("halo", state.restoreDraft)
        assertEquals(R.string.error_chat_sending_closed, state.notice?.res)

        viewModel.draftRestored()
        viewModel.noticeShown()
        assertNull(viewModel.state.value.restoreDraft)
        assertNull(viewModel.state.value.notice)
        assertFalse(viewModel.send("lagi"))
    }

    @Test
    fun `a conversation that is gone while sending is final and the text is given back`() {
        api.sendReply = { failure(404, "CHAT_CONVERSATION_NOT_FOUND") }
        val viewModel = viewModel()

        viewModel.send("halo")

        val state = viewModel.state.value
        assertTrue(state.gone)
        assertNull(state.local)
        assertEquals("halo", state.restoreDraft)
    }

    @Test
    fun `a request that is still running waits two seconds and is sent once more with the same key`() {
        var attempts = 0
        api.sendReply = { body ->
            if (++attempts == 1) failure(409, "IDEMPOTENCY_IN_PROGRESS") else ok(message(3, senderId = "me", body = body))
        }
        val viewModel = viewModel()

        viewModel.send("halo")

        assertEquals(listOf(2_000L), pauses)
        assertEquals(2, api.sendKeys.size)
        assertEquals(api.sendKeys[0], api.sendKeys[1])
        assertNull(viewModel.state.value.local)
        assertEquals(listOf(1L, 2L, 3L), ids(viewModel))
    }

    @Test
    fun `a request that is still running after the second try is shown as failed and can be retried`() {
        var attempts = 0
        api.sendReply = { body ->
            if (++attempts <= 2) failure(409, "IDEMPOTENCY_IN_PROGRESS") else ok(message(3, senderId = "me", body = body))
        }
        val viewModel = viewModel()

        viewModel.send("halo")

        assertEquals(LocalStatus.Failed(R.string.error_request_in_progress), viewModel.state.value.local?.status)
        assertEquals(listOf(2_000L), pauses)

        viewModel.retry()
        assertNull(viewModel.state.value.local)
        assertEquals(1, api.sendKeys.toSet().size)
    }

    @Test
    fun `a key that was used for another body is replaced so the retry is accepted`() {
        var attempts = 0
        api.sendReply = { body ->
            if (++attempts == 1) failure(422, "IDEMPOTENCY_KEY_REUSED") else ok(message(3, senderId = "me", body = body))
        }
        val viewModel = viewModel()

        viewModel.send("halo")
        assertEquals(LocalStatus.Failed(R.string.error_key_reused), viewModel.state.value.local?.status)

        viewModel.retry()

        assertNotEquals(api.sendKeys[0], api.sendKeys[1])
        assertEquals(listOf("halo", "halo"), api.sendBodies)
        assertNull(viewModel.state.value.local)
    }

    @Test
    fun `a rate limit waits Retry-After with a countdown and sends again with the same key`() {
        var attempts = 0
        api.sendReply = { body ->
            if (++attempts == 1) failure(429, "RATE_LIMITED", retryAfter = "2") else ok(message(3, senderId = "me", body = body))
        }
        val seen = mutableListOf<Int?>()
        lateinit var viewModel: ChatViewModel
        viewModel = ChatViewModel(
            repository,
            openInfo,
            ownUserId = "me",
            pause = { millis ->
                pauses += millis
                seen += (viewModel.state.value.local?.status as? LocalStatus.Sending)?.waitSeconds
            },
            jitter = { 0L }
        )

        viewModel.send("halo")

        assertEquals(listOf(1_000L, 1_000L), pauses)
        assertEquals(listOf<Int?>(2, 1), seen)
        assertEquals(2, api.sendKeys.size)
        assertEquals(api.sendKeys[0], api.sendKeys[1])
        assertNull(viewModel.state.value.local)
    }

    @Test
    fun `a rate limit that does not end after three waits is shown as failed and the message is kept`() {
        api.sendReply = { failure(429, "RATE_LIMITED", retryAfter = "1") }
        val viewModel = viewModel()

        viewModel.send("halo")

        assertEquals(4, api.sendKeys.size)
        assertEquals(1, api.sendKeys.toSet().size)
        assertEquals(LocalStatus.Failed(R.string.error_rate_limited), viewModel.state.value.local?.status)
        assertEquals("halo", viewModel.state.value.local?.pending?.body)
    }

    @Test
    fun `a validation error is shown and the message is kept`() {
        api.sendReply = { failure(400, "VALIDATION_ERROR") }
        val viewModel = viewModel()

        viewModel.send("halo")

        assertEquals(LocalStatus.Failed(R.string.error_validation), viewModel.state.value.local?.status)
    }

    @Test
    fun `a conversation that is closed from the start does not send`() {
        val viewModel = viewModel(openInfo.copy(canSend = false))

        assertFalse(viewModel.state.value.canCompose)
        assertFalse(viewModel.send("halo"))
        assertTrue(api.sendKeys.isEmpty())
    }

    @Test
    fun `nothing is sent before the first load worked`() {
        api.messagesReply = { _, _ -> throw IOException("putus") }
        val viewModel = viewModel()

        assertFalse(viewModel.state.value.canCompose)
        assertFalse(viewModel.send("halo"))
        assertTrue(api.sendKeys.isEmpty())
    }

    @Test
    fun `a message of more than 4000 characters is refused with a notice and 4000 is allowed`() {
        val viewModel = viewModel()

        assertFalse(viewModel.send("a".repeat(4_001)))
        assertEquals(R.string.chat_error_terlalu_panjang, viewModel.state.value.notice?.res)
        assertTrue(api.sendKeys.isEmpty())

        assertTrue(viewModel.send("a".repeat(4_000)))
        assertEquals(1, api.sendKeys.size)
    }

    // Hapus pesan sendiri

    @Test
    fun `my message is deleted and then shows as deleted in place`() {
        api.deleteReply = { id -> ok(message(id, senderId = "me", deleted = true)) }
        val viewModel = viewModel()

        viewModel.delete(2)

        assertEquals(listOf(2L), api.deleted)
        val deleted = viewModel.state.value.timeline.messages[1]
        assertEquals(2L, deleted.id)
        assertTrue(deleted.deleted)
        assertEquals("", deleted.body)
        assertNull(viewModel.state.value.deletingId)
        assertEquals(listOf(1L, 2L), ids(viewModel))
    }

    @Test
    fun `a message of the other person, an unknown message and a deleted message are not sent to the server`() {
        api.deleteReply = { id -> ok(message(id, senderId = "me", deleted = true)) }
        val viewModel = viewModel()

        viewModel.delete(1)
        viewModel.delete(99)
        assertTrue(api.deleted.isEmpty())

        viewModel.delete(2)
        viewModel.delete(2)
        assertEquals(listOf(2L), api.deleted)
    }

    @Test
    fun `a delete that the server refuses keeps the message and says why`() {
        api.deleteReply = { failure(403, "AUTH_FORBIDDEN") }
        val viewModel = viewModel()

        viewModel.delete(2)

        assertFalse(viewModel.state.value.timeline.messages[1].deleted)
        assertEquals(R.string.error_forbidden, viewModel.state.value.notice?.res)
        assertNull(viewModel.state.value.deletingId)
    }

    @Test
    fun `a delete when the connection is lost keeps the message and says so`() {
        api.deleteReply = { throw IOException("putus") }
        val viewModel = viewModel()

        viewModel.delete(2)

        assertFalse(viewModel.state.value.timeline.messages[1].deleted)
        assertEquals(R.string.error_network, viewModel.state.value.notice?.res)
        assertNull(viewModel.state.value.deletingId)
    }

    @Test
    fun `deleting a message the server no longer has reloads the latest messages`() {
        api.deleteReply = { failure(404, "CHAT_MESSAGE_NOT_FOUND") }
        val viewModel = viewModel()

        viewModel.delete(2)

        assertEquals(R.string.error_chat_message_not_found, viewModel.state.value.notice?.res)
        assertEquals(listOf<Pair<Long?, Long?>>(null to null, null to null), api.messageCalls)
    }

    // Tanda baca

    @Test
    fun `nothing is marked read until the screen is visible, and then the newest message is marked once`() {
        val viewModel = viewModel()
        assertTrue(api.reads.isEmpty())

        viewModel.setVisible(true)
        assertEquals(listOf(2L), api.reads)

        viewModel.setVisible(true)
        viewModel.setVisible(false)
        viewModel.setVisible(true)
        assertEquals(listOf(2L), api.reads)
    }

    @Test
    fun `a new message of the other person that a poll brings is marked read, and one of mine is not`() = runBlocking {
        api.messagesReply = { after, _ ->
            when (after) {
                null -> messages(message(1, senderId = "u2"), message(2, senderId = "me"))
                2L -> messages(message(3, senderId = "u2"))
                else -> messages(message(4, senderId = "me"))
            }
        }
        val viewModel = viewModel()
        viewModel.setVisible(true)
        assertEquals(listOf(2L), api.reads)

        viewModel.poll()
        assertEquals(listOf(2L, 3L), api.reads)

        viewModel.poll()
        assertEquals(listOf(2L, 3L), api.reads)
    }

    @Test
    fun `a message that arrives while the person reads older ones is marked once they are back at the bottom`() = runBlocking {
        api.messagesReply = { after, _ ->
            if (after == null) messages(message(1, senderId = "u2"), message(2, senderId = "me")) else messages(message(3, senderId = "u2"))
        }
        val viewModel = viewModel()
        viewModel.setVisible(true)

        viewModel.setAtBottom(false)
        viewModel.poll()
        assertEquals(listOf(2L), api.reads)

        viewModel.setAtBottom(true)
        assertEquals(listOf(2L, 3L), api.reads)
    }

    @Test
    fun `a conversation with only my own messages sends no mark`() {
        api.messagesReply = { _, _ -> messages(message(1, senderId = "me"), message(2, senderId = "me")) }
        val viewModel = viewModel()

        viewModel.setVisible(true)

        assertTrue(api.reads.isEmpty())
    }

    @Test
    fun `a mark that failed is tried again the next time there is a chance`() {
        var failing = true
        api.readReply = { id -> if (failing) failure(500, "INTERNAL_ERROR") else ok(ChatReadState(id, 0)) }
        val viewModel = viewModel()

        viewModel.setVisible(true)
        assertEquals(listOf(2L), api.reads)

        failing = false
        viewModel.setVisible(true)
        assertEquals(listOf(2L, 2L), api.reads)

        viewModel.setVisible(true)
        assertEquals(listOf(2L, 2L), api.reads)
    }

    @Test
    fun `no mark is sent before the first load worked`() {
        api.messagesReply = { _, _ -> throw IOException("putus") }
        val viewModel = viewModel()

        viewModel.setVisible(true)

        assertTrue(api.reads.isEmpty())
    }

    // Keadaan

    @Test
    fun `the box and the send button follow the load, the state of the conversation and a waiting message`() {
        val closed = viewModel(openInfo.copy(canSend = false)).state.value
        val open = viewModel().state.value

        assertFalse(closed.canCompose)
        assertTrue(open.canCompose)
        assertEquals(openInfo.peerLastReadMessageId, open.peerLastReadMessageId)
    }
}
