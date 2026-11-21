package com.nthb.kibooz.ui.common

import com.nthb.kibooz.data.remote.DailyMoodDistribution
import com.nthb.kibooz.ml.Emotion
import kotlin.math.atan2
import kotlin.math.roundToInt

/** Warna emosi di semua grafik. Sedih biru karena PRD FR-3.1 memakai Senang, Sedih, Marah dan Bingung. */
object MoodColors {
    const val SENANG = 0xFFFFC107.toInt()
    const val SEDIH = 0xFF2196F3.toInt()
    const val MARAH = 0xFFE53935.toInt()
    const val BINGUNG = 0xFFFF9800.toInt()
    const val EMPTY = 0xFFE5E7EB.toInt()

    fun of(emotion: Emotion): Int = when (emotion) {
        Emotion.SENANG -> SENANG
        Emotion.SEDIH -> SEDIH
        Emotion.MARAH -> MARAH
        Emotion.BINGUNG -> BINGUNG
    }
}

/** Hitungan murni di balik grafik mood, dipisahkan dari kode gambar supaya bisa diuji di JVM. */
object MoodChartMath {

    /** Urutan irisan donat dan batang, searah jarum jam dari atas. */
    val ORDER = listOf(Emotion.SENANG, Emotion.BINGUNG, Emotion.SEDIH, Emotion.MARAH)

    /** Persentase tiap emosi dari jumlah catatan. Semuanya 0 jika belum ada catatan. */
    fun percentages(distribution: DailyMoodDistribution): Map<Emotion, Float> {
        val total = distribution.total
        val counts = mapOf(
            Emotion.SENANG to distribution.senang,
            Emotion.SEDIH to distribution.sedih,
            Emotion.MARAH to distribution.marah,
            Emotion.BINGUNG to distribution.bingung
        )
        return ORDER.associateWith { if (total == 0) 0f else counts.getValue(it) * 100f / total }
    }

    /**
     * Emosi yang irisannya berada di sudut [angleFromTop] (derajat searah jarum jam dari atas, 0 sampai 360),
     * atau null jika sudut itu di luar semua irisan.
     */
    fun sliceAt(angleFromTop: Float, percentages: Map<Emotion, Float>): Emotion? {
        var start = 0f
        for (emotion in ORDER) {
            val sweep = (percentages[emotion] ?: 0f) * 3.6f
            if (sweep > 0f && angleFromTop >= start && angleFromTop < start + sweep) return emotion
            start += sweep
        }
        return null
    }

    /** Sudut sentuhan terhadap pusat donat, derajat searah jarum jam dari atas. Sumbu y layar mengarah ke bawah. */
    fun angleFromTop(dx: Float, dy: Float): Float {
        val degrees = Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble())).toFloat()
        return if (degrees < 0f) degrees + 360f else degrees
    }

    /** Singkatan hari dari nama Inggris di API. Nama yang tidak dikenal dikembalikan apa adanya. */
    fun dayLabel(day: String): String = when (day) {
        "Monday" -> "Sen"
        "Tuesday" -> "Sel"
        "Wednesday" -> "Rab"
        "Thursday" -> "Kam"
        "Friday" -> "Jum"
        else -> day
    }

    /** Tinggi batang 0 sampai 1 terhadap batang tertinggi. Semua batang 0 jika tidak ada catatan. */
    fun barFraction(count: Int, max: Int): Float = if (max <= 0) 0f else count.coerceIn(0, max).toFloat() / max

    fun percentText(value: Float): String = "${value.roundToInt()}%"
}
