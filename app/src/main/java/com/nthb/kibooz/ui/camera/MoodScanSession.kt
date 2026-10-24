package com.nthb.kibooz.ui.camera

import com.nthb.kibooz.data.remote.MoodSource
import com.nthb.kibooz.data.repository.MoodRepository
import com.nthb.kibooz.ml.Emotion
import com.nthb.kibooz.ml.EmotionResult

data class Student(val id: String, val name: String)

/** Kotak wajah dalam piksel frame. Dipakai untuk mengenali wajah yang sama setelah ID pelacakannya berganti. */
data class FaceBox(val left: Int, val top: Int, val right: Int, val bottom: Int) {

    private val area: Long get() = maxOf(0, right - left).toLong() * maxOf(0, bottom - top)

    /** Rasio luas irisan terhadap luas gabungan, 0 jika tidak bersinggungan sampai 1 jika sama persis. */
    fun overlap(other: FaceBox): Float {
        val width = minOf(right, other.right) - maxOf(left, other.left)
        val height = minOf(bottom, other.bottom) - maxOf(top, other.top)
        if (width <= 0 || height <= 0) return 0f

        val intersection = width.toLong() * height
        val union = area + other.area - intersection

        return if (union <= 0) 0f else intersection.toFloat() / union
    }
}

/** Satu wajah di frame terakhir. [emotion] kosong selama label belum diterima, [box] kosong jika tidak diketahui. */
data class FaceReading(val trackingId: Int, val emotion: EmotionResult?, val box: FaceBox? = null)

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
 * Satu siswa hanya boleh dipasang ke satu wajah yang masih terlihat. Wajah yang hilang dari frame tidak langsung
 * dilepas. Pasangan siswanya bertahan selama [graceMillis], dan jika wajah dengan ID pelacakan baru muncul di
 * tempat yang sama (kotaknya tumpang tindih) pasangan itu dipindahkan ke ID baru. Itu menjaga satu frame yang
 * terlewat atau ID yang berganti dari menghapus pilihan guru. Setelah [graceMillis] hasil terakhir wajah itu
 * tetap tersimpan untuk siswanya, dan siswa itu bisa dipasang ke wajah lain.
 * Hanya dipakai dari thread utama.
 */
class MoodScanSession(
    private val graceMillis: Long = DEFAULT_GRACE_MILLIS,
    private val nowMillis: () -> Long = { System.nanoTime() / NANOS_PER_MILLI }
) {

    private val assigned = mutableMapOf<Int, Student>()
    private val latest = mutableMapOf<Int, EmotionResult>()
    private val kept = mutableMapOf<String, StudentMood>()

    // Wajah berpasangan yang sedang tidak terlihat: kapan mulai hilang, dan kotak terakhirnya
    private val missingSince = mutableMapOf<Int, Long>()
    private val lastBox = mutableMapOf<Int, FaceBox>()

    // Keputusan guru per siswa. Konfirmasi hanya berlaku untuk label yang dikonfirmasi, jika AI kemudian
    // menerima label lain maka hasil baru itu harus dikonfirmasi lagi. Pergantian label oleh guru tetap berlaku
    private val confirmed = mutableMapOf<String, Emotion>()
    private val manual = mutableMapOf<String, Emotion>()

    fun onFaces(faces: List<FaceReading>) {
        val now = nowMillis()
        val visible = faces.mapTo(mutableSetOf()) { it.trackingId }

        for (trackingId in assigned.keys.filter { it !in visible }) {
            val since = missingSince.getOrPut(trackingId) { now }
            if (now - since > graceMillis) release(trackingId)
        }

        relink(faces.filter { it.trackingId !in assigned && it.box != null }, now)

        for (face in faces) {
            if (face.trackingId in assigned) {
                missingSince.remove(face.trackingId)
                face.box?.let { lastBox[face.trackingId] = it }
            }
        }
        latest.keys.retainAll { it in visible || it in assigned }

        for (face in faces) {
            face.emotion?.let { latest[face.trackingId] = it }
        }
    }

    /** Wajah berpasangan hilang cukup lama. Hasil terakhirnya disimpan untuk siswanya. */
    private fun release(trackingId: Int) {
        val student = assigned.remove(trackingId) ?: return
        latest.remove(trackingId)?.let { kept[student.id] = StudentMood(student, it) }
        missingSince.remove(trackingId)
        lastBox.remove(trackingId)
    }

    /** Memindahkan pasangan wajah yang hilang ke wajah baru yang kotaknya paling tumpang tindih. */
    private fun relink(newFaces: List<FaceReading>, now: Long) {
        val candidates = newFaces.flatMap { face ->
            missingSince.keys
                .filter { oldId -> assigned.containsKey(oldId) && now - missingSince.getValue(oldId) <= graceMillis }
                .mapNotNull { oldId ->
                    val overlap = lastBox[oldId]?.let { face.box?.overlap(it) } ?: 0f
                    if (overlap >= MIN_RELINK_OVERLAP) Triple(overlap, face.trackingId, oldId) else null
                }
        }.sortedByDescending { it.first }

        val usedNew = mutableSetOf<Int>()
        val usedOld = mutableSetOf<Int>()
        for ((_, newId, oldId) in candidates) {
            if (newId in usedNew || oldId in usedOld) continue
            usedNew += newId
            usedOld += oldId

            assigned[newId] = assigned.remove(oldId) ?: continue
            latest.remove(oldId)?.let { latest[newId] = it }
            missingSince.remove(oldId)
            lastBox.remove(oldId)?.let { lastBox[newId] = it }
        }
    }

    /**
     * Mengembalikan false jika siswa itu sudah dipasang ke wajah lain yang masih terlihat. Wajah yang sedang
     * hilang tidak menghalangi, pilihan guru yang baru menggantikan pasangan lamanya.
     */
    fun assign(trackingId: Int, student: Student): Boolean {
        if (isAssignedElsewhere(student.id, except = trackingId)) return false

        for (oldId in assigned.filter { (id, other) -> other.id == student.id && id != trackingId }.keys) {
            assigned.remove(oldId)
            latest.remove(oldId)
            missingSince.remove(oldId)
            lastBox.remove(oldId)
        }

        assigned[trackingId] = student
        missingSince.remove(trackingId)
        kept.remove(student.id)

        return true
    }

    private fun isAssignedElsewhere(studentId: String, except: Int?) =
        assigned.any { (id, other) -> other.id == studentId && id != except && id !in missingSince }

    fun studentAt(trackingId: Int): Student? = assigned[trackingId]

    /** Hanya menghitung wajah yang masih terlihat, bukan yang sedang hilang sementara. */
    fun isAssigned(studentId: String): Boolean = isAssignedElsewhere(studentId, except = null)

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

        /** Berapa lama pasangan wajah yang hilang dipertahankan sebelum dilepas. */
        const val DEFAULT_GRACE_MILLIS = 1_500L

        /** Minimal tumpang tindih kotak untuk menganggap wajah baru sebagai wajah yang sama. */
        const val MIN_RELINK_OVERLAP = 0.3f

        private const val NANOS_PER_MILLI = 1_000_000L
        private const val MANUAL_CONFIDENCE = 1f
    }
}
