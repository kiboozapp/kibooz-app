package com.nthb.kibooz.ui.camera

import com.nthb.kibooz.ml.Emotion
import com.nthb.kibooz.ml.EmotionResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoodScanSessionTest {

    private val session = MoodScanSession()
    private val amelia = Student("a", "Amelia")
    private val farhan = Student("f", "Farhan")

    private fun happy(confidence: Float = 0.9f) = EmotionResult(Emotion.SENANG, confidence)

    private fun sad() = EmotionResult(Emotion.SEDIH, 0.8f)

    @Test
    fun `a student can be assigned to a face`() {
        assertTrue(session.assign(1, amelia))

        assertEquals(amelia, session.studentAt(1))
        assertTrue(session.isAssigned("a"))
        assertNull(session.studentAt(2))
    }

    @Test
    fun `a student cannot be assigned to two visible faces`() {
        session.assign(1, amelia)

        assertFalse(session.assign(2, amelia))

        assertNull(session.studentAt(2))
        assertEquals(amelia, session.studentAt(1))
    }

    @Test
    fun `choosing another student for a face frees the first one`() {
        session.assign(1, amelia)

        assertTrue(session.assign(1, farhan))

        assertEquals(farhan, session.studentAt(1))
        assertFalse(session.isAssigned("a"))
        assertTrue(session.assign(2, amelia))
    }

    @Test
    fun `choosing the same student again for the same face is allowed`() {
        session.assign(1, amelia)

        assertTrue(session.assign(1, amelia))
    }

    @Test
    fun `results hold the accepted emotion of each assigned face`() {
        session.assign(1, amelia)
        session.assign(2, farhan)
        session.onFaces(listOf(FaceReading(1, happy()), FaceReading(2, sad())))

        val results = session.results().associate { it.student.id to it.emotion.emotion }

        assertEquals(mapOf("a" to Emotion.SENANG, "f" to Emotion.SEDIH), results)
    }

    @Test
    fun `a face without an accepted label has no result yet`() {
        session.assign(1, amelia)
        session.onFaces(listOf(FaceReading(1, null)))

        assertTrue(session.results().isEmpty())
    }

    @Test
    fun `a face without a student has no result`() {
        session.onFaces(listOf(FaceReading(1, happy())))

        assertTrue(session.results().isEmpty())
    }

    @Test
    fun `the result of a student stays when the face leaves the frame`() {
        session.assign(1, amelia)
        session.onFaces(listOf(FaceReading(1, happy(0.95f))))

        session.onFaces(emptyList())

        val results = session.results()
        assertEquals(1, results.size)
        assertEquals(amelia, results[0].student)
        assertEquals(0.95f, results[0].emotion.confidence, 0.0001f)
        assertFalse("the face is gone, so the student is free to take another", session.isAssigned("a"))
    }

    @Test
    fun `a student whose face left can be assigned to a new face`() {
        session.assign(1, amelia)
        session.onFaces(listOf(FaceReading(1, happy())))
        session.onFaces(emptyList())

        assertTrue(session.assign(7, amelia))
        session.onFaces(listOf(FaceReading(7, sad())))

        val results = session.results()
        assertEquals(1, results.size)
        assertEquals(Emotion.SEDIH, results[0].emotion.emotion)
    }

    @Test
    fun `an assigned face that never got a label leaves no result behind`() {
        session.assign(1, amelia)
        session.onFaces(listOf(FaceReading(1, null)))

        session.onFaces(emptyList())

        assertTrue(session.results().isEmpty())
    }

    @Test
    fun `a label keeps its last value while the next frames bring none`() {
        session.assign(1, amelia)
        session.onFaces(listOf(FaceReading(1, happy())))
        session.onFaces(listOf(FaceReading(1, null)))

        assertEquals(Emotion.SENANG, session.results().single().emotion.emotion)
    }

    @Test
    fun `results are split into batches the server accepts`() {
        val faces = (1..65).map { FaceReading(it, happy()) }
        session.onFaces(faces)
        faces.forEach { session.assign(it.trackingId, Student("s${it.trackingId}", "Siswa ${it.trackingId}")) }

        assertEquals(listOf(30, 30, 5), session.resultBatches().map { it.size })
    }

    @Test
    fun `no results give no batches`() {
        assertTrue(session.resultBatches().isEmpty())
    }
}
