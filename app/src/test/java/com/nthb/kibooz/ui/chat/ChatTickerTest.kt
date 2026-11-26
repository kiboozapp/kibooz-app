package com.nthb.kibooz.ui.chat

import com.nthb.kibooz.data.remote.ChatFrame
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout

class ChatTickerTest {

    @get:Rule
    val timeout: Timeout = Timeout(30, TimeUnit.SECONDS)

    private val link = FakeChatLink()
    private val pause = ManualPause()
    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private val ticks = mutableListOf<ChatTick>()
    private var hold: CompletableDeferred<Unit>? = null
    private var tickDone = true
    private var gap: suspend (Long) -> Unit = {}
    private var job: Job? = null

    @After
    fun tearDown() {
        runBlocking { job?.cancelAndJoin() }
    }

    /** Berjalan langsung sampai coroutine menunggu, jadi tes membaca keadaan sesudah setiap langkah. */
    private fun start(
        catchUp: Boolean = true,
        tickFor: (ChatFrame) -> ChatTick? = { it.tickForConversation("cv1", "me") }
    ) {
        val ticker = ChatTicker(
            link,
            pollMillis = 5_000L,
            safetyMillis = 30_000L,
            catchUpOnStart = catchUp,
            pause = { pause.pause(it) },
            resyncJitter = { 2_500L },
            retryMillis = 4_000L,
            minGapMillis = 1_500L,
            gap = { gap(it) }
        )
        job = scope.launch {
            ticker.run(
                tickFor = tickFor,
                onTick = {
                    ticks += it
                    hold?.await()
                    tickDone
                }
            )
        }
    }

    /** Menahan tick yang sedang berjalan sampai [releaseHold]. */
    private fun holdTicks() {
        hold = CompletableDeferred()
    }

    private fun releaseHold() {
        val gate = hold
        hold = null
        gate?.complete(Unit)
    }

    // Selagi socket putus

    @Test
    fun `while the socket is down it ticks for new messages every five seconds`() {
        start()
        assertEquals(listOf(5_000L), pause.waits)
        assertTrue(ticks.isEmpty())

        pause.releaseAll()

        assertEquals(listOf(ChatTick.NEW), ticks)
        assertEquals(listOf(5_000L, 5_000L), pause.waits)
    }

    @Test
    fun `it holds the socket while it runs and lets go when it is cancelled`() {
        start()
        assertEquals(1, link.acquired)
        assertEquals(0, link.released)

        runBlocking { job?.cancelAndJoin() }

        assertEquals(1, link.released)
    }

    @Test
    fun `a screen that starts with a socket that is down gets no catch up tick`() {
        start()

        assertTrue(ticks.isEmpty())
    }

    // Selagi socket tersambung

    @Test
    fun `while the socket is up the timer is slow and each timer tick reloads`() {
        link.connected.value = true
        start(catchUp = false)
        assertEquals(listOf(30_000L), pause.waits)

        pause.releaseAll()

        assertEquals(listOf(ChatTick.RELOAD), ticks)
    }

    @Test
    fun `the timer changes to the poll interval when the socket drops and back when it returns`() {
        start()
        assertEquals(listOf(5_000L), pause.waits)

        link.connected.value = true
        assertEquals(listOf(5_000L, 30_000L), pause.waits)

        link.connected.value = false
        assertEquals(listOf(5_000L, 30_000L, 5_000L), pause.waits)
    }

    @Test
    fun `a screen that starts with a socket that is already up catches up once`() {
        link.connected.value = true

        start()

        assertEquals(listOf(ChatTick.RELOAD), ticks)
    }

    @Test
    fun `a screen can skip the catch up tick`() {
        link.connected.value = true

        start(catchUp = false)

        assertTrue(ticks.isEmpty())
    }

    // Frame

    @Test
    fun `a ready frame reloads at once`() {
        start()

        link.emit(ChatFrame.Ready)

        assertEquals(listOf(ChatTick.RELOAD), ticks)
    }

    @Test
    fun `a new message of this conversation ticks for new messages without waiting`() {
        start()

        link.emit(ChatFrame.NewMessage("cv1"))

        assertEquals(listOf(ChatTick.NEW), ticks)
    }

    @Test
    fun `frames that mean nothing for this screen make no tick`() {
        start()

        link.emit(ChatFrame.NewMessage("cv2"))
        link.emit(ChatFrame.MessageDeleted("cv2"))
        link.emit(ChatFrame.Read("cv1", "me"))

        assertTrue(ticks.isEmpty())
    }

    @Test
    fun `a burst of frames while a tick runs becomes one more tick`() {
        holdTicks()
        start()

        link.emit(ChatFrame.NewMessage("cv1"))
        assertEquals(listOf(ChatTick.NEW), ticks)

        repeat(5) { link.emit(ChatFrame.NewMessage("cv1")) }
        assertEquals(listOf(ChatTick.NEW), ticks)

        releaseHold()

        assertEquals(listOf(ChatTick.NEW, ChatTick.NEW), ticks)
    }

