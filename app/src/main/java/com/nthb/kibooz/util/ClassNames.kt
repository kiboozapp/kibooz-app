package com.nthb.kibooz.util

import android.content.Context
import com.nthb.kibooz.R

/**
 * Menormalkan nama kelas dari server agar template berawalan "Kelas", "kelas", atau "Ruang kelas" tidak menggandakan
 * kata "kelas" (misalnya "Ruang kelas Kelas Matahari" menjadi "Ruang kelas Matahari").
 */
object ClassNames {

    private val LEADING_CLASS_PREFIX = Regex("^(?:kelas)(?:\\s+|[:\\-]\\s*)", RegexOption.IGNORE_CASE)

    /** Menghapus awalan "Kelas " berulang bila ada, misalnya "Kelas Matahari" menjadi "Matahari". */
    fun stripPrefix(rawName: String?): String {
        var current = rawName?.trim().orEmpty()
        while (true) {
            val next = LEADING_CLASS_PREFIX.replaceFirst(current, "").trimStart()
            if (next == current) break
            current = next
        }
        return if (current.equals("kelas", ignoreCase = true)) "" else current
    }

    /** Memastikan label memiliki tepat satu awalan "Kelas", atau [fallback] jika nama kosong. */
    fun withPrefix(rawName: String?, fallback: String = "Kelas"): String {
        val stem = stripPrefix(rawName)
        return if (stem.isEmpty()) fallback else "Kelas $stem"
    }

    /** Memilih inti nama setelah awalan "Kelas ", atau memakai [fallback] bila hasilnya kosong. */
    fun stemOrFallback(rawName: String?, fallback: String): String =
        stripPrefix(rawName).ifEmpty { fallback }

    /** Format judul ruang kelas ("Ruang kelas Matahari") memakai template dan fallback terlokalisasi. */
    fun chatRoomTitle(rawName: String?, formatRoomWithName: (String) -> String, fallbackRoomOnly: String): String {
        val stem = stripPrefix(rawName)
        return if (stem.isEmpty()) fallbackRoomOnly else formatRoomWithName(stem)
    }

    fun chatRoomTitle(context: Context, rawName: String?): String =
        chatRoomTitle(
            rawName = rawName,
            formatRoomWithName = { stem -> context.getString(R.string.chat_ruang_judul, stem) },
            fallbackRoomOnly = context.getString(R.string.chat_bagian_ruang_kelas)
        )
}
