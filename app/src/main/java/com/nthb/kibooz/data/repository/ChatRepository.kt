package com.nthb.kibooz.data.repository

import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.CHAT_MEDIA_PHOTO
import com.nthb.kibooz.data.remote.CHAT_PHOTO_MAX_BYTES
import com.nthb.kibooz.data.remote.CHAT_PHOTO_TYPES
import com.nthb.kibooz.data.remote.CHAT_VIDEO_MAX_BYTES
import com.nthb.kibooz.data.remote.CHAT_VIDEO_TYPES
import com.nthb.kibooz.data.remote.ChatApi
import com.nthb.kibooz.data.remote.ChatAttachment
import com.nthb.kibooz.data.remote.ChatContact
import com.nthb.kibooz.data.remote.ChatContactList
import com.nthb.kibooz.data.remote.ChatConversation
import com.nthb.kibooz.data.remote.ChatConversationList
import com.nthb.kibooz.data.remote.ChatMessage
import com.nthb.kibooz.data.remote.ChatMessageList
import com.nthb.kibooz.data.remote.ChatReadState
import com.nthb.kibooz.data.remote.ChatStorage
import com.nthb.kibooz.data.remote.ChatUploadSourceException
import com.nthb.kibooz.data.remote.ChatVideoUploadRequest
import com.nthb.kibooz.data.remote.ERROR_UPLOAD_SOURCE
import com.nthb.kibooz.data.remote.ExactLengthRequestBody
import com.nthb.kibooz.data.remote.MarkChatReadRequest
import com.nthb.kibooz.data.remote.OkHttpChatStorage
import com.nthb.kibooz.data.remote.SendChatMessageRequest
import com.nthb.kibooz.data.remote.StartChatRequest
import com.nthb.kibooz.data.remote.newIdempotencyKey
import com.nthb.kibooz.data.remote.safeApiCall
import com.squareup.moshi.Moshi
import okhttp3.MediaType.Companion.toMediaType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MultipartBody

/**
 * Satu pesan yang menunggu terkirim. [idempotencyKey] dibuat sekali di sini dan dipakai di setiap percobaan,
 * jadi kirim ulang setelah jawaban hilang tidak menulis pesan dua kali. Simpan objek ini sampai ada jawaban 2xx.
 * Jika isinya berubah, buat objek baru supaya kuncinya baru, karena kunci yang sama dengan isi lain ditolak
 * `IDEMPOTENCY_KEY_REUSED`.
 *
 * [mediaKey] diisi sesudah lampiran selesai diunggah, supaya kirim ulang tidak mengunggah lagi.
 */
class PendingMessage(
    val body: String,
    val idempotencyKey: String = newIdempotencyKey(),
    val mediaKey: String? = null
) {
    /**
     * Pesan yang sama dengan [key] sebagai lampirannya, dengan kunci idempoten yang tetap. Hanya untuk pesan yang
     * belum tersimpan di server, karena kunci boleh dipakai lagi sesudah jawaban yang bukan 2xx.
     */
    fun withMediaKey(key: String?) = PendingMessage(body, idempotencyKey, key)
}

