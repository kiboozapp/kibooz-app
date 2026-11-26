package com.nthb.kibooz.data.remote

import com.nthb.kibooz.ui.chat.ManualPause
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout

/** Satu koneksi palsu. Tes memainkan sisi server lewat [ready], [send], [closeBy], dan [fail]. */
private class FakeConnection(private val listener: ChatSocketTransport.Listener) : ChatSocketTransport.Connection {
    var closed = 0

    override fun close() {
        closed++
    }

    fun send(text: String) = listener.onText(text)

    fun ready() = send("""{"type":"ready","expiresAt":"2026-11-26T10:00:00Z"}""")

    fun closeBy(code: Int) = listener.onClosed(code)

    fun fail(httpStatus: Int? = null) = listener.onFailure(httpStatus)
}

private class FakeTransport : ChatSocketTransport {
    val connections = mutableListOf<FakeConnection>()

    val last: FakeConnection get() = connections.last()

    override fun connect(listener: ChatSocketTransport.Listener): ChatSocketTransport.Connection =
        FakeConnection(listener).also { connections += it }
}

class ChatSocketTest {

    @get:Rule
    val timeout: Timeout = Timeout(30, TimeUnit.SECONDS)

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val transport = FakeTransport()
    private val pause = ManualPause()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    private var clock = 0L
    private var token: String? = "t1"
    private val refreshed = mutableListOf<String>()

    /** Jawaban refresh. Mengembalikan token baru, atau null seperti saat jaringan putus. */
    private var onRefresh: (String) -> String? = { "t2".also { token = it } }

