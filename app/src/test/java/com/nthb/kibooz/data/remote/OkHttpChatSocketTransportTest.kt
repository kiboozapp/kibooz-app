package com.nthb.kibooz.data.remote

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import okhttp3.Authenticator
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout

/** Transport OkHttp terhadap server tiruan yang bisa meningkatkan koneksi ke WebSocket. */
class OkHttpChatSocketTransportTest {

    @get:Rule
    val timeout: Timeout = Timeout(60, TimeUnit.SECONDS)

    private lateinit var server: MockWebServer
    private val events = LinkedBlockingQueue<String>()

    private val listener = object : ChatSocketTransport.Listener {
        override fun onOpen() {
            events += "open"
        }

        override fun onText(text: String) {
            events += "text $text"
        }

        override fun onClosed(code: Int) {
            events += "closed $code"
        }

        override fun onFailure(httpStatus: Int?) {
            events += "failure $httpStatus"
        }
    }

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun transport(token: String? = "jwt", authenticator: Authenticator? = null): OkHttpChatSocketTransport {
        val base = server.url("/api/v1/").toString()
        val client = ApiClient(
            tokenProvider = { token },
            authenticator = authenticator,
            baseUrl = base,
            logLevel = HttpLoggingInterceptor.Level.NONE
        )
        return OkHttpChatSocketTransport(client.okHttpClient, chatSocketUrl(base))
    }

    private fun next(): String = requireNotNull(events.poll(10, TimeUnit.SECONDS)) { "tidak ada event dalam 10 detik" }

    private fun upgrade(afterOpen: (WebSocket) -> Unit = {}): MockResponse =
        MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) = afterOpen(webSocket)
        })

    @Test
    fun `the address is the chat socket route under the API base and holds no token`() {
        assertEquals(
            "https://api.example.org/api/v1/chat/ws",
            chatSocketUrl("https://api.example.org/api/v1/").toString()
        )
    }

    @Test
    fun `it opens with the bearer header, hands over frames, and reports the close code once`() {
        server.enqueue(
            upgrade { socket ->
                socket.send("""{"type":"ready"}""")
                socket.close(1001, "going away")
            }
        )

        val connection = transport().connect(listener)

        assertEquals("open", next())
        assertEquals("""text {"type":"ready"}""", next())
        assertEquals("closed 1001", next())

        val request = server.takeRequest(10, TimeUnit.SECONDS)
        assertNotNull(request)
        assertEquals("/api/v1/chat/ws", request?.path)
        assertEquals("Bearer jwt", request?.getHeader("Authorization"))
        assertEquals("websocket", request?.getHeader("Upgrade"))

        connection.close()
    }

    @Test
    fun `a refused handshake reports the status`() {
        server.enqueue(MockResponse().setResponseCode(503))

        transport().connect(listener)

        assertEquals("failure 503", next())
    }

    @Test
    fun `a handshake that is answered 401 is retried once with the refreshed token`() {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(upgrade { it.send("""{"type":"ready"}""") })

        val connection = transport(token = "old", authenticator = TokenAuthenticator { "fresh" }).connect(listener)

        assertEquals("open", next())
        assertEquals("""text {"type":"ready"}""", next())
        assertEquals("Bearer old", server.takeRequest(10, TimeUnit.SECONDS)?.getHeader("Authorization"))
        assertEquals("Bearer fresh", server.takeRequest(10, TimeUnit.SECONDS)?.getHeader("Authorization"))

        connection.close()
    }

    @Test
    fun `a 401 that stays 401 after the refresh is reported`() {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(401))

        transport(token = "old", authenticator = TokenAuthenticator { "fresh" }).connect(listener)

        assertEquals("failure 401", next())
    }
}
