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

private const val ROLE_GURU = "GURU"
private const val ROLE_WALI = "WALI"
private const val MAX_INLINE_CHILD_NAMES = 2

/** Nama yang tampil untuk [person]. Server mengirim nama bahasa Inggris untuk akun yang dihapus, jadi diganti. */
fun ChatPerson.displayName(deletedLabel: String): String = if (deleted) deletedLabel else fullName

/**
 * Judul kontak. Hanya wali murid yang menampilkan nama anak dalam tanda kurung (misalnya "Siti (Rani)").
 * Bila seorang wali punya lebih dari dua anak, nama diringkas ("Wali Kelas 1A (Aisyah Pratama +9 anak)") agar tetap
 * terbaca rapi. Kontak guru menampilkan nama guru saja supaya tidak terbaca seolah guru adalah wali murid.
 */
fun ChatContact.title(): String {
    val cleanName = fullName.trim()
    val cleanChildren = children.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    if (role.equals(ROLE_GURU, ignoreCase = true) || cleanChildren.isEmpty()) return cleanName

    val summary = if (cleanChildren.size <= MAX_INLINE_CHILD_NAMES) {
        cleanChildren.joinToString(", ")
    } else {
        "${cleanChildren.first()} +${cleanChildren.size - 1} anak"
    }
    return "$cleanName ($summary)"
}

/** Kelas yang dibagi dengan kontak, dipisah koma tanpa duplikat. */
fun ChatContact.classNames(): String = classes.map { it.name.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(", ")

/** Subjudul kontak yang memperjelas peran ("Guru" atau "Wali murid") serta kelas yang dibagikan. */
fun ChatContact.subtitle(guruLabel: String, waliLabel: String): String {
    val roleLabel = when {
        role.equals(ROLE_GURU, ignoreCase = true) -> guruLabel
        role.equals(ROLE_WALI, ignoreCase = true) -> waliLabel
        else -> ""
    }
    return listOf(roleLabel, classNames()).filter { it.isNotEmpty() }.joinToString(" • ")
}

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
