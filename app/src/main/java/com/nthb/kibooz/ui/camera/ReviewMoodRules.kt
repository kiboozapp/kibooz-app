package com.nthb.kibooz.ui.camera

import com.nthb.kibooz.data.remote.LogMoodBatchEntry
import com.nthb.kibooz.data.remote.MoodSource
import com.nthb.kibooz.ml.Emotion
import com.nthb.kibooz.model.ScannedStudentMood

/**
 * Label yang bisa dipilih guru di layar review, urut seperti chip-nya. KALEM tetap ada (keputusan produk) dan
 * dikirim sebagai SENANG. Setiap [Emotion] API harus punya label di sini supaya guru bisa memilihnya.
 */
val REVIEW_MOOD_LABELS = listOf("SENANG", "KALEM", "SEDIH", "BINGUNG", "MARAH")

/** `notes` di API paling banyak 500 karakter. */
const val MAX_NOTES_LENGTH = 500

/**
 * Label di layar review ke nilai `moodType` di API. "KALEM" tidak ada di API dan dipetakan ke SENANG
 * (keputusan produk), sedangkan nilai lain sama dengan nama [Emotion]. Null untuk label yang tidak dikenal.
 */
fun emotionFor(mood: String): Emotion? =
    if (mood == "KALEM") Emotion.SENANG else Emotion.entries.firstOrNull { it.name == mood }

/** Guru mengubah label jika emosi API-nya berbeda dari yang diterima dari kamera ("Senang" ke "Kalem" bukan perubahan). */
fun ScannedStudentMood.isChangedByTeacher(): Boolean =
    source == MoodSource.MANUAL_INPUT || emotionFor(currentMood) != emotionFor(detectedMood)

/** BR-04: hasil AI di bawah 75% yang belum dikonfirmasi dan belum diganti guru tidak boleh disimpan. */
fun ScannedStudentMood.needsConfirmation(): Boolean =
    !isChangedByTeacher() && !isConfirmed && confidenceScore < MIN_CONFIDENCE

/**
 * Satu entri batch. Label yang diganti guru dikirim sebagai MANUAL_INPUT tanpa skor (server mengisi 1), karena
 * skor model tidak berlaku untuk label pilihan guru. Catatan dipotong menjadi [MAX_NOTES_LENGTH] karakter.
 */
fun ScannedStudentMood.toEntry(): LogMoodBatchEntry {
    val emotion = checkNotNull(emotionFor(currentMood)) { "Label mood tidak dikenal: $currentMood" }
    val notes = teacherNotes.trim().take(MAX_NOTES_LENGTH).ifEmpty { null }

    return if (isChangedByTeacher()) {
        LogMoodBatchEntry(id, emotion, MoodSource.MANUAL_INPUT, notes = notes)
    } else {
        LogMoodBatchEntry(id, emotion, MoodSource.AI_CAMERA, confidenceScore, notes)
    }
}
