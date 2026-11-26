package com.nthb.kibooz.ui.chat

import android.graphics.Bitmap
import android.util.LruCache
import android.widget.ImageView
import com.nthb.kibooz.R
import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.CHAT_PHOTO_MAX_BYTES
import com.nthb.kibooz.data.remote.ChatStorage
import com.nthb.kibooz.util.decodeOrientedBitmap
import java.io.ByteArrayInputStream
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Memuat foto lampiran chat. Bitmap disimpan di memori menurut pesannya, bukan menurut alamatnya, karena alamat
 * bertanda tangan berganti di setiap jawaban server. Tidak ada yang ditulis ke disk. Hanya dipakai di main thread.
 */
class ChatMediaLoader(private val storage: ChatStorage) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val cache: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(MAX_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    /** Unduhan yang sedang jalan menurut sumbernya, supaya satu foto tidak diambil dua kali sekaligus. */
    private val running = HashMap<String, Deferred<Bitmap?>>()

    /**
     * Menampilkan foto pesan [messageKey] di [target]. [onResult] menerima false jika fotonya gagal dimuat, dan
     * tidak dipanggil jika [target] sudah dipakai untuk foto lain. Dengan [full] foto didekode lebih besar, dan
     * gambar kecil yang tersimpan tampil dulu.
     */
    fun showPhoto(
        target: ImageView,
        messageKey: String,
        url: String,
        full: Boolean = false,
        onResult: (loaded: Boolean) -> Unit
    ) {
        val side = if (full) FULL_SIDE else THUMBNAIL_SIDE
        val placeholder = if (full) cache.get("$messageKey@$THUMBNAIL_SIDE") else null

        show(target, "$messageKey@$side", url, placeholder, onResult) {
            val bytes = (storage.download(url, MAX_DOWNLOAD_BYTES) as? ApiResult.Success)?.data
            bytes?.let { data ->
                withContext(Dispatchers.IO) { decodeOrientedBitmap(side) { ByteArrayInputStream(data) } }
            }
        }
    }

    /** Menampilkan foto yang belum terkirim dari berkas sementaranya. */
    fun showFile(target: ImageView, file: File, onResult: (loaded: Boolean) -> Unit = {}) {
        show(target, file.absolutePath, file.absolutePath, null, onResult) {
            withContext(Dispatchers.IO) { decodeOrientedBitmap(THUMBNAIL_SIDE) { file.inputStream() } }
        }
    }

    /** Memastikan hasil unduhan yang datang terlambat tidak mengisi [target] lagi. */
    fun clear(target: ImageView) {
        target.setTag(R.id.tag_chat_media, null)
        target.setImageDrawable(null)
    }

    private fun show(
        target: ImageView,
        cacheKey: String,
        source: String,
        placeholder: Bitmap?,
        onResult: (loaded: Boolean) -> Unit,
        load: suspend () -> Bitmap?
    ) {
        target.setTag(R.id.tag_chat_media, cacheKey)
        cache.get(cacheKey)?.let {
            target.setImageBitmap(it)
            onResult(true)
            return
        }

        // setImageBitmap(null) tetap memasang drawable kosong, padahal layar memeriksa drawable yang null
        if (placeholder != null) target.setImageBitmap(placeholder) else target.setImageDrawable(null)
        val job = running.getOrPut(source + cacheKey) { scope.async { load()?.also { cache.put(cacheKey, it) } } }
        scope.launch {
            val bitmap = job.await()
            if (running[source + cacheKey] === job) running.remove(source + cacheKey)
            if (target.getTag(R.id.tag_chat_media) != cacheKey) return@launch

            if (bitmap != null) target.setImageBitmap(bitmap)
            onResult(bitmap != null)
        }
    }

    private companion object {
        const val MAX_CACHE_BYTES = 24 * 1024 * 1024

        // Sisi sampel, hasil dekodenya paling besar hampir dua kali angka ini
        const val THUMBNAIL_SIDE = 480
        const val FULL_SIDE = 1280

        // Batas foto di server bisa diatur, jadi unduhan diberi kelonggaran
        const val MAX_DOWNLOAD_BYTES = CHAT_PHOTO_MAX_BYTES * 2
    }
}
