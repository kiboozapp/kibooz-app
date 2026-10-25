package com.nthb.kibooz.ml

/**
 * Menerima kategori sampah hanya setelah muncul di [minFrames] frame berturut-turut dan bertahan [holdMillis],
 * supaya satu frame yang salah tebak tidak langsung mengunci hasil. Keyakinan hasil adalah rata-rata selama
 * kategori itu bertahan. Padanan [EmotionSmoother] untuk satu objek, tanpa pelacakan.
 *
 * Kategori yang sudah diterima tetap tampil sampai kategori lain bertahan cukup lama menggantikannya.
 * Tidak aman dipakai dari beberapa thread, panggil dari thread analyzer saja.
 */
class TrashSmoother(
    private val holdMillis: Long = EmotionSmoother.DEFAULT_HOLD_MILLIS,
    private val minFrames: Int = EmotionSmoother.DEFAULT_MIN_FRAMES
) {

    private var category: TrashCategory? = null
    private var since = 0L
    private var confidenceSum = 0f
    private var frames = 0
    private var accepted: TrashResult? = null

    /** Mengembalikan kategori yang sudah diterima, atau null jika belum ada yang bertahan. */
    fun update(result: TrashResult, nowMillis: Long): TrashResult? {
        if (category != result.category) {
            category = result.category
            since = nowMillis
            confidenceSum = result.confidence
            frames = 1
            return accepted
        }

        confidenceSum += result.confidence
        frames++

        if (frames >= minFrames && nowMillis - since >= holdMillis) {
            accepted = TrashResult(result.category, confidenceSum / frames)
        }

        return accepted
    }

    /** Dipanggil saat tidak ada objek di frame, supaya hasil lama tidak terbawa ke objek berikutnya. */
    fun reset() {
        category = null
        frames = 0
        confidenceSum = 0f
        accepted = null
    }
}
