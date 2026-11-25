package com.nthb.kibooz.data.repository

import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.ChatApi
import com.nthb.kibooz.data.remote.ChatContact
import com.nthb.kibooz.data.remote.ChatContactList
import com.nthb.kibooz.data.remote.ChatConversation
import com.nthb.kibooz.data.remote.ChatConversationList
import com.nthb.kibooz.data.remote.ChatMessage
import com.nthb.kibooz.data.remote.ChatMessageList
import com.nthb.kibooz.data.remote.ChatReadState
import com.nthb.kibooz.data.remote.MarkChatReadRequest
import com.nthb.kibooz.data.remote.SendChatMessageRequest
import com.nthb.kibooz.data.remote.StartChatRequest
import com.nthb.kibooz.data.remote.newIdempotencyKey
import com.nthb.kibooz.data.remote.safeApiCall
import com.squareup.moshi.Moshi

/**
 * Satu pesan yang menunggu terkirim. [idempotencyKey] dibuat sekali di sini dan dipakai di setiap percobaan,
 * jadi kirim ulang setelah jawaban hilang tidak menulis pesan dua kali. Simpan objek ini sampai ada jawaban 2xx.
 * Jika isinya berubah, buat objek baru supaya kuncinya baru, karena kunci yang sama dengan isi lain ditolak
 * `IDEMPOTENCY_KEY_REUSED`.
 */
class PendingMessage(
    val body: String,
    val idempotencyKey: String = newIdempotencyKey()
)

/** Chat guru dan wali lewat REST: kontak, percakapan, pesan, hapus pesan sendiri dan tanda baca. */
class ChatRepository(private val api: ChatApi, private val moshi: Moshi) {

    suspend fun contacts(page: Int = 1, limit: Int = PAGE_LIMIT): ApiResult<ChatContactList> =
        safeApiCall(moshi) { api.listContacts(page, limit) }

    /** Semua kontak, halaman demi halaman. Berhenti di halaman pertama yang gagal dan mengembalikan kegagalannya. */
    suspend fun allContacts(): ApiResult<List<ChatContact>> = allPages { page ->
        when (val result = contacts(page, PAGE_LIMIT)) {
            is ApiResult.Success -> ApiResult.Success(Page(result.data.contacts, result.data.total))
            is ApiResult.Failure -> result
            is ApiResult.NetworkError -> result
        }
    }

    suspend fun conversations(page: Int = 1, limit: Int = PAGE_LIMIT): ApiResult<ChatConversationList> =
        safeApiCall(moshi) { api.listConversations(page, limit) }

    /** Semua percakapan yang punya pesan, yang terbaru dulu. */
    suspend fun allConversations(): ApiResult<List<ChatConversation>> = allPages { page ->
        when (val result = conversations(page, PAGE_LIMIT)) {
            is ApiResult.Success -> ApiResult.Success(Page(result.data.conversations, result.data.total))
            is ApiResult.Failure -> result
            is ApiResult.NetworkError -> result
        }
    }

    /**
     * Mencari satu percakapan di daftar, karena server tidak punya rute untuk satu percakapan. Berhenti di halaman
     * tempat percakapannya ada. Null jika percakapannya tidak ada di daftar, misalnya karena belum punya pesan.
     */
    suspend fun findConversation(conversationId: String): ApiResult<ChatConversation?> {
        var seen = 0

        for (page in 1..MAX_PAGES) {
            when (val result = conversations(page, PAGE_LIMIT)) {
                is ApiResult.Success -> {
                    val items = result.data.conversations
                    items.firstOrNull { it.id == conversationId }?.let { return ApiResult.Success(it) }
                    seen += items.size
                    if (items.isEmpty() || seen >= result.data.total) return ApiResult.Success(null)
                }
                is ApiResult.Failure -> return result
                is ApiResult.NetworkError -> return result
            }
        }

        return ApiResult.Success(null)
    }

    /** Membuka percakapan langsung dengan kontak. Server membuatnya jika belum ada, dan mengembalikan yang lama jika sudah. */
    suspend fun startDirect(userId: String): ApiResult<ChatConversation> =
        safeApiCall(moshi) { api.startConversation(StartChatRequest(userId = userId)) }

    /** Membuka ruang sebuah kelas. Server membuatnya pada panggilan pertama. */
    suspend fun openClassRoom(classId: String): ApiResult<ChatConversation> =
        safeApiCall(moshi) { api.startConversation(StartChatRequest(classId = classId)) }

    /** Pesan terbaru, urut dari yang lama ke yang baru. */
    suspend fun latestMessages(conversationId: String, limit: Int = MESSAGE_LIMIT): ApiResult<ChatMessageList> =
        safeApiCall(moshi) { api.listMessages(conversationId, null, null, limit) }

    /** Pesan yang lebih lama dari [beforeId]. */
    suspend fun olderMessages(conversationId: String, beforeId: Long, limit: Int = MESSAGE_LIMIT): ApiResult<ChatMessageList> =
        safeApiCall(moshi) { api.listMessages(conversationId, null, beforeId, limit) }

    /** Pesan setelah [afterId], atau dari awal dengan 0. */
    suspend fun newerMessages(conversationId: String, afterId: Long, limit: Int = MESSAGE_LIMIT): ApiResult<ChatMessageList> =
        safeApiCall(moshi) { api.listMessages(conversationId, afterId, null, limit) }

    suspend fun send(conversationId: String, pending: PendingMessage): ApiResult<ChatMessage> =
        safeApiCall(moshi) {
            api.sendMessage(conversationId, pending.idempotencyKey, SendChatMessageRequest(pending.body))
        }

    /** Menghapus pesan sendiri. Pesannya tetap ada dengan `deleted` true dan isi kosong. */
    suspend fun delete(conversationId: String, messageId: Long): ApiResult<ChatMessage> =
        safeApiCall(moshi) { api.deleteMessage(conversationId, messageId) }

    /** Menggeser tanda baca ke [messageId]. Tanda hanya maju. */
    suspend fun markRead(conversationId: String, messageId: Long): ApiResult<ChatReadState> =
        safeApiCall(moshi) { api.markRead(conversationId, MarkChatReadRequest(messageId)) }

    private class Page<T>(val items: List<T>, val total: Int)

    /** Membaca halaman 1, 2, ... sampai total terpenuhi atau halaman kosong. Kegagalan satu halaman membatalkan semuanya. */
    private suspend fun <T> allPages(fetch: suspend (page: Int) -> ApiResult<Page<T>>): ApiResult<List<T>> {
        val all = mutableListOf<T>()

        for (page in 1..MAX_PAGES) {
            when (val result = fetch(page)) {
                is ApiResult.Success -> {
                    all += result.data.items
                    if (result.data.items.isEmpty() || all.size >= result.data.total) break
                }
                is ApiResult.Failure -> return result
                is ApiResult.NetworkError -> return result
            }
        }

        return ApiResult.Success(all)
    }

    private companion object {
        const val PAGE_LIMIT = 50
        const val MESSAGE_LIMIT = 50

        // Pengaman: 20 halaman dari 50 sudah 1000 entri, jauh di atas satu akun guru atau wali
        const val MAX_PAGES = 20
    }
}
