package com.nthb.kibooz.ui.chat

import androidx.annotation.StringRes
import com.nthb.kibooz.data.remote.ChatAttachment

/** Hasil menyiapkan berkas yang dipilih pengguna. */
sealed interface PreparedAttachment {
    data class Ready(val attachment: ChatAttachment) : PreparedAttachment

    /** Berkasnya tidak bisa dikirim, [error] menjelaskan alasannya. */
    data class Rejected(@StringRes val error: Int) : PreparedAttachment
}

/**
 * Mengubah berkas pilihan pengguna menjadi lampiran yang boleh diunggah. Dipisah dari Android supaya
 * [ChatViewModel] bisa diuji. Pembatalan harus menghapus berkas sementara yang sudah dibuat.
 */
fun interface AttachmentPreparer {
    suspend fun prepare(uri: String): PreparedAttachment
}
