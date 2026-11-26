package com.nthb.kibooz.data.remote

import java.io.File
import java.io.IOException
import java.io.InputStream
import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink

const val CHAT_MEDIA_PHOTO = "photo"
const val CHAT_MEDIA_VIDEO = "video"

/** Batas bawaan server, lihat `docs/client-guide.md` backend. */
const val CHAT_PHOTO_MAX_BYTES = 4L * 1024 * 1024
const val CHAT_VIDEO_MAX_BYTES = 25L * 1024 * 1024

val CHAT_PHOTO_TYPES = setOf("image/jpeg", "image/png", "image/webp")
val CHAT_VIDEO_TYPES = setOf("video/mp4", "video/webm")

/** Kode galat buatan aplikasi untuk kegagalan unggah dan unduh yang tidak punya `errorCode` dari API. */
const val ERROR_UPLOAD_SOURCE = "UPLOAD_SOURCE_UNREADABLE"
const val ERROR_UPLOAD_REJECTED = "UPLOAD_REJECTED"
const val ERROR_DOWNLOAD_REJECTED = "DOWNLOAD_REJECTED"
const val ERROR_INSECURE_ADDRESS = "INSECURE_STORAGE_ADDRESS"

/** Byte yang akan diunggah. */
interface UploadSource {
    val contentType: String
    val sizeBytes: Long

    /** Dipanggil lagi di setiap percobaan, jadi harus memberi aliran baru dari awal. Null jika tidak bisa dibuka. */
    fun open(): InputStream?
}

/** Satu foto atau video yang dipilih untuk sebuah pesan. */
class ChatAttachment(
    /** [CHAT_MEDIA_PHOTO] atau [CHAT_MEDIA_VIDEO]. */
    val kind: String,
    override val contentType: String,
    override val sizeBytes: Long,
    /** Berkas sementara milik lampiran ini. Null jika byte dibaca langsung dari sumbernya. */
    val tempFile: File? = null,
    private val opener: () -> InputStream?
) : UploadSource {
    override fun open(): InputStream? = opener()

    /** Menghapus berkas sementara. Aman dipanggil lebih dari sekali. */
    fun release() {
        tempFile?.delete()
    }
}

/** Sumber unggahan tidak bisa dibaca, atau panjangnya tidak sama dengan yang diumumkan. */
class ChatUploadSourceException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * Mengalirkan [source] tanpa memuat seluruh isinya ke memori. Panjangnya harus tepat [UploadSource.sizeBytes],
 * karena alamat bertanda tangan menolak ukuran lain. Byte di atas ukuran itu tidak pernah ditulis.
 *
 * [mediaType] null berarti header `Content-Type` diatur oleh pemanggil.
 */
class ExactLengthRequestBody(
    private val source: UploadSource,
    private val mediaType: MediaType?,
    private val onProgress: (sentBytes: Long) -> Unit = {}
) : RequestBody() {

    private var sourceBroken = false

    override fun contentType(): MediaType? = mediaType

    override fun contentLength(): Long = source.sizeBytes

    // Sumber yang rusak tidak dicoba lagi lewat rute lain, supaya galatnya tidak tertutup galat koneksi
    override fun isOneShot(): Boolean = sourceBroken

    override fun writeTo(sink: BufferedSink) {
        try {
            stream(sink)
        } catch (e: ChatUploadSourceException) {
            sourceBroken = true
            throw e
        }
    }

    private fun stream(sink: BufferedSink) {
        val expected = source.sizeBytes
        val input = reading { source.open() } ?: throw ChatUploadSourceException("Sumber tidak bisa dibuka")

        input.use {
            val buffer = ByteArray(BUFFER_BYTES)
            var sent = 0L

            while (true) {
                val count = reading { it.read(buffer) }
                if (count == -1) break
                if (sent + count > expected) throw ChatUploadSourceException("Sumber lebih panjang dari $expected byte")
                sink.write(buffer, 0, count)
                sent += count
                onProgress(sent)
            }

            if (sent != expected) throw ChatUploadSourceException("Sumber hanya $sent dari $expected byte")
        }
    }

    // OkHttp menjatuhkan proses jika penulisan body melempar selain IOException, misalnya SecurityException dari Uri
    private fun <T> reading(block: () -> T): T = try {
        block()
    } catch (e: IOException) {
        throw ChatUploadSourceException("Sumber gagal dibaca", e)
    } catch (e: RuntimeException) {
        throw ChatUploadSourceException("Sumber gagal dibaca", e)
    }

    private companion object {
        const val BUFFER_BYTES = 8 * 1024
    }
}
