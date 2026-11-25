package com.nthb.kibooz.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object TimeFormat {

    // Detik pecahan ("...:30.123Z") dibuang dulu karena pola XXX tidak mengenalinya
    private val FRACTION = Regex("\\.\\d+")
    private val LOCALE_ID = Locale("id", "ID")

    private fun parseIso(iso: String): Date? = try {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
        parser.parse(iso.replace(FRACTION, ""))
    } catch (_: Exception) {
        null
    }

    /** Jam dan menit dari waktu ISO 8601 server, dalam zona waktu pengguna. Null jika waktunya tidak terbaca. */
    fun localTime(iso: String, zone: TimeZone = TimeZone.getDefault()): String? {
        val date = parseIso(iso) ?: return null
        return SimpleDateFormat("HH:mm", Locale.US).apply { timeZone = zone }.format(date)
    }

    /** Tanggal Indonesia dari waktu ISO 8601 server, misal "25 November 2026". Null jika tidak terbaca. */
    fun localDate(iso: String, zone: TimeZone = TimeZone.getDefault()): String? {
        val date = parseIso(iso) ?: return null
        return SimpleDateFormat("d MMMM yyyy", LOCALE_ID).apply { timeZone = zone }.format(date)
    }

    /** Tanggal dan jam lokal singkat, misal "25 Nov 2026, 07:05". Null jika tidak terbaca. */
    fun localDateTime(iso: String, zone: TimeZone = TimeZone.getDefault()): String? {
        val date = parseIso(iso) ?: return null
        return SimpleDateFormat("d MMM yyyy, HH:mm", LOCALE_ID).apply { timeZone = zone }.format(date)
    }

    /** Tanggal ringkas untuk header beranda Guru dan Wali, misal "Rab, 25 Nov 2026". */
    fun compactHomeDate(date: Date = Date(), zone: TimeZone = TimeZone.getDefault()): String =
        SimpleDateFormat("EEE, d MMM yyyy", LOCALE_ID).apply { timeZone = zone }.format(date)
}
