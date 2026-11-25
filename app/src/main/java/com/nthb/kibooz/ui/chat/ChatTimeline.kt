package com.nthb.kibooz.ui.chat

import com.nthb.kibooz.data.remote.ChatMessage

/**
 * Pesan sebuah percakapan yang sudah dimuat, selalu urut dari `id` terkecil. Class biasa tanpa Android, jadi
 * penggabungan hasil muat awal, muat pesan lama, polling dan balasan kirim bisa diuji sendiri.
 */
data class ChatTimeline(val messages: List<ChatMessage> = emptyList()) {

    /** `id` terbesar, kursor `after` untuk polling. Null selama belum ada pesan. */
    val newestId: Long? get() = messages.lastOrNull()?.id

    /** `id` terkecil, kursor `before` untuk memuat pesan yang lebih lama. */
    val oldestId: Long? get() = messages.firstOrNull()?.id

    /**
     * Menggabungkan [incoming] tanpa menggandakan. Pesan dengan `id` yang sama diganti oleh kiriman terbaru, tetapi
     * pesan yang sudah dihapus tidak hidup lagi oleh salinan lama yang datang terlambat.
     */
    fun merge(incoming: List<ChatMessage>): ChatTimeline {
        if (incoming.isEmpty()) return this

        val byId = LinkedHashMap<Long, ChatMessage>(messages.size + incoming.size)
        messages.forEach { byId[it.id] = it }
        incoming.forEach { next ->
            val current = byId[next.id]
            byId[next.id] = if (current != null && current.deleted && !next.deleted) current else next
        }

        return ChatTimeline(byId.values.sortedBy { it.id })
    }
}
