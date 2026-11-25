package com.nthb.kibooz.data.repository

import com.nthb.kibooz.data.failureJson
import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.ChatApi
import com.squareup.moshi.Moshi
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout

class ChatRepositoryTest {

    @get:Rule
    val timeout: Timeout = Timeout(30, TimeUnit.SECONDS)

    private lateinit var server: MockWebServer
    private lateinit var repository: ChatRepository

    private fun personJson(id: String = "u2", name: String = "Bu Sari", role: String = "GURU", deleted: Boolean = false) =
        """{"id":"$id","fullName":"$name","role":"$role","avatarUrl":null,"deleted":$deleted}"""

    private fun messageJson(
        id: Long,
        body: String = "halo",
        senderId: String = "u2",
        deleted: Boolean = false,
        media: String? = null
    ) = """
        {"id":$id,"conversationId":"cv1","senderId":"$senderId","sender":${personJson(senderId)},"body":"$body",
         "media":${media ?: "null"},"deleted":$deleted,"createdAt":"2026-11-25T07:05:00Z"}
    """.trimIndent()

    private fun directJson(id: String = "cv1", lastId: Long? = 5, unread: Int = 2, peerRead: Long = 3, canSend: Boolean = true) = """
        {"id":"$id","kind":"direct","peer":${personJson()},"class":null,
         "lastMessage":${lastId?.let { messageJson(it) } ?: "null"},"unreadCount":$unread,"peerLastReadMessageId":$peerRead,
         "canSend":$canSend,"createdAt":"2026-11-20T00:00:00Z"}
    """.trimIndent()

    private fun roomJson(id: String = "cv9", canSend: Boolean = true) = """
        {"id":"$id","kind":"class","peer":null,"class":{"id":"c1","name":"Bunga"},
         "lastMessage":${messageJson(7, senderId = "u3")},"unreadCount":0,"peerLastReadMessageId":0,
         "canSend":$canSend,"createdAt":"2026-11-20T00:00:00Z"}
    """.trimIndent()

    private fun envelope(data: String) = """{"success":true,"data":$data}"""

    private fun listJson(key: String, items: List<String>, total: Int = items.size, page: Int = 1) =
        envelope("""{"page":$page,"limit":50,"total":$total,"$key":[${items.joinToString(",")}]}""")

    private fun messagesJson(vararg ids: Long, hasMore: Boolean = false) =
        envelope("""{"messages":[${ids.joinToString(",") { messageJson(it) }}],"hasMore":$hasMore}""")

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        val client = testClient(server)
        repository = ChatRepository(client.create(ChatApi::class.java), client.moshi)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun failure(status: Int, code: String) = jsonResponse(failureJson(code), status)

    /** Isi JSON request sebagai map, supaya tes bisa memastikan field yang kosong tidak dikirim. */
    @Suppress("UNCHECKED_CAST")
    private fun bodyOf(request: RecordedRequest): Map<String, Any?> =
        Moshi.Builder().build().adapter(Map::class.java).fromJson(request.body.readUtf8()) as Map<String, Any?>

    // GET /chat/contacts

    @Test
    fun `contacts reads the page and the names of the children and the shared classes`() = runBlocking {
        server.enqueue(
            jsonResponse(
                listJson(
                    "contacts",
                    listOf(
                        """{"id":"u5","fullName":"Pak Budi","role":"WALI","avatarUrl":null,"deleted":false,
                            "classes":[{"id":"c1","name":"Bunga"}],"children":["Rani","Dika"],"conversationId":null}""",
                        """{"id":"u6","fullName":"Bu Sari","role":"GURU","avatarUrl":"https://media.example.org/a.jpg","deleted":false,
                            "classes":[{"id":"c1","name":"Bunga"},{"id":"c2","name":"Mawar"}],"children":[],"conversationId":"cv1"}"""
                    )
                )
            )
        )

        val result = repository.contacts() as ApiResult.Success

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/v1/chat/contacts?page=1&limit=50", request.path)
        assertEquals(2, result.data.total)
        val parent = result.data.contacts[0]
        assertEquals(listOf("Rani", "Dika"), parent.children)
        assertEquals("Bunga", parent.classes.single().name)
        assertNull(parent.conversationId)
        val teacher = result.data.contacts[1]
        assertEquals("cv1", teacher.conversationId)
        assertEquals(2, teacher.classes.size)
        assertEquals("https://media.example.org/a.jpg", teacher.avatarUrl)
    }

