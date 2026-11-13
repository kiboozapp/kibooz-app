package com.nthb.kibooz.ui.guru

import androidx.annotation.StringRes
import com.nthb.kibooz.R
import com.nthb.kibooz.data.remote.GuruDashboard
import com.nthb.kibooz.ml.Emotion
import kotlin.math.roundToInt

/** Angka dasbor Guru yang siap ditampilkan, diolah dari balasan `GET /guru/dashboard`. */
data class DashboardSummary(
    val className: String,
    val totalStudents: Int,
    val presentStudents: Int,
    /** Persentase siswa hadir, dibulatkan. Null untuk kelas tanpa siswa karena tidak ada yang dibagi. */
    val attendancePercent: Int?,
    /** Emosi yang paling banyak muncul hari ini, null selama belum ada catatan mood. */
    val dominantMood: Emotion?,
    val moodCounts: Map<Emotion, Int>,
    /** Jumlah siswa yang moodnya sudah tercatat hari ini. */
    val recordedMoods: Int
) {
    /** Bagian [emotion] dari semua mood yang tercatat, 0 sampai 100. Nol selama belum ada catatan. */
    fun moodPercent(emotion: Emotion): Float =
        if (recordedMoods == 0) 0f else (moodCounts[emotion] ?: 0) * 100f / recordedMoods
}

fun GuruDashboard.toSummary(): DashboardSummary {
    val overview = classOverview
    val counts = mapOf(
        Emotion.SENANG to dailyMoodDistribution.senang,
        Emotion.SEDIH to dailyMoodDistribution.sedih,
        Emotion.MARAH to dailyMoodDistribution.marah,
        Emotion.BINGUNG to dailyMoodDistribution.bingung
    )
    val attendance = if (overview.totalStudents > 0) {
        val present = overview.presentStudents.coerceIn(0, overview.totalStudents)
        (present * 100f / overview.totalStudents).roundToInt()
    } else {
        null
    }

    return DashboardSummary(
        className = overview.className,
        totalStudents = overview.totalStudents,
        presentStudents = overview.presentStudents,
        attendancePercent = attendance,
        dominantMood = overview.dominantMood,
        moodCounts = counts,
        recordedMoods = counts.values.sum()
    )
}

/** Sapaan menurut jam di perangkat: pagi mulai 04.00, siang 11.00, sore 15.00 dan malam 18.00. */
@StringRes
fun greetingFor(hour: Int): Int = when (hour) {
    in 4..10 -> R.string.sapaan_pagi
    in 11..14 -> R.string.sapaan_siang
    in 15..17 -> R.string.sapaan_sore
    else -> R.string.sapaan_malam
}