    private val socket = ChatSocket(
        transport = transport,
        tokenProvider = { token },
        refresh = { failed ->
            refreshed += failed
            onRefresh(failed)
        },
        moshi = moshi,
        scope = scope,
        now = { clock },
        pause = { pause.pause(it) },
        jitter = { 0L }
    )

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun framesInto(list: MutableList<ChatFrame>) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) { socket.frames.collect { list += it } }
    }

    /** Membuka koneksi, menerima `ready`, dan membiarkannya hidup cukup lama untuk dianggap sehat. */
    private fun healthyConnection(): FakeConnection {
        val connection = transport.last
        connection.ready()
        clock += HEALTHY_CONNECTION_MILLIS
        return connection
    }

    // Membuka dan menutup

    @Test
    fun `several holders share one connection and it closes after the grace wait when the last one lets go`() {
        socket.acquire()
        socket.acquire()
        assertEquals(1, transport.connections.size)

        socket.release()
        assertTrue(pause.waits.isEmpty())

        socket.release()
        assertEquals(listOf(CLOSE_GRACE_MILLIS), pause.waits)
        assertEquals(0, transport.last.closed)

        pause.release(CLOSE_GRACE_MILLIS)

        assertEquals(1, transport.last.closed)
        assertEquals(1, transport.connections.size)
    }

    @Test
    fun `a holder that comes back within the grace wait keeps the same connection`() {
        socket.acquire()
        socket.release()

        socket.acquire()
        pause.release(CLOSE_GRACE_MILLIS)

        assertEquals(1, transport.connections.size)
        assertEquals(0, transport.last.closed)
    }

    @Test
    fun `a holder that comes back after the connection closed opens a new one`() {
        socket.acquire()
        socket.release()
        pause.release(CLOSE_GRACE_MILLIS)

        socket.acquire()

        assertEquals(2, transport.connections.size)
    }

    @Test
    fun `a release with no holder does nothing`() {
        socket.release()

        assertTrue(pause.waits.isEmpty())
        assertTrue(transport.connections.isEmpty())
    }

    @Test
    fun `it does not connect with no token or with a developer mock token`() {
        token = null
        socket.acquire()
        assertTrue(transport.connections.isEmpty())

        token = "mock-guru"
        socket.release()
        socket.acquire()
        assertTrue(transport.connections.isEmpty())
    }

    // Frame

    @Test
    fun `it is connected from the ready frame until the connection ends, and frames reach the collectors`() {
        val frames = mutableListOf<ChatFrame>()
        framesInto(frames)
        socket.acquire()
        assertFalse(socket.connected.value)

        transport.last.ready()
        assertTrue(socket.connected.value)

        transport.last.send("""{"type":"message","conversationId":"cv1","message":{"id":5,"body":"halo"}}""")
        transport.last.send("""{"type":"resync"}""")
        assertEquals(listOf(ChatFrame.Ready, ChatFrame.NewMessage("cv1"), ChatFrame.Resync), frames)

        transport.last.closeBy(CLOSE_GOING_AWAY)
        assertFalse(socket.connected.value)
    }

    @Test
    fun `bad frames and typing frames are dropped and the connection carries on`() {
        val frames = mutableListOf<ChatFrame>()
        framesInto(frames)
        socket.acquire()

        transport.last.send("bukan json")
        transport.last.send("{}")
        transport.last.send("""{"type":"typing","conversationId":"cv1","typing":true}""")
        transport.last.send("[1]")

        assertTrue(frames.isEmpty())
        assertFalse(socket.connected.value)

        transport.last.ready()
        assertTrue(socket.connected.value)
        assertEquals(listOf<ChatFrame>(ChatFrame.Ready), frames)
    }

    // Sambung ulang

    @Test
    fun `a server that is going away is followed by a reconnect after the base wait`() {
        socket.acquire()
        healthyConnection().closeBy(CLOSE_GOING_AWAY)

        assertEquals(listOf(RECONNECT_BASE_MILLIS), pause.waits)
        assertEquals(1, transport.connections.size)

        pause.releaseAll()

        assertEquals(2, transport.connections.size)
    }

    @Test
    fun `connections that fail before ready wait longer each time up to the cap`() {
        socket.acquire()

        repeat(7) {
            transport.last.fail()
            pause.releaseAll()
        }

        assertEquals(listOf(2_000L, 4_000L, 8_000L, 16_000L, 32_000L, 60_000L, 60_000L), pause.waits)
        assertEquals(8, transport.connections.size)
    }

    @Test
    fun `a connection that was ready but short lived does not reset the wait`() {
        socket.acquire()
        transport.last.fail()
        pause.releaseAll()

        transport.last.ready()
        clock += HEALTHY_CONNECTION_MILLIS - 1
        transport.last.closeBy(CLOSE_GOING_AWAY)

        assertEquals(listOf(2_000L, 4_000L), pause.waits)
    }

    @Test
    fun `a connection that stayed up long enough resets the wait`() {
        socket.acquire()
        transport.last.fail()
        pause.releaseAll()
        transport.last.fail()
        pause.releaseAll()

        healthyConnection().closeBy(CLOSE_GOING_AWAY)

        assertEquals(listOf(2_000L, 4_000L, RECONNECT_BASE_MILLIS), pause.waits)
    }

    @Test
    fun `jitter is added on top of the wait`() {
        val jittered = ChatSocket(
            transport = transport,
            tokenProvider = { token },
            refresh = { null },
            moshi = moshi,
            scope = scope,
            now = { clock },
            pause = { pause.pause(it) },
            jitter = { bound -> bound / 2 }
        )

        jittered.acquire()
        transport.last.fail()

        assertEquals(listOf(3_000L), pause.waits)
    }

    @Test
    fun `a policy violation close is an app bug and it does not reconnect`() {
        socket.acquire()
        healthyConnection().closeBy(CLOSE_POLICY_VIOLATION)

        assertTrue(pause.waits.isEmpty())
        assertEquals(1, transport.connections.size)
        assertFalse(socket.connected.value)
    }

    @Test
    fun `a server that is busy is not asked again for at least five seconds`() {
        socket.acquire()

        transport.last.closeBy(CLOSE_TRY_AGAIN_LATER)
        pause.releaseAll()
        transport.last.fail(httpStatus = 503)

        assertEquals(listOf(BUSY_MIN_WAIT_MILLIS, BUSY_MIN_WAIT_MILLIS), pause.waits)
    }

    @Test
    fun `a proxy that refuses the upgrade is asked again only at the slowest pace`() {
        socket.acquire()

        transport.last.fail(httpStatus = 426)

        assertEquals(listOf(RECONNECT_MAX_MILLIS), pause.waits)
    }

    @Test
    fun `a handshake that is still refused with 401 backs off like any other failure`() {
        socket.acquire()

        transport.last.fail(httpStatus = 401)

        assertEquals(listOf(2_000L), pause.waits)
    }

    // Token

    @Test
    fun `a token that ended is refreshed with the failed token before the next connection`() {
        socket.acquire()
        healthyConnection().closeBy(CLOSE_TOKEN_ENDED)

        assertEquals(listOf(RECONNECT_BASE_MILLIS), pause.waits)
        assertTrue(refreshed.isEmpty())

        pause.releaseAll()

        assertEquals(listOf("t1"), refreshed)
        assertEquals(2, transport.connections.size)
    }

    @Test
    fun `the refresh names the token of the closed socket even when REST already replaced it`() {
        socket.acquire()
        val connection = healthyConnection()
        token = "t2"
        connection.closeBy(CLOSE_TOKEN_ENDED)

        pause.releaseAll()

        assertEquals(listOf("t1"), refreshed)
    }

    @Test
    fun `a refresh that fails while the session is alive backs off and tries again before it connects`() {
        socket.acquire()
        onRefresh = { null }
        healthyConnection().closeBy(CLOSE_TOKEN_ENDED)

        pause.releaseAll()
        assertEquals(listOf("t1"), refreshed)
        assertEquals(1, transport.connections.size)
        assertEquals(listOf(RECONNECT_BASE_MILLIS, 2_000L), pause.waits)

        onRefresh = { "t2".also { token = it } }
        pause.releaseAll()

        assertEquals(listOf("t1", "t1"), refreshed)
        assertEquals(2, transport.connections.size)
    }

    @Test
    fun `a refresh that ended the session stops the connection for good`() {
        socket.acquire()
        onRefresh = {
            token = null
            null
        }
        healthyConnection().closeBy(CLOSE_TOKEN_ENDED)

        pause.releaseAll()

        assertEquals(1, transport.connections.size)
        assertEquals(1, pause.waits.size)
    }

    @Test
    fun `it stops when the session ended while it waited to reconnect`() {
        socket.acquire()
        healthyConnection().closeBy(CLOSE_GOING_AWAY)

        token = null
        pause.releaseAll()

        assertEquals(1, transport.connections.size)
    }

    // Akun berganti

    @Test
    fun `a reset closes the connection and opens a new one while a screen is still there`() {
        socket.acquire()
        val first = transport.last
        first.ready()
        assertTrue(socket.connected.value)

        socket.reset()

        assertEquals(1, first.closed)
        assertEquals(2, transport.connections.size)
        assertFalse(socket.connected.value)
    }

    @Test
    fun `a late ready frame of the old connection does not make the new one look connected`() {
        val frames = mutableListOf<ChatFrame>()
        framesInto(frames)
        socket.acquire()
        val first = transport.last

        socket.reset()
        first.ready()

        assertFalse(socket.connected.value)
        assertTrue(frames.isEmpty())
    }

    @Test
    fun `a reset with no screen opens nothing`() {
        socket.reset()

        assertTrue(transport.connections.isEmpty())
    }

    // Aturan murni

    @Test
    fun `the wait doubles from one second and stops growing at one minute`() {
        val waits = (0..8).map { reconnectWait(it, jitter = { 0L }) }

        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 32_000L, 60_000L, 60_000L, 60_000L), waits)
    }

    @Test
    fun `jitter is drawn from one wait and added to it, and a floor lifts a short wait`() {
        val bounds = mutableListOf<Long>()

        val wait = reconnectWait(1, floorMillis = 5_000L, jitter = { bound ->
            bounds += bound
            7L
        })

        assertEquals(listOf(5_000L), bounds)
        assertEquals(5_007L, wait)
    }

    @Test
    fun `the plan follows the close code and the handshake answer`() {
        val none: (Long) -> Long = { 0L }

        assertEquals(ReconnectPlan.Stop, planReconnect(ChatSocketEnd(closeCode = 1008), 1, none))
        assertEquals(ReconnectPlan.Wait(1_000L, refreshFirst = true), planReconnect(ChatSocketEnd(closeCode = 4001), 0, none))
        assertEquals(ReconnectPlan.Wait(1_000L), planReconnect(ChatSocketEnd(closeCode = 1001), 0, none))
        assertEquals(ReconnectPlan.Wait(1_000L), planReconnect(ChatSocketEnd(closeCode = 1000), 0, none))
        assertEquals(ReconnectPlan.Wait(1_000L), planReconnect(ChatSocketEnd(closeCode = 1006), 0, none))
        assertEquals(ReconnectPlan.Wait(5_000L), planReconnect(ChatSocketEnd(closeCode = 1013), 1, none))
        assertEquals(ReconnectPlan.Wait(5_000L), planReconnect(ChatSocketEnd(httpStatus = 503), 1, none))
        assertEquals(ReconnectPlan.Wait(60_000L), planReconnect(ChatSocketEnd(httpStatus = 426), 1, none))
        assertEquals(ReconnectPlan.Wait(2_000L), planReconnect(ChatSocketEnd(httpStatus = 401), 1, none))
        assertEquals(ReconnectPlan.Wait(2_000L), planReconnect(ChatSocketEnd(), 1, none))
    }
}
