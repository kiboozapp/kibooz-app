package com.nthb.kibooz.data.remote

import com.nthb.kibooz.ml.Emotion

/** Asal sebuah catatan mood. Nama konstanta sama dengan nilai `source` di API. */
enum class MoodSource { AI_CAMERA, MANUAL_INPUT }

/**
 * Satu baris di `entries`. `confidenceScore` wajib untuk [MoodSource.AI_CAMERA], dan untuk
 * [MoodSource.MANUAL_INPUT] boleh null (server mengisi 1). Nilai null tidak dikirim.
 */
data class LogMoodBatchEntry(
    val studentId: String,
    val moodType: Emotion,
    val source: MoodSource,
    val confidenceScore: Float? = null,
    val notes: String? = null
)

data class LogMoodBatchRequest(
    val classId: String,
    val entries: List<LogMoodBatchEntry>,
    val recordedAt: String? = null
)

data class LogMoodBatchItem(val logId: String, val studentId: String, val recordedAt: String)

data class LogMoodBatchData(val logs: List<LogMoodBatchItem>)
