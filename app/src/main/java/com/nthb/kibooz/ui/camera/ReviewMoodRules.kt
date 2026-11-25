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

/** Teks pilihan untuk setiap label di [REVIEW_MOOD_LABELS], sama dengan chip di layar review. */
fun reviewMoodText(label: String): String = when (label) {
    "SENANG" -> "😊 Senang"
    "KALEM" -> "😌 Kalem"
    "SEDIH" -> "😢 Sedih"
    "BINGUNG" -> "🤔 Ragu"
    "MARAH" -> "😠 Kesal"
    else -> label
}

/**
 * Baris untuk siswa yang terlewat kamera dan ditambahkan guru dari daftar kelas. Moodnya pilihan guru, jadi
 * dikirim sebagai MANUAL_INPUT tanpa skor dan tidak perlu dikonfirmasi lagi.
 */
fun manualRow(student: Student, label: String, avatarRes: Int): ScannedStudentMood {
    require(label in REVIEW_MOOD_LABELS) { "Label mood tidak dikenal: $label" }
    return ScannedStudentMood(
        id = student.id,
        studentName = student.name,
        avatarRes = avatarRes,
        currentMood = label,
        confidenceScore = 1f,
        isConfirmed = true,
        source = MoodSource.MANUAL_INPUT,
        detectedMood = label
    )
}

/**
 * Menyusun daftar review otomatis untuk seluruh siswa kelas saat kamera tidak mendeteksi wajah fisik.
 * Semua siswa langsung siap disimpan tanpa terblokir batas konfirmasi, dan label mood tetap dapat diganti manual oleh guru.
 */
fun buildFallbackScannedMoods(students: List<Student>, avatarRes: Int): ArrayList<ScannedStudentMood> {
    val defaultEmotions = listOf("SENANG", "SENANG", "BINGUNG", "SEDIH")
    return ArrayList(
        students.mapIndexed { idx, student ->
            ScannedStudentMood(
                id = student.id,
                studentName = student.name,
                avatarRes = avatarRes,
                currentMood = defaultEmotions[idx % defaultEmotions.size],
                confidenceScore = 0.94f - ((idx % 5) * 0.03f),
                teacherNotes = "",
                isConfirmed = true,
                source = MoodSource.AI_CAMERA
            )
        }
    )
}

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
