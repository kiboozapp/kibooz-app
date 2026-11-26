package com.nthb.kibooz.data.remote

import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import java.io.IOException

/**
 * Frame dari server yang dipakai aplikasi. Frame hanya memberi tahu bahwa ada yang berubah, jadi tidak ada id
 * pesan di sini dan tidak ada isi pesan. Layar mengambil pesannya lewat REST, dan kursor hanya maju dari jawaban
 * daftar server (lihat `ChatViewModel`). Frame `typing` dan jenis yang belum dikenal tidak diurai.
 */
sealed interface ChatFrame {
    /** Dikirim sekali setelah koneksi terbuka. Sesudahnya aplikasi mengejar apa yang terlewat. */
    data object Ready : ChatFrame

    /** Server curiga ada event yang hilang di jalan dan tidak menyebut percakapan. */
    data object Resync : ChatFrame

    /** Pesan baru di sebuah percakapan akun ini, termasuk pesan sendiri dari perangkat lain atau dari REST. */
    data class NewMessage(val conversationId: String?) : ChatFrame

    data class MessageDeleted(val conversationId: String?) : ChatFrame

    /** [userId] membaca percakapan sampai suatu pesan. */
    data class Read(val conversationId: String?, val userId: String?) : ChatFrame
}

/** Bentuk JSON frame. Moshi melewati semua isian lain, termasuk isi pesan. */
data class ChatFrameJson(
    val type: String? = null,
    val conversationId: String? = null,
    val userId: String? = null,
    val message: ChatFrameMessageJson? = null
)

data class ChatFrameMessageJson(val conversationId: String? = null)

private const val TYPE_READY = "ready"
private const val TYPE_MESSAGE = "message"
private const val TYPE_MESSAGE_DELETED = "message.deleted"
private const val TYPE_READ = "read"
private const val TYPE_RESYNC = "resync"

/** Mengurai satu frame teks. Mengembalikan null untuk frame yang rusak atau tidak dikenal, dan tidak pernah melempar. */
fun parseChatFrame(moshi: Moshi, text: String): ChatFrame? {
    val parsed: ChatFrameJson? = try {
        moshi.adapter(ChatFrameJson::class.java).fromJson(text)
    } catch (_: IOException) {
        null
    } catch (_: JsonDataException) {
        null
    }
    val json = parsed ?: return null

    val conversationId = json.conversationId ?: json.message?.conversationId

    return when (json.type) {
        TYPE_READY -> ChatFrame.Ready
        TYPE_RESYNC -> ChatFrame.Resync
        TYPE_MESSAGE -> ChatFrame.NewMessage(conversationId)
        TYPE_MESSAGE_DELETED -> ChatFrame.MessageDeleted(conversationId)
        TYPE_READ -> ChatFrame.Read(conversationId, json.userId)
        else -> null
    }
}