/** Chat guru dan wali lewat REST, yaitu kontak, percakapan, pesan, lampiran, hapus pesan sendiri dan tanda baca. */
class ChatRepository(
    private val api: ChatApi,
    private val moshi: Moshi,
    private val storage: ChatStorage = OkHttpChatStorage(),
    private val currentUserId: () -> String? = { null },
    private val nowMillis: () -> Long = System::currentTimeMillis
) {

    private data class ContactsSnapshot(val userId: String, val savedAt: Long, val contacts: List<ChatContact>)

    private val contactsLock = Mutex()
    private var contactsSnapshot: ContactsSnapshot? = null

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

    /**
     * Semua kontak, dengan hasil beberapa detik terakhir dipakai ulang. Layar kelas Wali memuat kontak hanya untuk
     * menurunkan nama pengajar, jadi satu pengambilan melayani beberapa layar yang dibuka berurutan. Cache terikat
     * pada pengguna yang masuk, sehingga akun lain tidak pernah menerima kontak akun sebelumnya. Daftar obrolan
     * tetap memakai [allContacts] agar selalu terbaru.
     */
    suspend fun recentContacts(): ApiResult<List<ChatContact>> = contactsLock.withLock {
        val userId = currentUserId()
        val snapshot = contactsSnapshot
        val now = nowMillis()
        if (userId != null && snapshot != null && snapshot.userId == userId && now - snapshot.savedAt in 0 until CONTACTS_CACHE_MS) {
            return@withLock ApiResult.Success(snapshot.contacts)
        }
        val result = allContacts()
        // Bila akun berganti selagi memuat, hasilnya bukan milik akun yang dicatat di awal dan tidak disimpan
        contactsSnapshot = if (userId != null && userId == currentUserId() && result is ApiResult.Success) {
            ContactsSnapshot(userId, now, result.data)
        } else {
            null
        }
        result
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
            api.sendMessage(conversationId, pending.idempotencyKey, SendChatMessageRequest(pending.body, pending.mediaKey))
        }

    /**
     * Mengunggah [attachment] dan mengembalikan `mediaKey` untuk dikirim bersama pesan. Foto lewat API, video
     * langsung ke storage. Ukuran dan jenis yang pasti ditolak server tidak dikirim sama sekali.
     * [onProgress] dipanggil dari thread jaringan.
     */
    suspend fun upload(
        conversationId: String,
        attachment: ChatAttachment,
        onProgress: (sentBytes: Long) -> Unit = {}
    ): ApiResult<String> = if (attachment.kind == CHAT_MEDIA_PHOTO) {
        uploadPhoto(conversationId, attachment, onProgress)
    } else {
        uploadVideo(conversationId, attachment, onProgress)
    }

    private suspend fun uploadPhoto(
        conversationId: String,
        photo: ChatAttachment,
        onProgress: (sentBytes: Long) -> Unit
    ): ApiResult<String> {
        if (photo.contentType !in CHAT_PHOTO_TYPES) return ApiResult.Failure(0, "INVALID_IMAGE", null)
        if (photo.sizeBytes !in 1..CHAT_PHOTO_MAX_BYTES) return ApiResult.Failure(0, "FILE_TOO_LARGE", null)

        val fileName = "photo." + photo.contentType.substringAfter('/')
        val body = ExactLengthRequestBody(photo, photo.contentType.toMediaType(), onProgress)
        val part = MultipartBody.Part.createFormData("file", fileName, body)

        return when (val result = safeApiCall(moshi) { api.uploadPhoto(conversationId, part) }) {
            is ApiResult.Success -> ApiResult.Success(result.data.mediaKey)
            is ApiResult.Failure -> result
            is ApiResult.NetworkError ->
                if (result.cause is ChatUploadSourceException) ApiResult.Failure(0, ERROR_UPLOAD_SOURCE, null) else result
        }
    }

    private suspend fun uploadVideo(
        conversationId: String,
        video: ChatAttachment,
        onProgress: (sentBytes: Long) -> Unit
    ): ApiResult<String> {
        if (video.contentType !in CHAT_VIDEO_TYPES) return ApiResult.Failure(0, "VALIDATION_ERROR", null)
        if (video.sizeBytes !in 1..CHAT_VIDEO_MAX_BYTES) return ApiResult.Failure(0, "FILE_TOO_LARGE", null)

        val request = ChatVideoUploadRequest(video.contentType, video.sizeBytes)
        val ticket = when (val result = safeApiCall(moshi) { api.createVideoUpload(conversationId, request) }) {
            is ApiResult.Success -> result.data
            is ApiResult.Failure -> return result
            is ApiResult.NetworkError -> return result
        }

        return when (val result = storage.upload(ticket.uploadUrl, ticket.headers, video, onProgress)) {
            is ApiResult.Success -> ApiResult.Success(ticket.mediaKey)
            is ApiResult.Failure -> result
            is ApiResult.NetworkError -> result
        }
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

        // Cukup singkat agar kelas yang baru diikuti anak segera muncul, dan cukup lama untuk layar yang dibuka berurutan
        const val CONTACTS_CACHE_MS = 30_000L
        const val MESSAGE_LIMIT = 50

        // Pengaman: 20 halaman dari 50 sudah 1000 entri, jauh di atas satu akun guru atau wali
        const val MAX_PAGES = 20
    }
}
