package com.nthb.kibooz.util

import com.nthb.kibooz.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormRulesTest {

    @Test
    fun `an email needs an at sign and a dot and at most 255 characters`() {
        assertNull(FormRules.email(" ani@example.com "))
        assertEquals(R.string.form_email_kosong, FormRules.email("  "))
        assertEquals(R.string.form_email_tidak_valid, FormRules.email("ani@example"))
        assertEquals(R.string.form_email_tidak_valid, FormRules.email("ani example@x.com"))
        assertEquals(R.string.form_email_terlalu_panjang, FormRules.email("a".repeat(250) + "@x.com"))
    }

    @Test
    fun `a new password is 8 to 72 bytes`() {
        assertNull(FormRules.newPassword("12345678"))
        assertNull(FormRules.newPassword("a".repeat(72)))
        assertEquals(R.string.form_password_kosong, FormRules.newPassword(""))
        assertEquals(R.string.form_password_terlalu_pendek, FormRules.newPassword("1234567"))
        assertEquals(R.string.form_password_terlalu_panjang, FormRules.newPassword("a".repeat(73)))
    }

    @Test
    fun `the password limit counts bytes and not characters`() {
        // 37 karakter, 74 byte
        assertEquals(R.string.form_password_terlalu_panjang, FormRules.newPassword("é".repeat(37)))
        assertNull(FormRules.newPassword("é".repeat(36)))
    }

    @Test
    fun `a login password is only checked for empty and too long`() {
        assertNull(FormRules.loginPassword("abc"))
        assertEquals(R.string.form_password_kosong, FormRules.loginPassword(""))
        assertEquals(R.string.form_password_terlalu_panjang, FormRules.loginPassword("a".repeat(73)))
    }

    @Test
    fun `a full name is 2 to 150 characters after trimming`() {
        assertNull(FormRules.fullName("Ani"))
        assertEquals(R.string.form_nama_terlalu_pendek, FormRules.fullName(" A "))
        assertNull(FormRules.fullName("a".repeat(150)))
        assertEquals(R.string.form_nama_terlalu_panjang, FormRules.fullName("a".repeat(151)))
    }

    @Test
    fun `a NISN is 5 to 30 digits`() {
        assertNull(FormRules.nisn("12345"))
        assertNull(FormRules.nisn("1".repeat(30)))
        assertEquals(R.string.form_nisn_tidak_valid, FormRules.nisn("1234"))
        assertEquals(R.string.form_nisn_tidak_valid, FormRules.nisn("1".repeat(31)))
        assertEquals(R.string.form_nisn_tidak_valid, FormRules.nisn("12345a"))
    }

    @Test
    fun `a class name is 1 to 50 characters`() {
        assertNull(FormRules.className("Kelas A"))
        assertEquals(R.string.form_nama_kelas_kosong, FormRules.className(" "))
        assertEquals(R.string.form_nama_kelas_terlalu_panjang, FormRules.className("a".repeat(51)))
    }

    @Test
    fun `a phone number is optional, digits with plus, spaces, dashes and brackets, up to 30 characters`() {
        assertNull(FormRules.phone(""))
        assertNull(FormRules.phone("+62 812-3456 (78)"))
        assertEquals(R.string.form_telepon_tidak_valid, FormRules.phone("0812abc"))
        assertEquals(R.string.form_telepon_tidak_valid, FormRules.phone("1".repeat(31)))
    }

    @Test
    fun `an address is optional and at most 1000 characters`() {
        assertNull(FormRules.address(""))
        assertNull(FormRules.address("a".repeat(1000)))
        assertEquals(R.string.form_alamat_terlalu_panjang, FormRules.address("a".repeat(1001)))
    }

    @Test
    fun `a forum title is 1 to 150 and a body is 1 to 5000 characters`() {
        assertNull(FormRules.forumTitle("Halo"))
        assertEquals(R.string.form_judul_kosong, FormRules.forumTitle(" "))
        assertEquals(R.string.form_judul_terlalu_panjang, FormRules.forumTitle("a".repeat(151)))
        assertNull(FormRules.forumBody("a".repeat(5000)))
        assertEquals(R.string.form_isi_kosong, FormRules.forumBody(""))
        assertEquals(R.string.form_isi_terlalu_panjang, FormRules.forumBody("a".repeat(5001)))
    }
}
