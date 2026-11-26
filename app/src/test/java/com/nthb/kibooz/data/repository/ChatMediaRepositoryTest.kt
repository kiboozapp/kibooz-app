package com.nthb.kibooz.data.repository

import com.nthb.kibooz.data.failureJson
import com.nthb.kibooz.data.remote.ApiClient
import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.CHAT_PHOTO_MAX_BYTES
import com.nthb.kibooz.data.remote.CHAT_VIDEO_MAX_BYTES
import com.nthb.kibooz.data.remote.ChatApi
import com.nthb.kibooz.data.remote.ChatAttachment
import com.nthb.kibooz.data.remote.ChatUploadSourceException
import com.nthb.kibooz.data.remote.ERROR_DOWNLOAD_REJECTED
import com.nthb.kibooz.data.remote.ERROR_INSECURE_ADDRESS
import com.nthb.kibooz.data.remote.ERROR_UPLOAD_REJECTED
import com.nthb.kibooz.data.remote.ERROR_UPLOAD_SOURCE
import com.nthb.kibooz.data.remote.ExactLengthRequestBody
import com.nthb.kibooz.data.remote.OkHttpChatStorage
import com.squareup.moshi.Moshi
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout

/** Unggah dan unduh lampiran chat. API dan storage adalah dua server terpisah, seperti di produksi. */
class ChatMediaRepositoryTest {

    @get:Rule
    val timeout: Timeout = Timeout(30, TimeUnit.SECONDS)

    private lateinit var apiServer: MockWebServer
    private lateinit var storageServer: MockWebServer
    private lateinit var client: ApiClient
    private lateinit var storage: OkHttpChatStorage
    private lateinit var repository: ChatRepository

    private val videoBytes = "isi-video-1".toByteArray()
    private val photoBytes = "isi-foto".toByteArray()

    @Before
    fun setUp() {
        apiServer = MockWebServer().apply { start() }
        storageServer = MockWebServer().apply { start() }
        client = testClient(apiServer)
        storage = OkHttpChatStorage(apiBaseUrl = apiServer.url("/api/v1/").toString())
        repository = ChatRepository(client.create(ChatApi::class.java), client.moshi, storage)
    }

    @After
    fun tearDown() {
        client.okHttpClient.connectionPool.evictAll()
        storage.client.connectionPool.evictAll()
        apiServer.shutdown()
        storageServer.shutdown()
    }

    private fun video(bytes: ByteArray = videoBytes) =
        ChatAttachment("video", "video/mp4", bytes.size.toLong()) { ByteArrayInputStream(bytes) }

    private fun photo(bytes: ByteArray = photoBytes) =
        ChatAttachment("photo", "image/jpeg", bytes.size.toLong()) { ByteArrayInputStream(bytes) }

    private fun uploadUrl() = storageServer.url("/bucket/pending/abc.mp4?X-Amz-Signature=s1").toString()

    private fun ticketJson(url: String = uploadUrl()) = """
        {"success":true,"data":{"uploadUrl":"$url","method":"PUT",
         "headers":{"Content-Type":"video/mp4","x-amz-meta-note":"chat"},
         "mediaKey":"chat-pending/abc.mp4","expiresAt":"2026-11-26T07:20:00Z"}}
    """.trimIndent()

    @Suppress("UNCHECKED_CAST")
    private fun bodyOf(request: RecordedRequest): Map<String, Any?> =
        Moshi.Builder().build().adapter(Map::class.java).fromJson(request.body.readUtf8()) as Map<String, Any?>

    // Video

    @Test
    fun `a video asks for an address and then puts exactly those bytes and headers to storage`() = runBlocking {
        apiServer.enqueue(jsonResponse(ticketJson()))
        storageServer.enqueue(MockResponse())
        var sent = 0L

        val result = repository.upload("cv1", video()) { sent = it }

        assertEquals("chat-pending/abc.mp4", (result as ApiResult.Success).data)
        val ask = apiServer.takeRequest()
        assertEquals("POST", ask.method)
        assertEquals("/api/v1/chat/conversations/cv1/media/video-upload", ask.path)
        assertEquals("Bearer jwt", ask.getHeader("Authorization"))
        assertEquals(mapOf<String, Any?>("contentType" to "video/mp4", "sizeBytes" to 11.0), bodyOf(ask))

        val put = storageServer.takeRequest()
        assertEquals("PUT", put.method)
        assertEquals("/bucket/pending/abc.mp4?X-Amz-Signature=s1", put.path)
        assertEquals("video/mp4", put.getHeader("Content-Type"))
        assertEquals("chat", put.getHeader("x-amz-meta-note"))
        assertEquals("11", put.getHeader("Content-Length"))
        assertArrayEquals(videoBytes, put.body.readByteArray())
        assertEquals(11L, sent)
    }

