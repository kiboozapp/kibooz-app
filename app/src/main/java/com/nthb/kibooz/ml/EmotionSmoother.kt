package com.nthb.kibooz.ml

/**
 * Menerima label emosi hanya setelah muncul di [minFrames] frame berturut-turut dan bertahan [holdMillis]
 * untuk wajah yang sama, supaya gambar blur atau ekspresi sekilas tidak langsung mengunci hasil.
 * Keyakinan hasil adalah rata-rata selama label itu bertahan.
 *
 * Label yang sudah diterima tetap tampil sampai label lain bertahan cukup lama menggantikannya.
 * Tidak aman dipakai dari beberapa thread, panggil dari thread analyzer saja.
 */
class EmotionSmoother(
    private val holdMillis: Long = DEFAULT_HOLD_MILLIS,
    private val minFrames: Int = DEFAULT_MIN_FRAMES
) {

    private class Track(
        var emotion: Emotion,
        var since: Long,
        var confidenceSum: Float,
        var frames: Int,
        var accepted: EmotionResult?
    )

    private val tracks = mutableMapOf<Int, Track>()

    /** Mengembalikan label yang sudah diterima untuk wajah ini, atau null jika belum ada yang bertahan. */
    fun update(trackingId: Int, result: EmotionResult, nowMillis: Long): EmotionResult? {
        val track = tracks[trackingId]

        if (track == null || track.emotion != result.emotion) {
            tracks[trackingId] = Track(result.emotion, nowMillis, result.confidence, 1, track?.accepted)
            return track?.accepted
        }

        track.confidenceSum += result.confidence
        track.frames++

        if (track.frames >= minFrames && nowMillis - track.since >= holdMillis) {
            track.accepted = EmotionResult(track.emotion, track.confidenceSum / track.frames)
        }

        return track.accepted
    }

    /** Membuang wajah yang sudah tidak ada di frame, supaya statusnya tidak menumpuk. */
    fun retain(activeTrackingIds: Set<Int>) {
        tracks.keys.retainAll(activeTrackingIds)
    }

    companion object {
        const val DEFAULT_HOLD_MILLIS = 300L

        /** Penyaringan kestabilan 3 frame berturut-turut dari PRD (04_AI_ML_AND_CAMERA_PIPELINE). */
        const val DEFAULT_MIN_FRAMES = 3
    }
}
