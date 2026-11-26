package com.nthb.kibooz.data.remote

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * Satu koneksi WebSocket ke server chat, dipisah dari [ChatSocket] supaya aturan sambung ulang bisa dites tanpa
 * jaringan. Semua callback datang dari thread mana pun.
 */
interface ChatSocketTransport {
    /** Membuka koneksi baru. Header `Authorization` diisi oleh klien, token tidak pernah ada di alamat. */
    fun connect(listener: Listener): Connection

    interface Listener {
        fun onOpen()
        fun onText(text: String)

        /** Server menutup koneksi dengan [code]. */
        fun onClosed(code: Int)

        /** Handshake ditolak dengan [httpStatus], atau koneksi putus tanpa jawaban HTTP (null). */
        fun onFailure(httpStatus: Int?)
    }

    interface Connection {
        /** Menutup koneksi dengan kode 1000. Aman dipanggil berulang. */
        fun close()
    }
}

/** Alamat `GET chat/ws` di bawah alamat dasar API, yang berakhir dengan garis miring. */
fun chatSocketUrl(apiBaseUrl: String): HttpUrl =
    requireNotNull(apiBaseUrl.toHttpUrl().resolve("chat/ws")) { "Alamat dasar API tidak valid" }

/**
 * Transport dengan OkHttp. [baseClient] adalah klien API, jadi socket memakai TLS dan connection spec yang sama,
 * [AuthTokenInterceptor] mengisi header token, dan [TokenAuthenticator] memperbarui token sekali saat handshake
 * dijawab 401. Klien juga mengirim ping tiap [PING_SECONDS] detik. Ping dari server hanya dibalas, jadi tanpa ping
 * sendiri jaringan yang putus diam-diam tidak pernah ketahuan dan polling cadangan tidak pernah kembali.
 */
class OkHttpChatSocketTransport(baseClient: OkHttpClient, private val url: HttpUrl) : ChatSocketTransport {

    private val client: OkHttpClient = baseClient.newBuilder()
        .pingInterval(PING_SECONDS, TimeUnit.SECONDS)
        .build()

    override fun connect(listener: ChatSocketTransport.Listener): ChatSocketTransport.Connection {
        // OkHttp memanggil onClosing lalu onClosed, dan listener hanya perlu mendengar akhir koneksi sekali
        val finished = AtomicBoolean(false)
        val socket = client.newWebSocket(
            Request.Builder().url(url).build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) = listener.onOpen()

                override fun onMessage(webSocket: WebSocket, text: String) = listener.onText(text)

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    if (finished.compareAndSet(false, true)) listener.onClosed(code)
                    webSocket.close(NORMAL_CLOSE, null)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (finished.compareAndSet(false, true)) listener.onClosed(code)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (finished.compareAndSet(false, true)) listener.onFailure(response?.code)
                }
            }
        )

        return object : ChatSocketTransport.Connection {
            override fun close() {
                socket.close(NORMAL_CLOSE, null)
            }
        }
    }

    private companion object {
        const val NORMAL_CLOSE = 1000
        const val PING_SECONDS = 30L
    }
}