    @Test
    fun `the put to storage carries no token and no cookie even if the server lists them`() = runBlocking {
        storageServer.enqueue(MockResponse())
        val headers = mapOf("Content-Type" to "video/mp4", "authorization" to "Bearer jwt", "Cookie" to "a=b")

        val result = storage.upload(uploadUrl(), headers, video())

        assertTrue(result is ApiResult.Success)
        val put = storageServer.takeRequest()
        assertNull(put.getHeader("Authorization"))
        assertNull(put.getHeader("Cookie"))
        assertEquals("video/mp4", put.getHeader("Content-Type"))
    }

    @Test
    fun `storage that refuses the upload is a failure with its own code`() = runBlocking {
        apiServer.enqueue(jsonResponse(ticketJson()))
        storageServer.enqueue(MockResponse().setResponseCode(403).setBody("<Error>expired</Error>"))

        val result = repository.upload("cv1", video()) as ApiResult.Failure

        assertEquals(403, result.status)
        assertEquals(ERROR_UPLOAD_REJECTED, result.errorCode)
    }

    @Test
    fun `a redirect from storage is not followed`() = runBlocking {
        storageServer.enqueue(MockResponse().setResponseCode(307).setHeader("Location", storageServer.url("/lain")))

        val result = storage.upload(uploadUrl(), emptyMap(), video()) as ApiResult.Failure

        assertEquals(307, result.status)
        assertEquals(1, storageServer.requestCount)
    }

    @Test
    fun `storage that cannot be reached is a network error`() = runBlocking {
        val url = uploadUrl()
        storageServer.shutdown()

        assertTrue(storage.upload(url, emptyMap(), video()) is ApiResult.NetworkError)
    }

    @Test
    fun `a refused video upload request sends nothing to storage`() = runBlocking {
        apiServer.enqueue(jsonResponse(failureJson("CHAT_SENDING_CLOSED"), 409))

        val result = repository.upload("cv1", video()) as ApiResult.Failure

        assertEquals("CHAT_SENDING_CLOSED", result.errorCode)
        assertEquals(0, storageServer.requestCount)
    }

    @Test
    fun `a release build refuses an upload address that is not https`() = runBlocking {
        val release = OkHttpChatStorage(apiBaseUrl = "https://api.example.org/api/v1/")
        val unread = ChatAttachment("video", "video/mp4", 5) { error("tidak boleh dibaca") }

        val put = release.upload(uploadUrl(), emptyMap(), unread) as ApiResult.Failure
        val get = release.download(uploadUrl(), 100) as ApiResult.Failure

        assertEquals(ERROR_INSECURE_ADDRESS, put.errorCode)
        assertEquals(ERROR_INSECURE_ADDRESS, get.errorCode)
        assertEquals(0, storageServer.requestCount)
    }

    // Foto

    @Test
    fun `a photo is sent to the API as a multipart field named file`() = runBlocking {
        apiServer.enqueue(
            jsonResponse(
                """{"success":true,"data":{"mediaKey":"chat-pending/p.jpg","kind":"photo",
                    "contentType":"image/jpeg","sizeBytes":8}}"""
            )
        )

        val result = repository.upload("cv1", photo())

        assertEquals("chat-pending/p.jpg", (result as ApiResult.Success).data)
        val request = apiServer.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/chat/conversations/cv1/media/photo", request.path)
        assertEquals("Bearer jwt", request.getHeader("Authorization"))
        assertTrue(request.getHeader("Content-Type").orEmpty().startsWith("multipart/form-data; boundary="))
        val body = request.body.readUtf8()
        assertTrue(body.contains("name=\"file\"; filename=\"photo.jpeg\""))
        assertTrue(body.contains("Content-Type: image/jpeg"))
        assertTrue(body.contains("isi-foto"))
        assertEquals(0, storageServer.requestCount)
    }

    @Test
    fun `a photo that cannot be opened is a source failure and not a network error`() = runBlocking {
        apiServer.enqueue(MockResponse())
        val gone = ChatAttachment("photo", "image/jpeg", 8) { null }

        val result = repository.upload("cv1", gone) as ApiResult.Failure

        assertEquals(ERROR_UPLOAD_SOURCE, result.errorCode)
    }

    // Batas

