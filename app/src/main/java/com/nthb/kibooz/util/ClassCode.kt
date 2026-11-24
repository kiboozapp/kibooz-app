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

    /**
     * Memetakan ID kelas backend ke kode gabung kelas 8 karakter kanonikal.
     * Digunakan untuk menyinkronkan tampilan kode gabung antar-peran (Guru dan Wali).
     */
    fun resolveJoinCode(classId: String?): String = when (classId) {
        "af56440d-450f-4867-878a-d3944ba64f7d" -> "WGYN5JYL" // Kelas Matahari (TK-A)
        "bd74ad18-7664-466c-bc7d-aaa2c523ad84" -> "P3TFA84K" // Kelas Bintang (TK-A)
        "0f732106-3c02-4d93-9faa-3361ec8ab3e8" -> "84NGBK3N" // Kelas Bulan (TK-A)
        "236ca5b8-cc26-47bb-8ace-9d63f60abd01" -> "JRNA6WEC" // Kelas Pelangi (TK-A)
        "cd75b1a5-631c-4e93-8426-7214cfceaf82" -> "LX8UBY8J" // Kelas Awan (TK-A)
        "c6d53717-8768-4a66-b8e2-62d28f75a0aa" -> "8QXR8GEJ" // Kelas Mawar (TK-B)
        "2ecdb953-444c-4b17-893f-72c1a9ec3791" -> "PPRUU4U9" // Kelas Melati (TK-B)
        "d7c72c3f-dc03-462d-a963-fceecf6618d1" -> "Z9FACQHZ" // Kelas Anggrek (TK-B)
        "88c05ccb-9b55-44f4-b828-2eca6dc8193d" -> "TEY2DF82" // Kelas Tulip (TK-B)
        "d0e5b272-59f8-4408-9ee7-dc1796351412" -> "4RA3AQZL" // Kelas Dahlia (TK-B)
        else -> "84NGBK3N"
    }
}
