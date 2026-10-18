package com.nthb.kibooz.ui.camera

import com.nthb.kibooz.ml.EmotionResult

data class Student(val id: String, val name: String)

/** Satu wajah di frame terakhir. [emotion] kosong selama label belum diterima. */
data class FaceReading(val trackingId: Int, val emotion: EmotionResult?)

data class StudentMood(val student: Student, val emotion: EmotionResult)

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

        return kept.values.toList() + visible
    }
}
