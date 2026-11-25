package com.nthb.kibooz.ui.chat

import com.nthb.kibooz.data.remote.ApiEnvelope
import com.nthb.kibooz.data.remote.ChatApi
import com.nthb.kibooz.data.remote.ChatContact
import com.nthb.kibooz.data.remote.ChatContactClass
import com.nthb.kibooz.data.remote.ChatContactList
import com.nthb.kibooz.data.remote.ChatConversation
import com.nthb.kibooz.data.remote.ChatConversationList
import com.nthb.kibooz.data.remote.ChatMedia
import com.nthb.kibooz.data.remote.ChatMessage
import com.nthb.kibooz.data.remote.ChatMessageList
import com.nthb.kibooz.data.remote.ChatPerson
import com.nthb.kibooz.data.remote.ChatReadState
import com.nthb.kibooz.data.remote.ChildList
import com.nthb.kibooz.data.remote.ClassApi
import com.nthb.kibooz.data.remote.ClassList
import com.nthb.kibooz.data.remote.ClassStudentList
import com.nthb.kibooz.data.remote.MarkChatReadRequest
import com.nthb.kibooz.data.remote.SendChatMessageRequest
import com.nthb.kibooz.data.remote.StartChatRequest
import kotlinx.coroutines.CompletableDeferred
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response

/** Data contoh untuk tes chat. */
object ChatFixtures {

    fun person(id: String = "u2", name: String = "Bu Sari", role: String = "GURU", deleted: Boolean = false) =
        ChatPerson(id = id, fullName = name, role = role, avatarUrl = null, deleted = deleted)

    fun message(
        id: Long,
        senderId: String = "u2",
        body: String = "pesan $id",
        deleted: Boolean = false,
        createdAt: String = "2026-11-25T07:05:00Z",
        media: ChatMedia? = null
    ) = ChatMessage(
        id = id,
        conversationId = "cv1",
        senderId = senderId,
        sender = person(senderId, name = "Pengirim $senderId"),
        body = if (deleted) "" else body,
        media = media,
        deleted = deleted,
        createdAt = createdAt
    )

    fun direct(
        id: String = "cv1",
        canSend: Boolean = true,
        unread: Int = 0,
        peerRead: Long = 0,
        last: ChatMessage? = message(1),
        peer: ChatPerson = person()
    ) = ChatConversation(
        id = id,
        kind = "direct",
        peer = peer,
        lastMessage = last,
        unreadCount = unread,
        peerLastReadMessageId = peerRead,
        canSend = canSend,
        createdAt = "2026-11-20T00:00:00Z"
    )

    fun room(id: String = "cv9", className: String = "Bunga", canSend: Boolean = true) = ChatConversation(
        id = id,
        kind = "class",
        peer = null,
        classRoom = ChatContactClass("c1", className),
        lastMessage = message(7, senderId = "u3"),
        canSend = canSend,
        createdAt = "2026-11-20T00:00:00Z"
    )

    fun contact(id: String, name: String = "Kontak $id", children: List<String> = emptyList(), conversationId: String? = null) =
        ChatContact(
            id = id,
            fullName = name,
            role = if (children.isEmpty()) "GURU" else "WALI",
            classes = listOf(ChatContactClass("c1", "Bunga")),
            children = children,
            conversationId = conversationId
        )

    fun <T> ok(data: T): Response<ApiEnvelope<T>> = Response.success(ApiEnvelope(true, null, data))

    /** Jawaban galat dengan kode galat, dan header Retry-After jika diberikan. */
    fun <T> failure(status: Int, errorCode: String, retryAfter: String? = null): Response<ApiEnvelope<T>> {
        val raw = okhttp3.Response.Builder()
            .code(status)
            .message("galat")
            .protocol(Protocol.HTTP_1_1)
            .request(Request.Builder().url("http://localhost/").build())
            .apply { retryAfter?.let { header("Retry-After", it) } }
            .build()
        val body = """{"success":false,"message":"x","errorCode":"$errorCode"}""".toResponseBody("application/json".toMediaType())

        return Response.error(body, raw)
    }

    fun messages(vararg items: ChatMessage, hasMore: Boolean = false) = ok(ChatMessageList(items.toList(), hasMore))
}

