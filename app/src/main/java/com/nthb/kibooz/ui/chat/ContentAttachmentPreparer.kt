package com.nthb.kibooz.ui.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.provider.OpenableColumns
import com.nthb.kibooz.R
import com.nthb.kibooz.data.remote.CHAT_MEDIA_PHOTO
import com.nthb.kibooz.data.remote.CHAT_MEDIA_VIDEO
import com.nthb.kibooz.data.remote.CHAT_PHOTO_MAX_BYTES
import com.nthb.kibooz.data.remote.CHAT_VIDEO_MAX_BYTES
import com.nthb.kibooz.data.remote.CHAT_VIDEO_TYPES
import com.nthb.kibooz.data.remote.ChatAttachment
import com.nthb.kibooz.util.decodeOrientedBitmap
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Menyiapkan berkas dari pemilih foto. Video dibaca langsung dari Uri saat diunggah. Foto selalu disalin atau
 * diperkecil ke berkas sementara di cache, yang dihapus lewat [ChatAttachment.release].
 */
class ContentAttachmentPreparer(context: Context) : AttachmentPreparer {

    private val resolver = context.applicationContext.contentResolver
    private val directory = File(context.applicationContext.cacheDir, "chat_media")

    override suspend fun prepare(uri: String): PreparedAttachment {
        var temporary: File? = null

        return try {
            withContext(Dispatchers.IO) {
                val source = Uri.parse(uri)
                val type = resolver.getType(source)?.lowercase().orEmpty()

                when {
                    type.startsWith("video/") -> video(source, type)
                    type.startsWith("image/") -> photo(source, type) { temporary = it }
                    else -> PreparedAttachment.Rejected(R.string.chat_error_jenis_lampiran)
                }
            }
        } catch (e: CancellationException) {
            temporary?.delete()
            throw e
        } catch (_: Exception) {
            // Penyedia berkas bisa melempar apa saja, dari berkas hilang sampai izin dicabut
            temporary?.delete()
            unreadable()
        } catch (_: OutOfMemoryError) {
            temporary?.delete()
            unreadable()
        }
    }

    private fun video(source: Uri, type: String): PreparedAttachment {
        if (type !in CHAT_VIDEO_TYPES) return PreparedAttachment.Rejected(R.string.chat_error_format_video)

        // Ukuran dihitung dari berkasnya, tanpa disimpan di memori, karena angka dari penyedia bisa salah dan
        // unggahan menuntut jumlah byte yang tepat
        val size = readUpTo(source, CHAT_VIDEO_MAX_BYTES)
        if (size > CHAT_VIDEO_MAX_BYTES) return PreparedAttachment.Rejected(R.string.chat_error_video_terlalu_besar)
        if (size <= 0) return unreadable()

        return PreparedAttachment.Ready(
            ChatAttachment(CHAT_MEDIA_VIDEO, type, size) { resolver.openInputStream(source) }
        )
    }

    private fun photo(source: Uri, type: String, created: (File) -> Unit): PreparedAttachment {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open(source).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return unreadable()

        removeLeftovers()
        directory.mkdirs()

        if (!ChatPhotoRules.needsReencode(type, declaredSize(source), bounds.outWidth, bounds.outHeight)) {
            val copy = File.createTempFile(PREFIX, "." + type.substringAfter('/'), directory).also(created)
            val copied = copy.outputStream().use { readUpTo(source, CHAT_PHOTO_MAX_BYTES, it) }
            if (copied in 1..CHAT_PHOTO_MAX_BYTES) return ready(copy, type)
            // Ukuran yang dilaporkan penyedia salah, jadi foto diperkecil
            copy.delete()
        }

        val bitmap = decodeOrientedBitmap(ChatPhotoRules.MAX_SIDE) { resolver.openInputStream(source) }
            ?: return unreadable()
        val jpeg = File.createTempFile(PREFIX, ".jpg", directory).also(created)

        for ((side, quality) in ENCODE_STEPS) {
            // Penulisan yang gagal, misalnya penyimpanan penuh, bisa meninggalkan JPEG terpotong
            if (!writeJpeg(bitmap, side, quality, jpeg)) break
            if (jpeg.length() in 1..CHAT_PHOTO_MAX_BYTES) {
                bitmap.recycle()
                return ready(jpeg, "image/jpeg")
            }
        }

        bitmap.recycle()
        jpeg.delete()
        return unreadable()
    }

    private fun ready(file: File, type: String) = PreparedAttachment.Ready(
        ChatAttachment(CHAT_MEDIA_PHOTO, type, file.length(), tempFile = file) { file.inputStream() }
    )

    // JPEG tidak punya transparansi, jadi bagian transparan digambar di atas putih dan tidak menjadi hitam
    private fun writeJpeg(source: Bitmap, maxSide: Int, quality: Int, target: File): Boolean {
        val (width, height) = ChatPhotoRules.fitWithin(source.width, source.height, maxSide)
        val flat = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(flat).apply {
            drawColor(Color.WHITE)
            drawBitmap(source, null, Rect(0, 0, width, height), Paint(Paint.FILTER_BITMAP_FLAG))
        }
        val written = target.outputStream().use { flat.compress(Bitmap.CompressFormat.JPEG, quality, it) }
        flat.recycle()
        return written
    }

    private fun open(source: Uri): InputStream =
        resolver.openInputStream(source) ?: throw IOException("Berkas tidak bisa dibuka")

    private fun declaredSize(source: Uri): Long? =
        resolver.query(source, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0).takeIf { it > 0 } else null
        }

    /**
     * Membaca berkas dan menyalinnya ke [sink] jika ada. Berhenti begitu melewati [limit], dan mengembalikan jumlah
     * byte yang terbaca.
     */
    private fun readUpTo(source: Uri, limit: Long, sink: OutputStream? = null): Long = open(source).use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (total <= limit) {
            val count = input.read(buffer)
            if (count == -1) break
            sink?.write(buffer, 0, count)
            total += count
        }
        total
    }

    // Proses yang mati tidak sempat menghapus berkasnya. Hanya yang sudah lama, supaya pesan yang masih menunggu aman
    private fun removeLeftovers() {
        val oldest = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)
        directory.listFiles()?.filter { it.lastModified() < oldest }?.forEach { it.delete() }
    }

    private fun unreadable() = PreparedAttachment.Rejected(R.string.chat_error_lampiran_tidak_terbaca)

    private companion object {
        const val PREFIX = "chat_"

        /** Pasangan sisi terpanjang dan mutu JPEG, dicoba berurutan sampai ukurannya di bawah batas. */
        val ENCODE_STEPS = listOf(ChatPhotoRules.MAX_SIDE to 80, 1280 to 75, 960 to 70)
    }
}