    @Test
    fun `a reload wins over new message ticks that wait with it`() {
        holdTicks()
        start()
        link.emit(ChatFrame.NewMessage("cv1"))

        link.emit(ChatFrame.NewMessage("cv1"))
        link.emit(ChatFrame.MessageDeleted("cv1"))
        link.emit(ChatFrame.NewMessage("cv1"))

        releaseHold()

        assertEquals(listOf(ChatTick.NEW, ChatTick.RELOAD), ticks)
    }

    @Test
    fun `a resync waits a random time, and resyncs that come close together make one reload`() {
        start()

        link.emit(ChatFrame.Resync)
        link.emit(ChatFrame.Resync)
        link.emit(ChatFrame.Resync)

        assertEquals(1, pause.waits.count { it == 2_500L })
        assertTrue(ticks.isEmpty())

        pause.release(2_500L)

        assertEquals(listOf(ChatTick.RELOAD), ticks)
    }

    @Test
    fun `a resync that comes after the first one finished waits again`() {
        start()

        link.emit(ChatFrame.Resync)
        pause.release(2_500L)
        link.emit(ChatFrame.Resync)

        assertEquals(2, pause.waits.count { it == 2_500L })
    }

    @Test
    fun `the list filter reloads for what changes the list and for nothing else`() {
        start(tickFor = { it.tickForList("me") })

        link.emit(ChatFrame.NewMessage("cv9"))
        link.emit(ChatFrame.Read("cv9", "u2"))

        assertEquals(listOf(ChatTick.RELOAD), ticks)
    }

    // Ulang dan jarak

    @Test
    fun `a tick that is not done is asked again after a short wait while the socket is up`() {
        link.connected.value = true
        start(catchUp = false)
        tickDone = false

        link.emit(ChatFrame.MessageDeleted("cv1"))
        assertEquals(listOf(ChatTick.RELOAD), ticks)
        assertEquals(1, pause.waits.count { it == 4_000L })

        tickDone = true
        pause.release(4_000L)

        assertEquals(listOf(ChatTick.RELOAD, ChatTick.RELOAD), ticks)
        assertEquals(1, pause.waits.count { it == 4_000L })
    }

    @Test
    fun `while the socket is down a tick that is not done is left to the poll`() {
        start()
        tickDone = false

        pause.releaseAll()

        assertEquals(listOf(ChatTick.NEW), ticks)
        assertEquals(0, pause.waits.count { it == 4_000L })
    }

    @Test
    fun `frames that arrive during the gap after a tick become one tick`() {
        val gate = CompletableDeferred<Unit>()
        val gaps = mutableListOf<Long>()
        gap = {
            gaps += it
            gate.await()
        }
        start()

        repeat(4) { link.emit(ChatFrame.NewMessage("cv1")) }
        assertEquals(listOf(ChatTick.NEW), ticks)
        assertEquals(listOf(1_500L), gaps)

        gate.complete(Unit)

        assertEquals(listOf(ChatTick.NEW, ChatTick.NEW), ticks)
    }

    // Aturan frame

    @Test
    fun `the conversation filter maps each frame`() {
        fun tick(frame: ChatFrame) = frame.tickForConversation("cv1", "me")

        assertEquals(ChatTick.RELOAD, tick(ChatFrame.Ready))
        assertEquals(ChatTick.RELOAD, tick(ChatFrame.Resync))
        assertEquals(ChatTick.NEW, tick(ChatFrame.NewMessage("cv1")))
        assertEquals(ChatTick.NEW, tick(ChatFrame.NewMessage(null)))
        assertEquals(ChatTick.NEW, tick(ChatFrame.NewMessage("")))
        assertNull(tick(ChatFrame.NewMessage("cv2")))
        assertEquals(ChatTick.RELOAD, tick(ChatFrame.MessageDeleted("cv1")))
        assertNull(tick(ChatFrame.MessageDeleted("cv2")))
        assertEquals(ChatTick.RELOAD, tick(ChatFrame.Read("cv1", "u2")))
        assertNull(tick(ChatFrame.Read("cv1", "me")))
        assertNull(tick(ChatFrame.Read("cv2", "u2")))
    }

    @Test
    fun `the list filter maps each frame`() {
        fun tick(frame: ChatFrame) = frame.tickForList("me")

        assertEquals(ChatTick.RELOAD, tick(ChatFrame.Ready))
        assertEquals(ChatTick.RELOAD, tick(ChatFrame.Resync))
        assertEquals(ChatTick.RELOAD, tick(ChatFrame.NewMessage("cv9")))
        assertEquals(ChatTick.RELOAD, tick(ChatFrame.MessageDeleted("cv9")))
        // Tanda baca dari perangkat lain milik sendiri mengubah jumlah belum dibaca, milik lawan bicara tidak
        assertEquals(ChatTick.RELOAD, tick(ChatFrame.Read("cv9", "me")))
        assertNull(tick(ChatFrame.Read("cv9", "u2")))
    }
}
