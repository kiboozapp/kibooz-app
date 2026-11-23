package com.nthb.kibooz.ui.guru

import kotlin.math.roundToInt

/** Hitungan murni untuk angka dasbor Guru, dipisahkan dari layar supaya bisa diuji di JVM. */
object DashboardMath {

    /**
     * Persentase siswa hadir, dibulatkan dan dijaga 0 sampai 100. Null untuk kelas tanpa siswa karena tidak ada
     * yang dibagi.
     */
    fun attendancePercent(present: Int, total: Int): Int? {
        if (total <= 0) return null
        return (present.coerceIn(0, total) * 100f / total).roundToInt()
    }
}
