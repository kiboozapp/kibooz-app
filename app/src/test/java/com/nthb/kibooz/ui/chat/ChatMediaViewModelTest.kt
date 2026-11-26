package com.nthb.kibooz.ui.chat

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.nthb.kibooz.R
import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.ChatAttachment
import com.nthb.kibooz.data.remote.ChatMedia
import com.nthb.kibooz.data.remote.ChatStorage
import com.nthb.kibooz.data.remote.ERROR_UPLOAD_REJECTED
import com.nthb.kibooz.data.remote.ERROR_UPLOAD_SOURCE
import com.nthb.kibooz.data.remote.UploadSource
import com.nthb.kibooz.data.repository.ChatRepository
import com.nthb.kibooz.ui.chat.ChatFixtures.failure
import com.nthb.kibooz.ui.chat.ChatFixtures.message
import com.nthb.kibooz.ui.chat.ChatFixtures.messages
import com.nthb.kibooz.ui.chat.ChatFixtures.ok
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Storage palsu. [gate] menahan unggahan sampai tes melepasnya. */
private class FakeChatStorage : ChatStorage {
    val uploads = mutableListOf<String>()
    var gate: CompletableDeferred<Unit>? = null
    var reply: () -> ApiResult<Unit> = { ApiResult.Success(Unit) }
    var progress: (Long) -> Unit = {}
    var cancelled = false

    override suspend fun upload(
        url: String,
        headers: Map<String, String>,
        source: UploadSource,
        onProgress: (sentBytes: Long) -> Unit
    ): ApiResult<Unit> {
        uploads += url
        progress = onProgress
        try {
            gate?.await()
        } finally {
            cancelled = gate?.isCompleted == false
        }
        return reply()
    }

    override suspend fun download(url: String, maxBytes: Long): ApiResult<ByteArray> = ApiResult.Success(ByteArray(0))
}