/**
 * Server chat palsu. Setiap rute punya lambda yang bisa diganti per tes, dan panggilan yang masuk dicatat.
 * [sendGate] menahan jawaban kirim sampai tes melepasnya, untuk meniru pesan yang sedang dikirim.
 */
class FakeChatApi : ChatApi {
    /** Pasangan (after, before) tiap panggilan daftar pesan. */
    val messageCalls = mutableListOf<Pair<Long?, Long?>>()
    val sendKeys = mutableListOf<String>()
    val sendBodies = mutableListOf<String>()
    val deleted = mutableListOf<Long>()
    val reads = mutableListOf<Long>()
    val started = mutableListOf<StartChatRequest>()
    val conversationPages = mutableListOf<Int>()
    val contactPages = mutableListOf<Int>()

    var sendGate: CompletableDeferred<Unit>? = null
    var startGate: CompletableDeferred<Unit>? = null

    var contactsReply: (page: Int) -> Response<ApiEnvelope<ChatContactList>> =
        { page -> ChatFixtures.ok(ChatContactList(page, 50, 0, emptyList())) }
    var conversationsReply: (page: Int) -> Response<ApiEnvelope<ChatConversationList>> =
        { page -> ChatFixtures.ok(ChatConversationList(page, 50, 0, emptyList())) }
    var startReply: (StartChatRequest) -> Response<ApiEnvelope<ChatConversation>> = { error("not set") }
    var messagesReply: (after: Long?, before: Long?) -> Response<ApiEnvelope<ChatMessageList>> =
        { _, _ -> ChatFixtures.messages() }
    var sendReply: (body: String) -> Response<ApiEnvelope<ChatMessage>> = { error("not set") }
    var deleteReply: (messageId: Long) -> Response<ApiEnvelope<ChatMessage>> = { error("not set") }
    var readReply: (messageId: Long) -> Response<ApiEnvelope<ChatReadState>> =
        { id -> ChatFixtures.ok(ChatReadState(id, 0)) }

    override suspend fun listContacts(page: Int, limit: Int): Response<ApiEnvelope<ChatContactList>> {
        contactPages += page
        return contactsReply(page)
    }

    override suspend fun listConversations(page: Int, limit: Int): Response<ApiEnvelope<ChatConversationList>> {
        conversationPages += page
        return conversationsReply(page)
    }

    override suspend fun startConversation(body: StartChatRequest): Response<ApiEnvelope<ChatConversation>> {
        started += body
        startGate?.await()
        return startReply(body)
    }

    override suspend fun listMessages(
        conversationId: String,
        after: Long?,
        before: Long?,
        limit: Int?
    ): Response<ApiEnvelope<ChatMessageList>> {
        messageCalls += after to before
        return messagesReply(after, before)
    }

    override suspend fun sendMessage(
        conversationId: String,
        idempotencyKey: String,
        body: SendChatMessageRequest
    ): Response<ApiEnvelope<ChatMessage>> {
        sendKeys += idempotencyKey
        sendBodies += body.body
        sendGate?.await()
        return sendReply(body.body)
    }

    override suspend fun deleteMessage(conversationId: String, messageId: Long): Response<ApiEnvelope<ChatMessage>> {
        deleted += messageId
        return deleteReply(messageId)
    }

    override suspend fun markRead(conversationId: String, body: MarkChatReadRequest): Response<ApiEnvelope<ChatReadState>> {
        reads += body.messageId
        return readReply(body.messageId)
    }
}

/** Server kelas palsu untuk pemilih ruang kelas di tab "Mulai baru". */
class FakeClassesApi : ClassApi {
    var classesReply: () -> Response<ApiEnvelope<ClassList>> = {
        ChatFixtures.ok(ClassList(1, 50, 0, emptyList()))
    }
    var childrenReply: () -> Response<ApiEnvelope<ChildList>> = { ChatFixtures.ok(ChildList(emptyList())) }

    override suspend fun listClasses(page: Int, limit: Int): Response<ApiEnvelope<ClassList>> = classesReply()

    override suspend fun listStudents(classId: String, page: Int, limit: Int): Response<ApiEnvelope<ClassStudentList>> =
        error("not used")

    override suspend fun listChildren(): Response<ApiEnvelope<ChildList>> = childrenReply()
}
