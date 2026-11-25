package com.nthb.kibooz.ui.chat

import androidx.annotation.StringRes
import com.nthb.kibooz.data.remote.ChatMessage
import com.nthb.kibooz.util.TimeFormat
import java.util.TimeZone

/** Satu baris di daftar pesan. [key] membedakan baris untuk DiffUtil. */
sealed interface ChatRow {
    val key: String
}

/** Baris paling atas selama masih ada pesan yang lebih lama. Memuat, atau menunjukkan galat dengan ketukan untuk mencoba lagi. */
data class OlderRow(val loading: Boolean, @StringRes val error: Int?) : ChatRow {
    override val key: String get() = "older"
}

data class DateRow(val label: String) : ChatRow {
    override val key: String get() = "d:$label"
}

data class MessageRow(
    val message: ChatMessage,
    val own: Boolean,
    /** Jam lokal, kosong jika waktu dari server tidak terbaca. */
    val time: String,
    /** Pesan sendiri di percakapan langsung yang sudah dibaca lawan bicara. */
    val readByPeer: Boolean,
    /** Pesan sendiri yang bisa dihapus sekarang. */
    val deletable: Boolean,
    val deleting: Boolean
) : ChatRow {
    override val key: String get() = "m:${message.id}"
}

/** Pesan sendiri yang belum diterima server. Selalu paling bawah. */
data class LocalRow(val local: LocalMessage) : ChatRow {
    override val key: String get() = "local"
}

/**
 * Menyusun baris layar dari state: penanda tanggal tiap ganti hari, pesan urut `id`, lalu pesan sendiri yang
 * masih menunggu atau gagal. Fungsi murni tanpa Android supaya bisa diuji sendiri.
 */
fun buildChatRows(
    state: ChatState,
    ownUserId: String,
    isRoom: Boolean,
    zone: TimeZone = TimeZone.getDefault()
): List<ChatRow> {
    val rows = mutableListOf<ChatRow>()
    if (state.hasMoreOlder) rows += OlderRow(state.loadingOlder, state.olderError)

    var lastDay: String? = null
    for (message in state.timeline.messages) {
        val day = TimeFormat.localDate(message.createdAt, zone)
        if (day != null && day != lastDay) {
            rows += DateRow(day)
            lastDay = day
        }

        val own = message.senderId == ownUserId
        rows += MessageRow(
            message = message,
            own = own,
            time = TimeFormat.localTime(message.createdAt, zone).orEmpty(),
            readByPeer = own && !isRoom && !message.deleted && message.id <= state.peerLastReadMessageId,
            deletable = own && !message.deleted && state.deletingId == null,
            deleting = state.deletingId == message.id
        )
    }

    state.local?.let { rows += LocalRow(it) }
    return rows
}