    @Test
    fun `files over the limit and of a wrong type are refused before anything is read or sent`() = runBlocking {
        val bigVideo = ChatAttachment("video", "video/mp4", CHAT_VIDEO_MAX_BYTES + 1) { error("tidak boleh dibaca") }
        val bigPhoto = ChatAttachment("photo", "image/jpeg", CHAT_PHOTO_MAX_BYTES + 1) { error("tidak boleh dibaca") }
        val emptyVideo = ChatAttachment("video", "video/mp4", 0) { error("tidak boleh dibaca") }
        val heic = ChatAttachment("photo", "image/heic", 10) { error("tidak boleh dibaca") }
        val mov = ChatAttachment("video", "video/quicktime", 10) { error("tidak boleh dibaca") }

        val codes = listOf(bigVideo, bigPhoto, emptyVideo, heic, mov)
            .map { (repository.upload("cv1", it) as ApiResult.Failure).errorCode }

        assertEquals(
            listOf("FILE_TOO_LARGE", "FILE_TOO_LARGE", "FILE_TOO_LARGE", "INVALID_IMAGE", "VALIDATION_ERROR"),
            codes
        )
        assertEquals(0, apiServer.requestCount)
    }

    @Test
    fun `a video of exactly the limit is announced to the server`() = runBlocking {
        apiServer.enqueue(jsonResponse(failureJson("STORAGE_UNAVAILABLE"), 503))
        val largest = ChatAttachment("video", "video/mp4", CHAT_VIDEO_MAX_BYTES) { error("tidak boleh dibaca") }

        val result = repository.upload("cv1", largest) as ApiResult.Failure

        assertEquals("STORAGE_UNAVAILABLE", result.errorCode)
        assertEquals(CHAT_VIDEO_MAX_BYTES.toDouble(), bodyOf(apiServer.takeRequest())["sizeBytes"])
    }

    @Test
    fun `a stream longer than declared stops being read and nothing past the size is written`() {
        var consumed = 0
        val endless = object : InputStream() {
            override fun read(): Int = 1

            override fun read(b: ByteArray): Int {
                consumed += b.size
                return b.size
            }
        }
        val body = ExactLengthRequestBody(ChatAttachment("video", "video/mp4", 10_000) { endless }, null)
        val sink = Buffer()

        assertThrows(ChatUploadSourceException::class.java) { body.writeTo(sink) }

        assertTrue(sink.size <= 10_000)
        assertTrue(consumed <= 10_000 + 8 * 1024)
    }

    @Test
    fun `a stream shorter than declared and a source that throws are source failures`() {
        val short = ChatAttachment("video", "video/mp4", 50) { ByteArrayInputStream(videoBytes) }
        val denied = ChatAttachment("video", "video/mp4", 50) { throw SecurityException("izin hilang") }

        assertThrows(ChatUploadSourceException::class.java) { ExactLengthRequestBody(short, null).writeTo(Buffer()) }
        assertThrows(ChatUploadSourceException::class.java) { ExactLengthRequestBody(denied, null).writeTo(Buffer()) }
    }

    // Kirim dan unduh

    @Test
    fun `send carries the media key next to an empty body and keeps the idempotency key`() = runBlocking {
        apiServer.enqueue(jsonResponse(failureJson("VALIDATION_ERROR"), 400))
        val first = PendingMessage("")
        val uploaded = first.withMediaKey("chat-pending/abc.mp4")

        repository.send("cv1", uploaded)

        val request = apiServer.takeRequest()
        assertEquals(mapOf<String, Any?>("body" to "", "mediaKey" to "chat-pending/abc.mp4"), bodyOf(request))
        assertEquals(first.idempotencyKey, request.getHeader("Idempotency-Key"))
    }

    @Test
    fun `download returns the bytes without a token`() = runBlocking {
        storageServer.enqueue(MockResponse().setBody("isi-foto"))

        val result = storage.download(uploadUrl(), 100) as ApiResult.Success

        assertArrayEquals(photoBytes, result.data)
        assertNull(storageServer.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `download refuses a file over the limit and an expired address`() = runBlocking {
        storageServer.enqueue(MockResponse().setBody("isi-foto"))
        storageServer.enqueue(MockResponse().setResponseCode(403))

        val large = storage.download(uploadUrl(), 7) as ApiResult.Failure
        val expired = storage.download(uploadUrl(), 100) as ApiResult.Failure

        assertEquals("FILE_TOO_LARGE", large.errorCode)
        assertEquals(ERROR_DOWNLOAD_REJECTED, expired.errorCode)
    }
}
