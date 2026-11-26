package com.nthb.kibooz.util

/** Kode gabung kelas. API menerima 4 sampai 12 huruf atau angka tanpa tanda hubung dan tidak membedakan huruf besar kecil. */
object ClassCode {

    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 12

    private val PATTERN = Regex("^[A-Za-z0-9]{$MIN_LENGTH,$MAX_LENGTH}$")

    /** Mengembalikan kode dalam huruf besar, atau null jika [raw] bukan kode yang diterima API. */
    fun normalize(raw: String): String? {
        val trimmed = raw.trim()
        return if (PATTERN.matches(trimmed)) trimmed.uppercase() else null
    }

    /** Mengembalikan kode kiriman server tanpa spasi di tepi, atau string kosong jika server tidak mengirimnya. Aplikasi tidak pernah menebak kode. */
    fun fromServer(apiCode: String?): String = apiCode?.trim().orEmpty()
}
