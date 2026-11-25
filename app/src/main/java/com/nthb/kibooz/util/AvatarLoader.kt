package com.nthb.kibooz.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.widget.ImageView
import androidx.annotation.DrawableRes
import com.nthb.kibooz.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Pemuat foto profil jarak jauh ringan dengan LruCache memori dan pelindung race condition via tag view.
 * Memakai [OkHttpClient] proyek yang sudah tersedia tanpa menambah dependensi eksternal baru.
 */
class AvatarLoader(private val client: OkHttpClient) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val cache: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(MAX_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun load(imageView: ImageView, url: String?, @DrawableRes placeholderRes: Int) {
        val cleanUrl = url?.trim()?.takeIf { it.isNotEmpty() }
        imageView.setTag(R.id.tag_avatar_url, cleanUrl)

        if (cleanUrl == null) {
            imageView.setImageResource(placeholderRes)
            return
        }

        cache.get(cleanUrl)?.let { cached ->
            imageView.setImageBitmap(cached)
            return
        }

        imageView.setImageResource(placeholderRes)
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) { fetchAndDecode(cleanUrl) }
            if (bitmap != null) {
                cache.put(cleanUrl, bitmap)
                if (imageView.getTag(R.id.tag_avatar_url) == cleanUrl) {
                    imageView.setImageBitmap(bitmap)
                }
            }
        }
    }

    fun evict(url: String?) {
        url?.trim()?.takeIf { it.isNotEmpty() }?.let { cache.remove(it) }
    }

    fun clear() {
        cache.evictAll()
    }

    private fun fetchAndDecode(url: String): Bitmap? = try {
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val bytes = response.body?.bytes() ?: return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val opts = BitmapFactory.Options().apply {
                inSampleSize = AvatarCropMath.computeSampleSize(bounds.outWidth, bounds.outHeight, AVATAR_MAX_DECODE_SIDE)
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        }
    } catch (_: Exception) {
        null
    }

    private companion object {
        const val MAX_CACHE_BYTES = 8 * 1024 * 1024 // 8 MiB
        const val AVATAR_MAX_DECODE_SIDE = 512
    }
}
