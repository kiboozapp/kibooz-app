package com.nthb.kibooz.data.repository

import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.LeaderboardApi
import com.nthb.kibooz.data.remote.LeaderboardData
import com.nthb.kibooz.data.remote.LeaderboardPeriod
import com.nthb.kibooz.data.remote.safeApiCall
import com.squareup.moshi.Moshi

/** Papan peringkat kelas. Urutannya dihitung server, aplikasi hanya menampilkan. */
class LeaderboardRepository(private val api: LeaderboardApi, private val moshi: Moshi) {

    suspend fun leaderboard(
        classId: String? = null,
        period: LeaderboardPeriod = LeaderboardPeriod.SEMESTER
    ): ApiResult<LeaderboardData> = safeApiCall(moshi) { api.leaderboard(classId, period.query) }
}
