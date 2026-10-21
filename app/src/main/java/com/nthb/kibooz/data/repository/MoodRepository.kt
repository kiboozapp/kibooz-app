package com.nthb.kibooz.data.repository

import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.LogMoodBatchEntry
import com.nthb.kibooz.data.remote.LogMoodBatchItem
import com.nthb.kibooz.data.remote.LogMoodBatchRequest
import com.nthb.kibooz.data.remote.MoodApi
import com.nthb.kibooz.data.remote.MoodSource
import com.nthb.kibooz.data.remote.newIdempotencyKey
import com.nthb.kibooz.data.remote.safeApiCall
import com.squareup.moshi.Moshi

/**
 * Satu aksi simpan mood massal. [idempotencyKey] dibuat sekali di sini, jadi mengirim objek yang sama
 * lagi (tombol coba lagi) tidak menyimpan dua kali. Jika guru mengubah isinya, buat objek baru supaya
 * kuncinya baru: kunci yang sama dengan isi berbeda ditolak `IDEMPOTENCY_KEY_REUSED`.
 */
class PendingMoodBatch(
    val classId: String,
    val entries: List<LogMoodBatchEntry>,
    val idempotencyKey: String = newIdempotencyKey()
) {
    init {
        require(entries.size in 1..MoodRepository.MAX_BATCH_ENTRIES) {
            "entries harus berisi 1 sampai ${MoodRepository.MAX_BATCH_ENTRIES} item, bukan ${entries.size}"
        }
        require(entries.all { it.source != MoodSource.AI_CAMERA || it.confidenceScore != null }) {
            "confidenceScore wajib untuk entri AI_CAMERA"
        }
    }
}

class MoodRepository(private val api: MoodApi, private val moshi: Moshi) {

    /** Hasil sesuai urutan [PendingMoodBatch.entries]. Satu entri gagal berarti tidak ada yang tersimpan. */
    suspend fun saveBatch(batch: PendingMoodBatch): ApiResult<List<LogMoodBatchItem>> {
        val request = LogMoodBatchRequest(batch.classId, batch.entries)

        return when (val result = safeApiCall(moshi) { api.logMoodBatch(batch.idempotencyKey, request) }) {
            is ApiResult.Success -> ApiResult.Success(result.data.logs, result.message)
            is ApiResult.Failure -> result
            is ApiResult.NetworkError -> result
        }
    }

    companion object {
        /** Batas `entries` di `POST /guru/mood/log/batch`. */
        const val MAX_BATCH_ENTRIES = 30
    }
}
