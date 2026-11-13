package com.nthb.kibooz.data.remote

import com.nthb.kibooz.ml.Emotion

/** `data` dari `GET /guru/dashboard`: profil Guru, ringkasan kelas hari ini dan sebaran mood hari ini. */
data class GuruDashboard(
    val teacher: DashboardTeacher,
    val classOverview: ClassOverview,
    val dailyMoodDistribution: DailyMoodDistribution
)

data class DashboardTeacher(
    val fullName: String,
    val nip: String?,
    val avatarUrl: String?
)

data class ClassOverview(
    val classId: String,
    val className: String,
    val joinCode: String,
    /** False selama Guru kelas menutup kelas untuk anak baru. */
    val joinOpen: Boolean,
    val totalStudents: Int,
    val presentStudents: Int,
    /** Null selama belum ada catatan emosi hari ini. */
    val dominantMood: Emotion?
)

/** Jumlah anak untuk setiap emosi dari catatan terakhir mereka hari ini. */
data class DailyMoodDistribution(
    val senang: Int,
    val sedih: Int,
    val marah: Int,
    val bingung: Int
)
