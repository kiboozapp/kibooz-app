package com.nthb.kibooz.util

import androidx.annotation.StringRes
import com.nthb.kibooz.R

/**
 * Aturan isian yang sama dengan batas di `openapi.yaml` backend, supaya salah ketik tertangkap sebelum
 * request dikirim. Setiap fungsi mengembalikan pesan galat, atau null jika isiannya boleh dikirim.
 * Server tetap memeriksa ulang. Panjang dihitung dalam karakter, kecuali kata sandi yang dihitung dalam byte.
 */
object FormRules {

    const val EMAIL_MAX = 255
    const val PASSWORD_MIN = 8
    const val PASSWORD_MAX_BYTES = 72
    const val FULL_NAME_MIN = 2
    const val FULL_NAME_MAX = 150
    const val NISN_MIN = 5
    const val NISN_MAX = 30
    const val CLASS_NAME_MAX = 50
    const val PHONE_MAX = 30
    const val ADDRESS_MAX = 1000
    const val FORUM_TITLE_MAX = 150
    const val FORUM_BODY_MAX = 5000
    const val NOTES_MAX = 500

    private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    private val NISN = Regex("^[0-9]+$")
    private val PHONE = Regex("^\\+?[0-9 ()-]*$")

    @StringRes
    fun email(value: String): Int? {
        val email = value.trim()
        return when {
            email.isEmpty() -> R.string.form_email_kosong
            email.length > EMAIL_MAX -> R.string.form_email_terlalu_panjang
            !EMAIL.matches(email) -> R.string.form_email_tidak_valid
            else -> null
        }
    }

    /** Untuk pendaftaran dan ganti kata sandi. Panjangnya dihitung dalam byte UTF-8 karena batas bcrypt. */
    @StringRes
    fun newPassword(value: String): Int? = when {
        value.isEmpty() -> R.string.form_password_kosong
        value.length < PASSWORD_MIN -> R.string.form_password_terlalu_pendek
        value.toByteArray(Charsets.UTF_8).size > PASSWORD_MAX_BYTES -> R.string.form_password_terlalu_panjang
        else -> null
    }

    /** Login tidak menolak kata sandi pendek, server yang menjawab salah. Hanya yang kosong atau terlalu panjang. */
    @StringRes
    fun loginPassword(value: String): Int? = when {
        value.isEmpty() -> R.string.form_password_kosong
        value.toByteArray(Charsets.UTF_8).size > PASSWORD_MAX_BYTES -> R.string.form_password_terlalu_panjang
        else -> null
    }

    @StringRes
    fun fullName(value: String): Int? {
        val name = value.trim()
        return when {
            name.length < FULL_NAME_MIN -> R.string.form_nama_terlalu_pendek
            name.length > FULL_NAME_MAX -> R.string.form_nama_terlalu_panjang
            else -> null
        }
    }

    @StringRes
    fun nisn(value: String): Int? {
        val nisn = value.trim()
        return if (nisn.length in NISN_MIN..NISN_MAX && NISN.matches(nisn)) null else R.string.form_nisn_tidak_valid
    }

    @StringRes
    fun className(value: String): Int? {
        val name = value.trim()
        return when {
            name.isEmpty() -> R.string.form_nama_kelas_kosong
            name.length > CLASS_NAME_MAX -> R.string.form_nama_kelas_terlalu_panjang
            else -> null
        }
    }

    /** Boleh kosong, karena nomor telepon opsional dan teks kosong menghapusnya. */
    @StringRes
    fun phone(value: String): Int? {
        val phone = value.trim()
        return if (phone.length <= PHONE_MAX && PHONE.matches(phone)) null else R.string.form_telepon_tidak_valid
    }

    /** Boleh kosong. */
    @StringRes
    fun address(value: String): Int? =
        if (value.trim().length > ADDRESS_MAX) R.string.form_alamat_terlalu_panjang else null

    /** Catatan wali atau guru. Boleh kosong. */
    @StringRes
    fun notes(value: String): Int? =
        if (value.trim().length > NOTES_MAX) R.string.form_catatan_terlalu_panjang else null

    @StringRes
    fun forumTitle(value: String): Int? {
        val title = value.trim()
        return when {
            title.isEmpty() -> R.string.form_judul_kosong
            title.length > FORUM_TITLE_MAX -> R.string.form_judul_terlalu_panjang
            else -> null
        }
    }

    @StringRes
    fun forumBody(value: String): Int? {
        val body = value.trim()
        return when {
            body.isEmpty() -> R.string.form_isi_kosong
            body.length > FORUM_BODY_MAX -> R.string.form_isi_terlalu_panjang
            else -> null
        }
    }
}
