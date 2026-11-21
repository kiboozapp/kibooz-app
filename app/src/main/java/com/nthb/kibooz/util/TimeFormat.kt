package com.nthb.kibooz.util

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object TimeFormat {

    // Detik pecahan ("...:30.123Z") dibuang dulu karena pola XXX tidak mengenalinya
    private val FRACTION = Regex("\\.\\d+")

    /** Jam dan menit dari waktu ISO 8601 server, dalam zona waktu pengguna. Null jika waktunya tidak terbaca. */
    fun localTime(iso: String, zone: TimeZone = TimeZone.getDefault()): String? = try {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
        val date = parser.parse(iso.replace(FRACTION, ""))
        SimpleDateFormat("HH:mm", Locale.US).apply { timeZone = zone }.format(date!!)
    } catch (e: java.text.ParseException) {
        null
    }
}
