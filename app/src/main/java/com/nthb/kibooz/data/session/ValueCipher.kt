package com.nthb.kibooz.data.session

/** Mengenkripsi satu nilai teks menjadi teks lain yang aman disimpan di berkas. */
interface ValueCipher {
    fun encrypt(plain: String): String

    /** Melempar [UndecryptableException] jika [encrypted] rusak, diubah, atau kuncinya sudah hilang. */
    fun decrypt(encrypted: String): String
}

class UndecryptableException(cause: Throwable? = null) : Exception("Nilai tersimpan tidak bisa didekripsi", cause)
