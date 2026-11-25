package com.nthb.kibooz.data.repository

import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.CreateVideoRequest
import com.nthb.kibooz.data.remote.UpdateVideoRequest
import com.nthb.kibooz.data.remote.VideoApi
import com.nthb.kibooz.data.remote.VideoItem
import com.nthb.kibooz.data.remote.VideoList
import com.nthb.kibooz.data.remote.newIdempotencyKey
import com.nthb.kibooz.data.remote.safeApiCall
import com.nthb.kibooz.data.remote.safeApiCallEmpty
import com.squareup.moshi.Moshi

class VideoRepository(private val api: VideoApi, private val moshi: Moshi) {

    suspend fun videos(classId: String, page: Int = 1, limit: Int = 20): ApiResult<VideoList> =
        safeApiCall(moshi) { api.listVideos(classId, page, limit) }

    suspend fun createVideo(
        classId: String,
        title: String,
        videoUrl: String,
        description: String? = null,
        thumbnailUrl: String? = null,
        durationSeconds: Int? = null
    ): ApiResult<VideoItem> =
        safeApiCall(moshi) {
            val key = newIdempotencyKey()
            api.createVideo(classId, key, CreateVideoRequest(title, videoUrl, description, thumbnailUrl, durationSeconds))
        }

    suspend fun updateVideo(
        classId: String,
        videoId: String,
        title: String? = null,
        description: String? = null,
        thumbnailUrl: String? = null,
        durationSeconds: Int? = null
    ): ApiResult<VideoItem> =
        safeApiCall(moshi) {
            api.updateVideo(classId, videoId, UpdateVideoRequest(title, description, thumbnailUrl, durationSeconds))
        }

    suspend fun deleteVideo(classId: String, videoId: String): ApiResult<Unit> =
        safeApiCallEmpty(moshi) { api.deleteVideo(classId, videoId) }
}
