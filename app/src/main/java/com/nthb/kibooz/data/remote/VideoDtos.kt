package com.nthb.kibooz.data.remote

data class VideoItem(
    val id: String,
    val title: String,
    val description: String? = null,
    val videoUrl: String,
    val thumbnailUrl: String? = null,
    val durationSeconds: Int? = null,
    val createdAt: String
)

data class VideoList(
    val videos: List<VideoItem> = emptyList(),
    val page: Int = 1,
    val limit: Int = 20,
    val total: Int = 0
)

data class CreateVideoRequest(
    val title: String,
    val videoUrl: String,
    val description: String? = null,
    val thumbnailUrl: String? = null,
    val durationSeconds: Int? = null
)

/** Perubahan metadata video. Nilai null tidak mengubah field di server; teks kosong menghapus isi lama. URL video tetap. */
data class UpdateVideoRequest(
    val title: String? = null,
    val description: String? = null,
    val thumbnailUrl: String? = null,
    val durationSeconds: Int? = null
)
