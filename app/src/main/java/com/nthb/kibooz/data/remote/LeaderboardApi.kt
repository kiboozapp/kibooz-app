package com.nthb.kibooz.data.remote

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface LeaderboardApi {
    /**
     * Wali melihat kelas anaknya, Guru kelas yang ia ajar. Tanpa [classId] server memilih kelas yang berlaku.
     * [period] bernilai `today`, `week` atau `month`, dan null untuk poin semester yang sedang berjalan.
     */
    @GET("leaderboard")
    suspend fun leaderboard(
        @Query("classId") classId: String?,
        @Query("period") period: String?
    ): Response<ApiEnvelope<LeaderboardData>>
}

/**
 * Rentang poin yang dihitung papan peringkat. Hari, minggu (mulai Senin) dan bulan dihitung server dalam zona
 * waktu Asia/Jakarta, dan semua anak di kelas tetap tampil dengan poin 0 jika belum memindai pada rentang itu.
 */
enum class LeaderboardPeriod(val query: String?) {
    SEMESTER(null),
    TODAY("today"),
    WEEK("week"),
    MONTH("month")
}

/**
 * `data` dari `GET /leaderboard`. Peringkat berdasarkan poin semester yang sedang berjalan, lalu nama,
 * dan mulai dari nol lagi di awal semester baru. [podium] berisi tiga teratas, [rankings] semua orang setelahnya.
 */
data class LeaderboardData(val podium: List<RankEntry>, val rankings: List<RankEntry>)

data class RankEntry(val rank: Int, val studentName: String, val points: Int, val avatarUrl: String?)
