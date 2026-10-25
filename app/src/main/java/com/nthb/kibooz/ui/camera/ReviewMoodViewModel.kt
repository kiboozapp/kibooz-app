package com.nthb.kibooz.ui.camera

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nthb.kibooz.R
import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.LogMoodBatchEntry
import com.nthb.kibooz.data.remote.errorMessageRes
import com.nthb.kibooz.data.remote.retryWhenRateLimited
import com.nthb.kibooz.data.repository.MoodRepository
import com.nthb.kibooz.data.repository.PendingMoodBatch
import com.nthb.kibooz.model.ScannedStudentMood
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ReviewSaveState(
    val saving: Boolean = false,
    /** Sisa detik sebelum simpan dicoba ulang otomatis setelah terkena batas laju (429), null jika tidak menunggu. */
    val retryInSeconds: Int? = null,
    /** Terisi setelah semua siswa tersimpan, berisi jumlahnya. */
    val savedCount: Int? = null,
    @StringRes val error: Int? = null,
    /** Id siswa yang sudah punya 20 catatan mood hari ini (MOOD_LOG_LIMIT_REACHED). Tidak ada yang tersimpan di batch itu. */
    val limitReachedFor: List<String> = emptyList()
)

/**
 * Menyimpan hasil review lewat [MoodRepository], dibagi per 30 entri. Terkena batas laju (429) menunggu sesuai
 * `Retry-After` lalu mengirim ulang otomatis dengan kunci yang sama. Setiap kelompok entri yang sama
 * memakai [PendingMoodBatch] yang sama, jadi tombol coba lagi memakai `Idempotency-Key` yang sama dan tidak
 * menyimpan dua kali. Kelompok yang sudah tersimpan tidak dikirim lagi, dan entri yang diubah guru setelah
 * gagal mendapat kunci baru.
 */
class ReviewMoodViewModel(
    private val repository: MoodRepository,
    private val pause: suspend (millis: Long) -> Unit = { delay(it) }
) : ViewModel() {

    private val _state = MutableStateFlow(ReviewSaveState())
    val state: StateFlow<ReviewSaveState> = _state.asStateFlow()

    private val pending = mutableMapOf<Pair<String, List<LogMoodBatchEntry>>, PendingMoodBatch>()
    private val saved = mutableSetOf<LogMoodBatchEntry>()

    fun save(classId: String?, rows: List<ScannedStudentMood>) {
        if (_state.value.saving) return

        val error = when {
            classId.isNullOrBlank() -> R.string.review_kelas_belum_dipilih
            rows.isEmpty() -> R.string.review_kosong
            rows.any { it.needsConfirmation() } -> R.string.review_perlu_konfirmasi
            else -> null
        }
        if (error != null || classId == null) {
            _state.value = ReviewSaveState(error = error)
            return
        }

        val entries = rows.map { it.toEntry() }
        _state.value = ReviewSaveState(saving = true)

        viewModelScope.launch {
            for (chunk in entries.filter { it !in saved }.chunked(MoodRepository.MAX_BATCH_ENTRIES)) {
                val batch = pending.getOrPut(classId to chunk) { PendingMoodBatch(classId, chunk) }

                val result = retryWhenRateLimited(
                    onWait = { left -> _state.value = ReviewSaveState(saving = true, retryInSeconds = left.takeIf { it > 0 }) },
                    pause = pause
                ) { repository.saveBatch(batch) }

                when (result) {
                    is ApiResult.Success -> {
                        saved += chunk
                        pending.remove(classId to chunk)
                    }
                    is ApiResult.Failure -> {
                        _state.value = ReviewSaveState(
                            error = result.errorMessageRes(),
                            limitReachedFor = result.limitReachedStudents(chunk)
                        )
                        return@launch
                    }
                    is ApiResult.NetworkError -> {
                        _state.value = ReviewSaveState(error = result.errorMessageRes())
                        return@launch
                    }
                }
            }
            _state.value = ReviewSaveState(savedCount = entries.size)
        }
    }

    /** Dipanggil setelah galat ditampilkan supaya tidak muncul lagi saat layar diputar. */
    fun errorShown() {
        _state.value = _state.value.copy(error = null, limitReachedFor = emptyList())
    }

    /** `details` bernama `entries[N].studentId`, N adalah urutan di [chunk]. */
    private fun ApiResult.Failure.limitReachedStudents(chunk: List<LogMoodBatchEntry>): List<String> {
        if (errorCode != "MOOD_LOG_LIMIT_REACHED") return emptyList()

        return details.orEmpty().keys.mapNotNull { key ->
            ENTRY_INDEX.find(key)?.groupValues?.get(1)?.toIntOrNull()?.let { chunk.getOrNull(it)?.studentId }
        }.distinct()
    }

    class Factory(private val repository: MoodRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ReviewMoodViewModel(repository) as T
    }

    private companion object {
        val ENTRY_INDEX = Regex("""^entries\[(\d+)]""")
    }
}
