package com.nthb.kibooz.data.remote

import com.nthb.kibooz.ml.Emotion

/** `data` dari `GET /wali/dashboard`. */
data class WaliDashboard(
    val student: DashboardStudent,
    /** Mood terbaru hari ini, null sampai guru mencatatnya. */
    val todayMood: TodayMood?,
    val pointsSummary: PointsSummary,
    /** Panduan yang cocok dengan mood hari ini, null selama belum ada mood. */
    val recommendedGuidance: RecommendedGuidance?
)

/** Di dasbor Wali `className` berformat "Grade • School", berbeda dari nama biasa di endpoint lain. */
data class DashboardStudent(
    val id: String,
    val fullName: String,
    val classId: String,
    val className: String,
    val avatarUrl: String?,
    val nisn: String
)

data class TodayMood(
    val moodType: Emotion,
    val label: String,
    val confidence: Double,
    /** Waktu ISO 8601 dari server. */
    val recordedAt: String,
    val teacherNotes: String?
)

/** `anorganicCount` memang dieja begitu di API. B3 tidak dihitung di sini. */
data class PointsSummary(
    val totalPoints: Int,
    val seasonPoints: Int,
    val classRank: Int,
    val organicCount: Int,
    val anorganicCount: Int
) {
    val sortedCount: Int get() = organicCount + anorganicCount
}

/** [id] dikirim sebagai `guidanceId` saat panduan diterapkan. [bannerUrl] kosong jika penyimpanan belum punya alamat publik. */
data class RecommendedGuidance(val id: String, val title: String, val category: String, val bannerUrl: String)
