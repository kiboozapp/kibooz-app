package com.nthb.kibooz.ui.common

import com.nthb.kibooz.ml.TrashCategory
import kotlin.math.floor

/** Hitungan murni di balik diagram lingkaran sampah, dipisahkan dari kode gambar supaya bisa diuji di JVM. */
object TrashChartMath {

    /** Urutan irisan searah jarum jam dari atas, sama dengan urutan legenda. */
    val ORDER = listOf(TrashCategory.ORGANIK, TrashCategory.ANORGANIK, TrashCategory.B3)

    /**
     * Persentase bulat tiap kategori dari jumlah scan, dengan total selalu tepat 100 (semuanya 0 jika belum ada scan).
     *
     * Memakai metode sisa terbesar: setiap bagian dibulatkan ke bawah, lalu kekurangannya dibagikan satu per satu ke bagian
     * dengan pecahan terbesar. Membulatkan tiap bagian sendiri-sendiri bisa meleset, misalnya 1/1/1 menjadi 33+33+33 = 99.
     * Jika pecahannya sama besar, kategori yang lebih awal di [ORDER] didahulukan. Kategori yang tidak ada dihitung 0.
     */
    fun percentages(counts: Map<TrashCategory, Int>): Map<TrashCategory, Int> {
        val values = ORDER.associateWith { (counts[it] ?: 0).coerceAtLeast(0) }
        val total = values.values.sum()
        if (total == 0) return ORDER.associateWith { 0 }

        val exact = values.mapValues { it.value * 100.0 / total }
        val result = exact.mapValues { floor(it.value).toInt() }.toMutableMap()
        val missing = 100 - result.values.sum()

        // sortedByDescending stabil, jadi pecahan yang sama tetap mengikuti ORDER
        exact.entries
            .sortedByDescending { it.value - floor(it.value) }
            .take(missing)
            .forEach { result[it.key] = result.getValue(it.key) + 1 }

        return ORDER.associateWith { result.getValue(it) }
    }
}
