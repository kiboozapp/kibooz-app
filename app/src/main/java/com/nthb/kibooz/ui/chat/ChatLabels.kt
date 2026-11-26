package com.nthb.kibooz.ui.chat

import androidx.annotation.StringRes
import com.nthb.kibooz.R
import com.nthb.kibooz.data.remote.CHAT_MEDIA_PHOTO
import com.nthb.kibooz.data.remote.CHAT_MEDIA_VIDEO
import com.nthb.kibooz.data.remote.ChatContact
import com.nthb.kibooz.data.remote.ChatMedia
import com.nthb.kibooz.data.remote.ChatMessage
import com.nthb.kibooz.data.remote.ChatPerson
import com.nthb.kibooz.util.TimeFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Cara menampilkan isi sebuah pesan. Di daftar percakapan lampiran tanpa teks hanya disebut jenisnya. */
enum class PreviewKind { TEXT, DELETED, PHOTO, VIDEO, ATTACHMENT, EMPTY }

fun ChatMessage.previewKind(): PreviewKind = when {
    deleted -> PreviewKind.DELETED
    body.isNotBlank() -> PreviewKind.TEXT
    media?.kind == "photo" -> PreviewKind.PHOTO
    media?.kind == "video" -> PreviewKind.VIDEO
    media != null -> PreviewKind.ATTACHMENT
    else -> PreviewKind.EMPTY
}

/** Lampiran yang tampil di gelembung. Null untuk pesan yang dihapus dan untuk jenis yang tidak dikenal. */
fun ChatMessage.shownMedia(): ChatMedia? =
    media?.takeIf { !deleted && (it.kind == CHAT_MEDIA_PHOTO || it.kind == CHAT_MEDIA_VIDEO) }

/** Ukuran berkas untuk dibaca orang, misalnya "320 KB" atau "3,2 MB". */
fun fileSizeLabel(bytes: Long): String {
    if (bytes < BYTES_PER_MB) return "${((bytes + 1023) / 1024).coerceAtLeast(1)} KB"

    return String.format(Locale("id", "ID"), "%.1f MB", bytes.toDouble() / BYTES_PER_MB)
}

private const val BYTES_PER_MB = 1024L * 1024

/** Nama yang tampil untuk [person]. Server mengirim nama bahasa Inggris untuk akun yang dihapus, jadi diganti. */
fun ChatPerson.displayName(deletedLabel: String): String = if (deleted) deletedLabel else fullName

/** Judul kontak. Untuk wali dengan nama anak di kelas bersama, misalnya "Siti (Rani)". */
fun ChatContact.title(): String =
    if (children.isEmpty()) fullName else "$fullName (${children.joinToString(", ")})"

/** Kelas yang dibagi dengan kontak, dipisah koma. */
fun ChatContact.classNames(): String = classes.joinToString(", ") { it.name }

/**
 * Waktu pesan terakhir di daftar percakapan. Hari ini berupa jam ("07:05"), hari lain berupa tanggal singkat
 * ("25 Nov 2026"). Null jika waktu dari server tidak terbaca.
 */
fun listTimeLabel(iso: String, now: Date = Date(), zone: TimeZone = TimeZone.getDefault()): String? {
    val time = TimeFormat.localTime(iso, zone) ?: return null
    val day = TimeFormat.localDate(iso, zone) ?: return time
    val today = SimpleDateFormat("d MMMM yyyy", Locale("id", "ID")).apply { timeZone = zone }.format(now)

    return if (day == today) time else TimeFormat.localDateTime(iso, zone)?.substringBefore(",") ?: day
}

/** Satu atau dua huruf awal nama untuk avatar pengganti foto. Kosong jika nama kosong. */
fun initialsOf(name: String): String {
    val letters = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2).map { it.first().uppercaseChar() }
    return letters.joinToString("")
}

/**
 * Alasan kotak tulis ditutup. Server hanya mengirim `canSend` dan tidak mengirim alasannya, jadi alasannya
 * disimpulkan dari jenis percakapan dan dari status akun lawan bicara.
 */
@StringRes
fun closedReasonRes(isRoom: Boolean, peerDeleted: Boolean): Int = when {
    isRoom -> R.string.chat_ditutup_kelas_arsip
    peerDeleted -> R.string.chat_ditutup_akun_dihapus
    else -> R.string.chat_ditutup_tidak_sekelas
}
