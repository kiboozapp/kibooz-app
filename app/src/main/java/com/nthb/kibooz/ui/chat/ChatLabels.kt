package com.nthb.kibooz.ui.chat

import androidx.annotation.StringRes
import com.nthb.kibooz.R
import com.nthb.kibooz.data.remote.ChatContact
import com.nthb.kibooz.data.remote.ChatMessage
import com.nthb.kibooz.data.remote.ChatPerson

/** Cara menampilkan isi sebuah pesan di daftar percakapan. Lampiran belum bisa dibuka, jadi hanya jenisnya yang disebut. */
enum class PreviewKind { TEXT, DELETED, PHOTO, VIDEO, ATTACHMENT, EMPTY }

fun ChatMessage.previewKind(): PreviewKind = when {
    deleted -> PreviewKind.DELETED
    body.isNotBlank() -> PreviewKind.TEXT
    media?.kind == "photo" -> PreviewKind.PHOTO
    media?.kind == "video" -> PreviewKind.VIDEO
    media != null -> PreviewKind.ATTACHMENT
    else -> PreviewKind.EMPTY
}

/** Nama yang tampil untuk [person]. Server mengirim nama bahasa Inggris untuk akun yang dihapus, jadi diganti. */
fun ChatPerson.displayName(deletedLabel: String): String = if (deleted) deletedLabel else fullName

/** Judul kontak. Untuk wali dengan nama anak di kelas bersama, misalnya "Siti (Rani)". */
fun ChatContact.title(): String =
    if (children.isEmpty()) fullName else "$fullName (${children.joinToString(", ")})"

/** Kelas yang dibagi dengan kontak, dipisah koma. */
fun ChatContact.classNames(): String = classes.joinToString(", ") { it.name }

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
