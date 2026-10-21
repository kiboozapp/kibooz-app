package com.nthb.kibooz.ui.camera

import com.nthb.kibooz.data.repository.MoodRepository
import com.nthb.kibooz.ml.Emotion
import com.nthb.kibooz.ml.EmotionResult

data class Student(val id: String, val name: String)

/** Satu wajah di frame terakhir. [emotion] kosong selama label belum diterima. */
data class FaceReading(val trackingId: Int, val emotion: EmotionResult?)

/** Nilai `source` di API. */
enum class MoodSource { AI_CAMERA, MANUAL_INPUT }

/** BR-04, hasil AI di bawah 75% harus dikonfirmasi guru sebelum disimpan. */
const val MIN_CONFIDENCE = 0.75f

/**
 * Hasil emosi satu siswa. [source] adalah AI_CAMERA dengan keyakinan sebenarnya dari model, atau MANUAL_INPUT
 * jika guru mengganti labelnya (keyakinan 1 di sana bukan hasil model dan tidak boleh dikirim sebagai skor AI).
 * [confirmed] berarti guru menerima label AI apa adanya.
 */
data class StudentMood(
    val student: Student,
    val emotion: EmotionResult,
    val source: MoodSource = MoodSource.AI_CAMERA,
    val confirmed: Boolean = false
) {
    /** Hasil AI di bawah [MIN_CONFIDENCE] yang belum dikonfirmasi atau diganti guru. */
    val needsConfirmation: Boolean
        get() = source == MoodSource.AI_CAMERA && !confirmed && emotion.confidence < MIN_CONFIDENCE
}

/**
 * Mencatat siswa mana yang ada di wajah mana selama satu sesi pemindaian, dan hasil emosi tiap siswa.
 *
 * Satu siswa hanya boleh dipasang ke satu wajah yang masih terlihat. Saat wajah yang sudah dipasang hilang
 * dari frame, hasil terakhirnya tetap tersimpan untuk siswa itu, dan siswa itu bisa dipasang ke wajah lain.
 * Hanya dipakai dari thread utama.
 */
class MoodScanSession {

    private val assigned = mutableMapOf<Int, Student>()
    private val latest = mutableMapOf<Int, EmotionResult>()
    private val kept = mutableMapOf<String, StudentMood>()

    // Keputusan guru per siswa. Konfirmasi hanya berlaku untuk label yang dikonfirmasi, jika AI kemudian
    // menerima label lain maka hasil baru itu harus dikonfirmasi lagi. Pergantian label oleh guru tetap berlaku
    private val confirmed = mutableMapOf<String, Emotion>()
    private val manual = mutableMapOf<String, Emotion>()

    fun onFaces(faces: List<FaceReading>) {
        val visible = faces.mapTo(mutableSetOf()) { it.trackingId }

        for (trackingId in assigned.keys.filter { it !in visible }) {
            val student = assigned.remove(trackingId) ?: continue
            latest[trackingId]?.let { kept[student.id] = StudentMood(student, it) }
        }
        latest.keys.retainAll(visible)

        for (face in faces) {
            face.emotion?.let { latest[face.trackingId] = it }
        }
    }

    /** Mengembalikan false jika siswa itu sudah dipasang ke wajah lain yang masih terlihat. */
    fun assign(trackingId: Int, student: Student): Boolean {
        if (assigned.any { (id, other) -> other.id == student.id && id != trackingId }) return false

        assigned[trackingId] = student
        kept.remove(student.id)

        return true
    }

    fun studentAt(trackingId: Int): Student? = assigned[trackingId]

    fun isAssigned(studentId: String): Boolean = assigned.values.any { it.id == studentId }

    /** Hasil tiap siswa yang punya emosi diterima: dari wajah yang masih terlihat dan yang sudah keluar frame. */
    fun results(): List<StudentMood> {
        val visible = assigned.mapNotNull { (trackingId, student) ->
            latest[trackingId]?.let { StudentMood(student, it) }
        }

        return (kept.values.toList() + visible).map(::withDecision)
    }

    /** Hasil AI di bawah 75% yang masih menunggu guru menerima atau mengganti labelnya (BR-04). */
    fun pendingConfirmation(): List<StudentMood> = results().filter { it.needsConfirmation }

    /** Guru menerima label AI [studentId] apa adanya. Hanya berlaku untuk label yang sedang tampil. */
    fun confirm(studentId: String) {
        val current = results().firstOrNull { it.student.id == studentId } ?: return
        if (current.source == MoodSource.AI_CAMERA) confirmed[studentId] = current.emotion.emotion
    }

    /** Guru mengganti label [studentId]. Hasilnya menjadi MANUAL_INPUT dan tidak berubah lagi oleh kamera. */
    fun override(studentId: String, emotion: Emotion) {
        manual[studentId] = emotion
        confirmed.remove(studentId)
    }

    private fun withDecision(mood: StudentMood): StudentMood {
        manual[mood.student.id]?.let {
            return StudentMood(mood.student, EmotionResult(it, MANUAL_CONFIDENCE), MoodSource.MANUAL_INPUT, confirmed = true)
        }
        return if (confirmed[mood.student.id] == mood.emotion.emotion) mood.copy(confirmed = true) else mood
    }

    /**
     * Hasil yang boleh disimpan, dibagi per [MAX_BATCH_ENTRIES] karena POST /guru/mood/log/batch menolak lebih
     * dari 30 entri. Hasil yang masih perlu konfirmasi tidak ikut.
     */
    fun resultBatches(): List<List<StudentMood>> =
        results().filterNot { it.needsConfirmation }.chunked(MAX_BATCH_ENTRIES)

    companion object {
        const val MAX_BATCH_ENTRIES = MoodRepository.MAX_BATCH_ENTRIES
        private const val MANUAL_CONFIDENCE = 1f
    }
}
