package com.nthb.kibooz.data.remote

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface LeaderboardApi {
    /** Wali melihat kelas anaknya, Guru kelas yang ia ajar. Tanpa [classId] server memilih kelas yang berlaku. */
    @GET("leaderboard")
    suspend fun leaderboard(@Query("classId") classId: String?): Response<ApiEnvelope<LeaderboardData>>
}

/**
 * `data` dari `GET /leaderboard`. Peringkat berdasarkan poin semester yang sedang berjalan, lalu nama,
 * dan mulai dari nol lagi di awal semester baru. [podium] berisi tiga teratas, [rankings] semua orang setelahnya.
 */
data class LeaderboardData(val podium: List<RankEntry>, val rankings: List<RankEntry>)

data class RankEntry(val rank: Int, val studentName: String, val points: Int, val avatarUrl: String?)
