package com.nthb.kibooz.data.remote

import com.nthb.kibooz.ml.Emotion

/** `data` dari `GET /guru/dashboard`. */
data class GuruDashboard(
    val teacher: TeacherInfo,
    val classOverview: ClassOverview,
    val dailyMoodDistribution: DailyMoodDistribution
)

data class TeacherInfo(val fullName: String, val nip: String?, val avatarUrl: String?)

/**
 * Ringkasan satu kelas. Di dasbor Guru `className` berformat "Class A (Bunga)", berbeda dari nama biasa di
 * endpoint lain. [dominantMood] null selama kelas belum punya catatan mood hari ini.
 */
data class ClassOverview(
    val classId: String,
    val className: String,
    val joinCode: String,
    val joinOpen: Boolean,
    val totalStudents: Int,
    val presentStudents: Int,
    val dominantMood: Emotion?
)

/** Jumlah siswa per mood hari ini. Kunci JSON memakai huruf kecil. */
data class DailyMoodDistribution(val senang: Int, val sedih: Int, val marah: Int, val bingung: Int) {
    val total: Int get() = senang + sedih + marah + bingung
}

/** `data` dari `GET /guru/mood/analytics`. */
data class MoodAnalytics(
    val donutSummary: List<DonutSlice>,
    val weeklyTrend: List<WeeklyPoint>,
    /** Hanya terisi untuk `range=monthly`. */
    val monthlyDistribution: List<MonthlyCount>? = null
)

data class DonutSlice(val mood: Emotion, val percentage: Int, val colorHex: String)

/** [day] berupa nama hari bahasa Inggris, `Monday` sampai `Friday`. [averageHappyScore] 0 sampai 100. */
data class WeeklyPoint(val day: String, val averageHappyScore: Int)

data class MonthlyCount(val mood: Emotion, val count: Int)

/** `data` dari `GET /guru/profile`. */
data class GuruProfile(
    val id: String,
    val fullName: String,
    val nip: String?,
    val schoolName: String,
    val avatarUrl: String?
)

/** `data` dari `GET /guru/profile/detail` dan `PUT /guru/profile`. */
data class GuruProfileDetail(
    val id: String,
    val fullName: String,
    val nip: String?,
    val schoolName: String,
    val avatarUrl: String?,
    val email: String,
    val phoneNumber: String?,
    val address: String?,
    val joinedAt: String
)

/** Isi `PUT /guru/profile`. Field null tidak dikirim dan mempertahankan nilai lama di server. */
data class UpdateGuruProfileRequest(
    val fullName: String? = null,
    val phoneNumber: String? = null,
    val address: String? = null
)