@OptIn(ExperimentalCoroutinesApi::class)
class ChatMediaViewModelTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val api = FakeChatApi()
    private val storage = FakeChatStorage()
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val repository = ChatRepository(api, moshi, storage)
    private val pauses = mutableListOf<Long>()
    private val info = ChatInfo("cv1", isRoom = false, peerDeleted = false, canSend = true, peerLastReadMessageId = 0)

    private var clock = 0L

    /** Lampiran yang dikembalikan penyiap untuk tiap uri. */
    private val files = mutableMapOf<String, ChatAttachment>()

    private val preparer = AttachmentPreparer { uri ->
        files[uri]?.let { PreparedAttachment.Ready(it) }
            ?: PreparedAttachment.Rejected(R.string.chat_error_format_video)
    }

    private fun viewModel(preparer: AttachmentPreparer = this.preparer) = ChatViewModel(
        repository,
        info,
        ownUserId = "me",
        pause = { pauses += it },
        jitter = { 0L },
        now = { clock },
        preparer = preparer
    )

    private fun photo(uri: String = "foto"): File {
        val file = folder.newFile("$uri.jpg").apply { writeText("isi") }
        files[uri] = ChatAttachment("photo", "image/jpeg", 3, tempFile = file) { file.inputStream() }
        return file
    }

    private fun video(uri: String = "video") {
        files[uri] = ChatAttachment("video", "video/mp4", 100) { ByteArrayInputStream(ByteArray(100)) }
    }

    /** Pesan dengan lampiran seperti yang dikirim server. */
    private fun mediaMessage(id: Long, url: String = "https://storage.example.org/$id", deleted: Boolean = false) =
        message(
            id,
            senderId = "u2",
            body = "",
            deleted = deleted,
            media = if (deleted) null else ChatMedia("photo", "image/jpeg", 3, url)
        )

    private val sending get() = LocalStatus.Sending()

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        api.messagesReply = { _, _ -> messages(message(1, senderId = "u2")) }
        var next = 3L
        api.sendReply = { body -> ok(message(next++, senderId = "me", body = body)) }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // Memilih

    @Test
    fun `a picked file waits as the attachment and can be removed again`() {
        val file = photo()
        val viewModel = viewModel()

        viewModel.attach("foto")
        assertNotNull(viewModel.state.value.attachment)
        assertFalse(viewModel.state.value.preparing)
        assertTrue(api.photoUploads.isEmpty())

        viewModel.removeAttachment()
        assertNull(viewModel.state.value.attachment)
        assertFalse(file.exists())
    }

    @Test
    fun `a second pick replaces the first and removes its temporary file`() {
        val first = photo("satu")
        val second = photo("dua")
        val viewModel = viewModel()

        viewModel.attach("satu")
        viewModel.attach("dua")

        assertFalse(first.exists())
        assertTrue(second.exists())
        assertEquals(second, viewModel.state.value.attachment?.tempFile)
    }

    @Test
    fun `a refused file shows the reason and attaches nothing`() {
        val viewModel = viewModel()

        viewModel.attach("lain")

        assertNull(viewModel.state.value.attachment)
        assertEquals(R.string.chat_error_format_video, viewModel.state.value.notice?.res)
    }

    @Test
    fun `nothing can be sent or picked while the file is being prepared`() {
        val gate = CompletableDeferred<PreparedAttachment>()
        val viewModel = viewModel(AttachmentPreparer { gate.await() })

        viewModel.attach("foto")

        assertTrue(viewModel.state.value.preparing)
        assertFalse(viewModel.state.value.canAttach)
        assertFalse(viewModel.send("halo"))

        gate.complete(PreparedAttachment.Rejected(R.string.chat_error_format_video))
        assertFalse(viewModel.state.value.preparing)
    }

    // Unggah dan kirim

    @Test
    fun `a photo is uploaded, then sent with its key and no text, and its temporary file is removed`() {
        val file = photo()
        val viewModel = viewModel()
        viewModel.attach("foto")

        assertTrue(viewModel.send(""))

        assertEquals(1, api.photoUploads.size)
        assertEquals(listOf<String?>("k1"), api.sendMediaKeys)
        assertEquals(listOf(""), api.sendBodies)
        assertNull(viewModel.state.value.local)
        assertNull(viewModel.state.value.attachment)
        assertEquals(listOf(1L, 3L), viewModel.state.value.timeline.messages.map { it.id })
        assertFalse(file.exists())
    }

    @Test
    fun `a video goes to storage and its progress shows in steps`() {
        video()
        storage.gate = CompletableDeferred()
        val viewModel = viewModel()
        viewModel.attach("video")

        viewModel.send("lihat")
        assertEquals(LocalStatus.Sending(uploadPercent = 0), viewModel.state.value.local?.status)
        storage.progress(52)
        assertEquals(LocalStatus.Sending(uploadPercent = 50), viewModel.state.value.local?.status)
        assertTrue(api.sendKeys.isEmpty())

        storage.gate?.complete(Unit)

        assertEquals(100L, api.videoRequests.single().sizeBytes)
        assertEquals("video/mp4", api.videoRequests.single().contentType)
        assertEquals(listOf("https://storage.example.org/pending/v1"), storage.uploads)
        assertEquals(listOf<String?>("v1"), api.sendMediaKeys)
        assertEquals(listOf("lihat"), api.sendBodies)
        assertNull(viewModel.state.value.local)
    }

    @Test
    fun `a running upload blocks a second send and a second pick, and polling goes on`() = runBlocking {
        video()
        photo()
        storage.gate = CompletableDeferred()
        val viewModel = viewModel()
        viewModel.attach("video")
        viewModel.send("")

        assertFalse(viewModel.send("lagi"))
        viewModel.attach("foto")
        assertNull(viewModel.state.value.attachment)
        assertTrue(viewModel.poll())
        assertEquals(1L to null, api.messageCalls.last())

        storage.gate?.complete(Unit)
        assertEquals(1, storage.uploads.size)
        assertEquals(1, api.sendKeys.size)
    }

    @Test
    fun `a failed upload keeps the file and a retry uploads again with the same idempotency key`() {
        video()
        storage.reply = { ApiResult.NetworkError(IOException("putus")) }
        val viewModel = viewModel()
        viewModel.attach("video")
        viewModel.send("")

        val failed = viewModel.state.value.local
        assertEquals(LocalStatus.Failed(R.string.error_network), failed?.status)
        assertNull(failed?.pending?.mediaKey)
        assertNotNull(failed?.attachment)
        assertTrue(api.sendKeys.isEmpty())

        storage.reply = { ApiResult.Success(Unit) }
        viewModel.retry()

        assertEquals(2, storage.uploads.size)
        assertEquals(listOf(failed?.pending?.idempotencyKey), api.sendKeys)
        assertEquals(listOf<String?>("v2"), api.sendMediaKeys)
        assertNull(viewModel.state.value.local)
    }

    @Test
    fun `a retry after a failed send does not upload again`() {
        val file = photo()
        var failing = true
        api.sendReply = { body ->
            if (failing) throw IOException("putus") else ok(message(3, senderId = "me", body = body))
        }
        val viewModel = viewModel()
        viewModel.attach("foto")
        viewModel.send("")

        assertEquals("k1", viewModel.state.value.local?.pending?.mediaKey)
        assertTrue(file.exists())

        failing = false
        viewModel.retry()

        assertEquals(1, api.photoUploads.size)
        assertEquals(listOf<String?>("k1", "k1"), api.sendMediaKeys)
        assertEquals(1, api.sendKeys.toSet().size)
        assertNull(viewModel.state.value.local)
        assertFalse(file.exists())
    }

    @Test
    fun `an old media key that the server no longer accepts is uploaded again under the same idempotency key`() {
        photo()
        var attempts = 0
        api.sendReply = { body ->
            when (++attempts) {
                1 -> throw IOException("putus")
                2 -> failure(400, "VALIDATION_ERROR", field = "mediaKey")
                else -> ok(message(3, senderId = "me", body = body))
            }
        }
        val viewModel = viewModel()
        viewModel.attach("foto")
        viewModel.send("")

        viewModel.retry()

        assertEquals(2, api.photoUploads.size)
        assertEquals(listOf<String?>("k1", "k1", "k2"), api.sendMediaKeys)
        assertEquals(1, api.sendKeys.toSet().size)
        assertNull(viewModel.state.value.local)
    }

    @Test
    fun `a fresh media key that is refused fails once, and the next retry uploads again`() {
        photo()
        api.sendReply = { failure(400, "VALIDATION_ERROR", field = "mediaKey") }
        val viewModel = viewModel()
        viewModel.attach("foto")

        viewModel.send("")

        assertEquals(1, api.photoUploads.size)
        assertEquals(1, api.sendKeys.size)
        assertEquals(
            LocalStatus.Failed(R.string.chat_error_lampiran_belum_diterima),
            viewModel.state.value.local?.status
        )
        assertNull(viewModel.state.value.local?.pending?.mediaKey)

        viewModel.retry()
        assertEquals(2, api.photoUploads.size)
    }

    @Test
    fun `a validation error about the text keeps the media key`() {
        photo()
        api.sendReply = { failure(400, "VALIDATION_ERROR", field = "body") }
        val viewModel = viewModel()
        viewModel.attach("foto")

        viewModel.send("halo")

        assertEquals(LocalStatus.Failed(R.string.error_validation), viewModel.state.value.local?.status)
        assertEquals("k1", viewModel.state.value.local?.pending?.mediaKey)
    }

    @Test
    fun `storage that refuses the upload and a file that changed each have their own message`() {
        video()
        storage.reply = { ApiResult.Failure(403, ERROR_UPLOAD_REJECTED, null) }
        val viewModel = viewModel()
        viewModel.attach("video")
        viewModel.send("")
        assertEquals(LocalStatus.Failed(R.string.chat_error_unggah_ditolak), viewModel.state.value.local?.status)

        storage.reply = { ApiResult.Failure(0, ERROR_UPLOAD_SOURCE, null) }
        viewModel.retry()
        assertEquals(LocalStatus.Failed(R.string.chat_error_berkas_berubah), viewModel.state.value.local?.status)
    }

    @Test
    fun `a rate limited photo waits Retry-After and is sent again`() {
        photo()
        var attempts = 0
        api.photoReply = {
            if (++attempts == 1) {
                failure(429, "RATE_LIMITED", retryAfter = "1")
            } else {
                ok(com.nthb.kibooz.data.remote.ChatPhotoUpload("k9"))
            }
        }
        val viewModel = viewModel()
        viewModel.attach("foto")

        viewModel.send("")

        assertEquals(listOf(1_000L), pauses)
        assertEquals(listOf<String?>("k9"), api.sendMediaKeys)
        assertNull(viewModel.state.value.local)
    }

    @Test
    fun `discarding a failed message removes its temporary file`() {
        val file = photo()
        api.photoReply = { failure(502, "STORAGE_ERROR") }
        val viewModel = viewModel()
        viewModel.attach("foto")
        viewModel.send("")
        assertTrue(file.exists())

        viewModel.discardFailed()

        assertNull(viewModel.state.value.local)
        assertFalse(file.exists())
    }

    @Test
    fun `a conversation that closed during the upload gives the text back and drops the file`() {
        val file = photo()
        api.photoReply = { failure(409, "CHAT_SENDING_CLOSED") }
        val viewModel = viewModel()
        viewModel.attach("foto")

        viewModel.send("halo")

        val state = viewModel.state.value
        assertFalse(state.canSend)
        assertNull(state.local)
        assertEquals("halo", state.restoreDraft)
        assertTrue(api.sendKeys.isEmpty())
        assertFalse(file.exists())
    }

    @Test
    fun `a server without storage turns attachments off and keeps text working`() {
        val file = photo()
        api.photoReply = { failure(503, "STORAGE_UNAVAILABLE") }
        val viewModel = viewModel()
        viewModel.attach("foto")

        viewModel.send("halo")

        assertTrue(viewModel.state.value.attachmentsOff)
        assertFalse(viewModel.state.value.canAttach)
        assertEquals("halo", viewModel.state.value.restoreDraft)
        assertEquals(R.string.error_storage_unavailable, viewModel.state.value.notice?.res)
        assertFalse(file.exists())
        assertTrue(viewModel.send("halo"))
        assertEquals(listOf<String?>(null), api.sendMediaKeys)
    }

    @Test
    fun `closing the screen cancels the upload and removes the temporary file`() {
        val file = photo()
        api.photoGate = CompletableDeferred()
        val store = ViewModelStore()
        val factory = ChatViewModel.Factory(repository, info, "me", preparer)
        val viewModel = ViewModelProvider(store, factory)[ChatViewModel::class.java]
        viewModel.attach("foto")
        viewModel.send("")
        assertEquals(sending.copy(uploadPercent = 0), viewModel.state.value.local?.status)

        store.clear()
        api.photoGate?.complete(Unit)

        assertFalse(file.exists())
        assertTrue(api.sendKeys.isEmpty())
    }

    @Test
    fun `closing the screen during a video upload cancels it`() {
        video()
        storage.gate = CompletableDeferred()
        val store = ViewModelStore()
        val factory = ChatViewModel.Factory(repository, info, "me", preparer)
        val viewModel = ViewModelProvider(store, factory)[ChatViewModel::class.java]
        viewModel.attach("video")
        viewModel.send("")

        store.clear()

        assertTrue(storage.cancelled)
        assertTrue(api.sendKeys.isEmpty())
    }

    // Membuka lampiran

    @Test
    fun `opening a media message asks for it again and opens the new address without moving the cursor`() = runBlocking {
        api.messagesReply = { after, before ->
            when {
                after != null -> messages()
                before == null -> messages(mediaMessage(5, url = "https://storage.example.org/lama"))
                else -> messages(mediaMessage(5, url = "https://storage.example.org/baru"))
            }
        }
        val viewModel = viewModel()

        viewModel.openMedia(5)

        assertEquals("https://storage.example.org/baru", viewModel.state.value.openMedia?.media?.url)
        assertEquals(null to 6L, api.messageCalls.last())
        viewModel.mediaOpened()
        assertNull(viewModel.state.value.openMedia)

        viewModel.poll()
        assertEquals(5L to null, api.messageCalls.last())
    }

    @Test
    fun `a media message that was deleted meanwhile is not opened`() {
        api.messagesReply = { _, before ->
            if (before == null) messages(mediaMessage(5)) else messages(mediaMessage(5, deleted = true))
        }
        val viewModel = viewModel()

        viewModel.openMedia(5)

        assertNull(viewModel.state.value.openMedia)
        assertTrue(viewModel.state.value.timeline.messages.single().deleted)
        assertEquals(R.string.chat_pesan_dihapus, viewModel.state.value.notice?.res)
    }

    @Test
    fun `offline, a media message still opens with what the screen has`() {
        api.messagesReply = { _, before ->
            if (before == null) messages(mediaMessage(5)) else throw IOException("putus")
        }
        val viewModel = viewModel()

        viewModel.openMedia(5)

        assertEquals(5L, viewModel.state.value.openMedia?.id)
    }

    @Test
    fun `a new address is asked for at most once a minute for each message`() {
        api.messagesReply = { _, _ -> messages(mediaMessage(5)) }
        val viewModel = viewModel()

        viewModel.refreshMedia(5)
        viewModel.refreshMedia(5)
        assertEquals(2, api.messageCalls.size)

        clock = 60_000L
        viewModel.refreshMedia(5)
        assertEquals(3, api.messageCalls.size)

        viewModel.refreshMedia(99)
        assertEquals(3, api.messageCalls.size)
    }

    @Test
    fun `many photos that fail at once ask for new addresses at most ten times a minute`() {
        val all = (1L..12L).map { mediaMessage(it) }.toTypedArray()
        api.messagesReply = { _, _ -> messages(*all) }
        val viewModel = viewModel()

        (1L..12L).forEach { viewModel.refreshMedia(it) }
        assertEquals(11, api.messageCalls.size)

        clock = 60_000L
        viewModel.refreshMedia(12)
        assertEquals(12, api.messageCalls.size)
    }
}
