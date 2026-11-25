package com.nthb.kibooz.data.remote

import com.squareup.moshi.Json

/** Satu orang di chat. Akun yang sudah dihapus tampil dengan `deleted` true dan nama bawaan server. */
data class ChatPerson(
    val id: String,
    val fullName: String,
    val role: String = "",
    val avatarUrl: String? = null,
    val deleted: Boolean = false
)

data class ChatContactClass(val id: String, val name: String)

/** Orang yang bisa diajak bicara. [children] berisi nama anak si wali di kelas bersama, kosong untuk guru. */
data class ChatContact(
    val id: String,
    val fullName: String,
    val role: String = "",
    val avatarUrl: String? = null,
    val deleted: Boolean = false,
    val classes: List<ChatContactClass> = emptyList(),
    val children: List<String> = emptyList(),
    /** Null sampai percakapan dengan orang ini ada. */
    val conversationId: String? = null
)

/** `data` dari `GET /chat/contacts`. */
data class ChatContactList(
    val page: Int,
    val limit: Int,
    val total: Int,
    val contacts: List<ChatContact>
)

/** Lampiran foto atau video. Aplikasi belum membukanya, jadi hanya jenisnya yang dipakai. */
data class ChatMedia(
    val kind: String,
    val contentType: String = "",
    val sizeBytes: Long = 0,
    val url: String = "",
    val expiresAt: String = ""
)

/** Satu pesan. [id] hanya naik di dalam satu percakapan dan menjadi kursor `after` dan `before`. */
data class ChatMessage(
    val id: Long,
    val conversationId: String,
    val senderId: String,
    val sender: ChatPerson,
    /** Kosong untuk pesan yang dihapus dan untuk pesan yang hanya berisi lampiran. */
    val body: String = "",
    val media: ChatMedia? = null,
    val deleted: Boolean = false,
    val createdAt: String
)

const val CHAT_KIND_DIRECT = "direct"
const val CHAT_KIND_CLASS = "class"

/** Percakapan langsung guru dan wali, atau ruang sebuah kelas. */
data class ChatConversation(
    val id: String,
    val kind: String,
    /** Lawan bicara percakapan langsung, null untuk ruang kelas. */
    val peer: ChatPerson? = null,
    /** Kelas dari sebuah ruang, null untuk percakapan langsung. */
    @Json(name = "class") val classRoom: ChatContactClass? = null,
    val lastMessage: ChatMessage? = null,
    val unreadCount: Int = 0,
    /** Pesan terakhir yang sudah dibaca lawan bicara. Selalu 0 untuk ruang kelas. */
    val peerLastReadMessageId: Long = 0,
    val canSend: Boolean = true,
    val createdAt: String = ""
) {
    val isRoom: Boolean get() = kind == CHAT_KIND_CLASS
}

/** `data` dari `GET /chat/conversations`. Hanya percakapan yang punya pesan, yang terbaru dulu. */
data class ChatConversationList(
    val page: Int,
    val limit: Int,
    val total: Int,
    val conversations: List<ChatConversation>
)

/** `data` dari `GET .../messages`. Pesan selalu urut dari yang lama ke yang baru. */
data class ChatMessageList(
    val messages: List<ChatMessage>,
    /** Masih ada pesan lagi ke arah yang diminta, yang lebih lama untuk `before` dan yang lebih baru untuk `after`. */
    val hasMore: Boolean = false
)

/** `data` dari `PUT .../read`. */
data class ChatReadState(val lastReadMessageId: Long, val unreadCount: Int)

/** Isi `POST /chat/conversations`. Kirim tepat satu dari dua isian, yang null tidak ikut terkirim. */
data class StartChatRequest(val userId: String? = null, val classId: String? = null)

/** Isi `POST .../messages`. Hanya teks, karena lampiran belum dibuat. */
data class SendChatMessageRequest(val body: String)

data class MarkChatReadRequest(val messageId: Long)