    @Test
    fun `allContacts reads the next page until the total is met`() = runBlocking {
        server.enqueue(jsonResponse(listJson("contacts", listOf("""{"id":"u1","fullName":"A"}"""), total = 2, page = 1)))
        server.enqueue(jsonResponse(listJson("contacts", listOf("""{"id":"u2","fullName":"B"}"""), total = 2, page = 2)))

        val result = repository.allContacts() as ApiResult.Success

        assertEquals(listOf("u1", "u2"), result.data.map { it.id })
        assertEquals("/api/v1/chat/contacts?page=1&limit=50", server.takeRequest().path)
        assertEquals("/api/v1/chat/contacts?page=2&limit=50", server.takeRequest().path)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `allContacts returns the failure of the page that failed`() = runBlocking {
        server.enqueue(jsonResponse(listJson("contacts", listOf("""{"id":"u1","fullName":"A"}"""), total = 2)))
        server.enqueue(failure(403, "AUTH_FORBIDDEN"))

        val result = repository.allContacts() as ApiResult.Failure

        assertEquals("AUTH_FORBIDDEN", result.errorCode)
    }

    // GET /chat/conversations

    @Test
    fun `conversations reads a direct chat, a class room, unread counts and the read mark of the other person`() = runBlocking {
        server.enqueue(jsonResponse(listJson("conversations", listOf(directJson(), roomJson()))))

        val result = repository.conversations() as ApiResult.Success

        assertEquals("/api/v1/chat/conversations?page=1&limit=50", server.takeRequest().path)
        val direct = result.data.conversations[0]
        assertFalse(direct.isRoom)
        assertEquals("Bu Sari", direct.peer?.fullName)
        assertNull(direct.classRoom)
        assertEquals(2, direct.unreadCount)
        assertEquals(3L, direct.peerLastReadMessageId)
        assertEquals(5L, direct.lastMessage?.id)
        assertTrue(direct.canSend)

        val room = result.data.conversations[1]
        assertTrue(room.isRoom)
        assertNull(room.peer)
        assertEquals("Bunga", room.classRoom?.name)
        assertEquals(0L, room.peerLastReadMessageId)
    }

    @Test
    fun `a closed conversation reads canSend false and a null last message is accepted`() = runBlocking {
        server.enqueue(jsonResponse(listJson("conversations", listOf(directJson(lastId = null, canSend = false)))))

        val conversation = (repository.conversations() as ApiResult.Success).data.conversations.single()

        assertFalse(conversation.canSend)
        assertNull(conversation.lastMessage)
    }

    @Test
    fun `a deleted account reads as a deleted person`() = runBlocking {
        val deletedPeer = personJson(name = "Deleted account", deleted = true)
        server.enqueue(
            jsonResponse(
                listJson("conversations", listOf(directJson().replace(personJson(), deletedPeer)))
            )
        )

        val peer = (repository.conversations() as ApiResult.Success).data.conversations.single().peer

        assertTrue(peer!!.deleted)
    }

    @Test
    fun `allConversations reads every page`() = runBlocking {
        server.enqueue(jsonResponse(listJson("conversations", listOf(directJson("a")), total = 2, page = 1)))
        server.enqueue(jsonResponse(listJson("conversations", listOf(directJson("b")), total = 2, page = 2)))

        val result = repository.allConversations() as ApiResult.Success

        assertEquals(listOf("a", "b"), result.data.map { it.id })
    }

    @Test
    fun `findConversation stops on the page that holds it and answers null when the list has no such conversation`() = runBlocking {
        server.enqueue(jsonResponse(listJson("conversations", listOf(directJson("a")), total = 2, page = 1)))
        server.enqueue(jsonResponse(listJson("conversations", listOf(directJson("b", canSend = false)), total = 2, page = 2)))

        val found = (repository.findConversation("b") as ApiResult.Success).data

        assertNotNull(found)
        assertFalse(found!!.canSend)
        assertEquals(2, server.requestCount)

        server.enqueue(jsonResponse(listJson("conversations", listOf(directJson("a")), total = 1)))
        assertNull((repository.findConversation("zzz") as ApiResult.Success).data)
    }

    // POST /chat/conversations

    @Test
    fun `startDirect posts only the userId and reads both 201 and 200`() = runBlocking {
        server.enqueue(jsonResponse(envelope(directJson("new", lastId = null)), 201))
        server.enqueue(jsonResponse(envelope(directJson("new", lastId = null)), 200))

        val created = repository.startDirect("u5") as ApiResult.Success
        val again = repository.startDirect("u5") as ApiResult.Success

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/chat/conversations", request.path)
        assertEquals(mapOf<String, Any?>("userId" to "u5"), bodyOf(request))
        assertEquals("new", created.data.id)
        assertEquals(created.data.id, again.data.id)
    }

    @Test
    fun `openClassRoom posts only the classId`() = runBlocking {
        server.enqueue(jsonResponse(envelope(roomJson()), 201))

        val result = repository.openClassRoom("c1") as ApiResult.Success

        assertEquals(mapOf<String, Any?>("classId" to "c1"), bodyOf(server.takeRequest()))
        assertTrue(result.data.isRoom)
    }

    @Test
    fun `a contact or class that is not allowed answers CHAT_CONTACT_NOT_ALLOWED`() = runBlocking {
        server.enqueue(failure(403, "CHAT_CONTACT_NOT_ALLOWED"))
        server.enqueue(failure(403, "CHAT_CONTACT_NOT_ALLOWED"))

        val direct = repository.startDirect("nobody") as ApiResult.Failure
        val room = repository.openClassRoom("c9") as ApiResult.Failure

        assertEquals(403, direct.status)
        assertEquals("CHAT_CONTACT_NOT_ALLOWED", direct.errorCode)
        assertEquals("CHAT_CONTACT_NOT_ALLOWED", room.errorCode)
    }

    // GET /chat/conversations/{id}/messages

    @Test
    fun `latestMessages sends no cursor and reads messages in the order they were sent`() = runBlocking {
        server.enqueue(jsonResponse(messagesJson(4, 5, 6, hasMore = true)))

        val result = repository.latestMessages("cv1") as ApiResult.Success

        assertEquals("/api/v1/chat/conversations/cv1/messages?limit=50", server.takeRequest().path)
        assertEquals(listOf(4L, 5L, 6L), result.data.messages.map { it.id })
        assertTrue(result.data.hasMore)
        assertEquals("u2", result.data.messages[0].senderId)
        assertEquals("Bu Sari", result.data.messages[0].sender.fullName)
    }

    @Test
    fun `olderMessages sends before and newerMessages sends after, never both`() = runBlocking {
        server.enqueue(jsonResponse(messagesJson(1, 2)))
        server.enqueue(jsonResponse(messagesJson(7)))
        server.enqueue(jsonResponse(messagesJson()))

        repository.olderMessages("cv1", beforeId = 4)
        repository.newerMessages("cv1", afterId = 6)
        repository.newerMessages("cv1", afterId = 0, limit = 100)

        assertEquals("/api/v1/chat/conversations/cv1/messages?before=4&limit=50", server.takeRequest().path)
        assertEquals("/api/v1/chat/conversations/cv1/messages?after=6&limit=50", server.takeRequest().path)
        assertEquals("/api/v1/chat/conversations/cv1/messages?after=0&limit=100", server.takeRequest().path)
    }

    @Test
    fun `a deleted message keeps its place with an empty body and a media message reads its kind`() = runBlocking {
        val photo = """{"kind":"photo","contentType":"image/jpeg","sizeBytes":1200,"url":"https://media.example.org/p","expiresAt":"2026-11-25T08:00:00Z"}"""
        server.enqueue(
            jsonResponse(
                envelope(
                    """{"messages":[${messageJson(1, body = "", deleted = true)},${messageJson(2, body = "", media = photo)}],"hasMore":false}"""
                )
            )
        )

        val messages = (repository.latestMessages("cv1") as ApiResult.Success).data.messages

        assertTrue(messages[0].deleted)
        assertEquals("", messages[0].body)
        assertNull(messages[0].media)
        assertEquals("photo", messages[1].media?.kind)
    }

    @Test
    fun `a conversation that is not yours answers CHAT_CONVERSATION_NOT_FOUND`() = runBlocking {
        server.enqueue(failure(404, "CHAT_CONVERSATION_NOT_FOUND"))

        val result = repository.latestMessages("other") as ApiResult.Failure

        assertEquals(404, result.status)
        assertEquals("CHAT_CONVERSATION_NOT_FOUND", result.errorCode)
    }

    // POST /chat/conversations/{id}/messages

    @Test
    fun `send posts the body with the idempotency key and returns the stored message`() = runBlocking {
        server.enqueue(jsonResponse(envelope(messageJson(9, body = "selamat pagi", senderId = "u1")), 201))
        val pending = PendingMessage("selamat pagi")

        val result = repository.send("cv1", pending) as ApiResult.Success

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/chat/conversations/cv1/messages", request.path)
        assertEquals(pending.idempotencyKey, request.getHeader("Idempotency-Key"))
        assertEquals(mapOf<String, Any?>("body" to "selamat pagi"), bodyOf(request))
        assertEquals(9L, result.data.id)
    }

    @Test
    fun `the key matches the pattern the server accepts`() {
        assertTrue(Regex("^[A-Za-z0-9_-]{8,64}$").matches(PendingMessage("x").idempotencyKey))
    }

    @Test
    fun `sending the same pending message again reuses the key and a new message gets a new key`() = runBlocking {
        repeat(3) { server.enqueue(jsonResponse(envelope(messageJson(9, senderId = "u1")), 201)) }
        val pending = PendingMessage("halo")

        repository.send("cv1", pending)
        repository.send("cv1", pending)
        repository.send("cv1", PendingMessage("halo"))

        val keys = List(3) { server.takeRequest().getHeader("Idempotency-Key") }
        assertEquals(keys[0], keys[1])
        assertNotEquals(keys[0], keys[2])
    }

    @Test
    fun `a replayed answer is still a success`() = runBlocking {
        server.enqueue(jsonResponse(envelope(messageJson(9, senderId = "u1")), 201).setHeader("Idempotent-Replayed", "true"))

        assertTrue(repository.send("cv1", PendingMessage("halo")) is ApiResult.Success)
    }

    @Test
    fun `send answers the closed conversation and the idempotency conflicts`() = runBlocking {
        server.enqueue(failure(409, "CHAT_SENDING_CLOSED"))
        server.enqueue(failure(409, "IDEMPOTENCY_IN_PROGRESS"))
        server.enqueue(failure(422, "IDEMPOTENCY_KEY_REUSED"))

        val closed = repository.send("cv1", PendingMessage("a")) as ApiResult.Failure
        val inProgress = repository.send("cv1", PendingMessage("a")) as ApiResult.Failure
        val reused = repository.send("cv1", PendingMessage("a")) as ApiResult.Failure

        assertEquals(409, closed.status)
        assertEquals("CHAT_SENDING_CLOSED", closed.errorCode)
        assertEquals("IDEMPOTENCY_IN_PROGRESS", inProgress.errorCode)
        assertEquals(422, reused.status)
        assertEquals("IDEMPOTENCY_KEY_REUSED", reused.errorCode)
    }

    @Test
    fun `send answers a validation error, a missing conversation and a rate limit with Retry-After`() = runBlocking {
        server.enqueue(failure(400, "VALIDATION_ERROR"))
        server.enqueue(failure(404, "CHAT_CONVERSATION_NOT_FOUND"))
        server.enqueue(failure(429, "RATE_LIMITED").setHeader("Retry-After", "9"))
        server.enqueue(failure(503, "SERVER_TIMEOUT").setHeader("Retry-After", "1"))

        val invalid = repository.send("cv1", PendingMessage("a")) as ApiResult.Failure
        val missing = repository.send("nope", PendingMessage("a")) as ApiResult.Failure
        val limited = repository.send("cv1", PendingMessage("a")) as ApiResult.Failure
        val slow = repository.send("cv1", PendingMessage("a")) as ApiResult.Failure

        assertEquals("VALIDATION_ERROR", invalid.errorCode)
        assertEquals("CHAT_CONVERSATION_NOT_FOUND", missing.errorCode)
        assertEquals(429, limited.status)
        assertEquals(9L, limited.retryAfterSeconds)
        assertEquals("SERVER_TIMEOUT", slow.errorCode)
    }

    // DELETE /chat/conversations/{id}/messages/{messageId}

    @Test
    fun `delete sends DELETE and returns the message as it is now`() = runBlocking {
        server.enqueue(jsonResponse(envelope(messageJson(9, body = "", senderId = "u1", deleted = true))))

        val result = repository.delete("cv1", 9) as ApiResult.Success

        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/api/v1/chat/conversations/cv1/messages/9", request.path)
        assertTrue(result.data.deleted)
        assertEquals("", result.data.body)
    }

    @Test
    fun `delete answers a missing message and a message of somebody else`() = runBlocking {
        server.enqueue(failure(404, "CHAT_MESSAGE_NOT_FOUND"))
        server.enqueue(failure(403, "AUTH_FORBIDDEN"))

        val missing = repository.delete("cv1", 99) as ApiResult.Failure
        val foreign = repository.delete("cv1", 1) as ApiResult.Failure

        assertEquals("CHAT_MESSAGE_NOT_FOUND", missing.errorCode)
        assertEquals(403, foreign.status)
    }

    // PUT /chat/conversations/{id}/read

    @Test
    fun `markRead puts the message id and returns the mark and what is left unread`() = runBlocking {
        server.enqueue(jsonResponse(envelope("""{"lastReadMessageId":12,"unreadCount":1}""")))

        val result = repository.markRead("cv1", 12) as ApiResult.Success

        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/api/v1/chat/conversations/cv1/read", request.path)
        assertEquals(12.0, bodyOf(request)["messageId"])
        assertEquals(12L, result.data.lastReadMessageId)
        assertEquals(1, result.data.unreadCount)
    }

    @Test
    fun `markRead on a conversation that is not yours answers CHAT_CONVERSATION_NOT_FOUND`() = runBlocking {
        server.enqueue(failure(404, "CHAT_CONVERSATION_NOT_FOUND"))

        assertEquals("CHAT_CONVERSATION_NOT_FOUND", (repository.markRead("x", 1) as ApiResult.Failure).errorCode)
    }

    // Semua panggilan

    @Test
    fun `a response that is not the envelope is a malformed response and a proxy 502 has no error code`() = runBlocking {
        server.enqueue(jsonResponse("""{"success":true}"""))
        server.enqueue(jsonResponse("<html>bad gateway</html>", 502))

        val noData = repository.conversations() as ApiResult.Failure
        val proxy = repository.conversations() as ApiResult.Failure

        assertEquals("MALFORMED_RESPONSE", noData.errorCode)
        assertEquals(502, proxy.status)
        assertNull(proxy.errorCode)
    }

    @Test
    fun `offline is a network error for every call`() = runBlocking {
        server.shutdown()

        assertTrue(repository.contacts() is ApiResult.NetworkError)
        assertTrue(repository.conversations() is ApiResult.NetworkError)
        assertTrue(repository.startDirect("u1") is ApiResult.NetworkError)
        assertTrue(repository.openClassRoom("c1") is ApiResult.NetworkError)
        assertTrue(repository.latestMessages("cv1") is ApiResult.NetworkError)
        assertTrue(repository.send("cv1", PendingMessage("a")) is ApiResult.NetworkError)
        assertTrue(repository.delete("cv1", 1) is ApiResult.NetworkError)
        assertTrue(repository.markRead("cv1", 1) is ApiResult.NetworkError)
    }
}
