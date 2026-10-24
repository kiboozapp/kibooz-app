package com.nthb.kibooz.ui.camera

import com.nthb.kibooz.data.remote.MoodSource
import com.nthb.kibooz.ml.Emotion
import com.nthb.kibooz.ml.EmotionResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoodScanSessionTest {

    private var now = 0L
    private val session = MoodScanSession(graceMillis = 1_000, nowMillis = { now })
    private val amelia = Student("a", "Amelia")
    private val farhan = Student("f", "Farhan")

    private fun happy(confidence: Float = 0.9f) = EmotionResult(Emotion.SENANG, confidence)

    private fun sad(confidence: Float = 0.8f) = EmotionResult(Emotion.SEDIH, confidence)

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

    // BR-04

    private fun assignWithResult(result: EmotionResult, student: Student = amelia, trackingId: Int = 1) {
        session.assign(trackingId, student)
        session.onFaces(listOf(FaceReading(trackingId, result)))
    }

    @Test
    fun `a result at or above 75 percent needs no confirmation`() {
        assignWithResult(happy(0.75f))

        assertFalse(session.results().single().needsConfirmation)
        assertTrue(session.pendingConfirmation().isEmpty())
    }

    @Test
    fun `a result under 75 percent waits for the teacher and is not saved yet`() {
        assignWithResult(happy(0.74f))

        val pending = session.pendingConfirmation().single()
        assertEquals(amelia, pending.student)
        assertEquals(MoodSource.AI_CAMERA, pending.source)
        assertTrue(session.resultBatches().isEmpty())
    }

    @Test
    fun `confirming keeps the AI source and the real confidence and lets it be saved`() {
        assignWithResult(happy(0.6f))

        session.confirm("a")

        val mood = session.results().single()
        assertEquals(MoodSource.AI_CAMERA, mood.source)
        assertEquals(0.6f, mood.emotion.confidence, 0.0001f)
        assertTrue(mood.confirmed)
        assertEquals(1, session.resultBatches().single().size)
    }

    @Test
    fun `a confirmation does not cover a different label that the camera accepts later`() {
        assignWithResult(happy(0.6f))
        session.confirm("a")

        session.onFaces(listOf(FaceReading(1, sad(0.6f))))

        assertEquals(Emotion.SEDIH, session.pendingConfirmation().single().emotion.emotion)
    }

    @Test
    fun `changing the label makes a manual result that the camera no longer changes`() {
        assignWithResult(happy(0.6f))

        session.override("a", Emotion.MARAH)
        session.onFaces(listOf(FaceReading(1, sad(0.9f))))

        val mood = session.results().single()
        assertEquals(Emotion.MARAH, mood.emotion.emotion)
        assertEquals(MoodSource.MANUAL_INPUT, mood.source)
        assertFalse(mood.needsConfirmation)
        assertEquals(1, session.resultBatches().single().size)
    }

    @Test
    fun `a manual result for a student whose face left stays`() {
        assignWithResult(happy(0.6f))
        session.override("a", Emotion.BINGUNG)

        session.onFaces(emptyList())

        assertEquals(MoodSource.MANUAL_INPUT, session.results().single().source)
    }

    @Test
    fun `confirming a student without a result does nothing`() {
        session.confirm("a")

        assertTrue(session.results().isEmpty())
    }

    @Test
    fun `only the confirmed results of a mixed session are batched`() {
        assignWithResult(happy(0.9f), amelia, 1)
        assignWithResult(happy(0.5f), farhan, 2)

        assertEquals(listOf("a"), session.resultBatches().single().map { it.student.id })
        assertEquals(listOf("f"), session.pendingConfirmation().map { it.student.id })
    }

    // Masa tenggang dan menautkan ulang wajah

    private fun box(left: Int, top: Int = 0, size: Int = 100) = FaceBox(left, top, left + size, top + size)

    @Test
    fun `a face missing for one frame keeps its student and its result`() {
        session.assign(1, amelia)
        session.onFaces(listOf(FaceReading(1, happy(0.9f), box(0))))

        now = 100
        session.onFaces(emptyList())
        now = 200
        session.onFaces(listOf(FaceReading(1, null, box(0))))

        assertEquals(amelia, session.studentAt(1))
        assertEquals(Emotion.SENANG, session.results().single().emotion.emotion)
    }

    @Test
    fun `a face that stays away longer than the grace period is released with its result kept`() {
        session.assign(1, amelia)
        session.onFaces(listOf(FaceReading(1, happy(0.9f), box(0))))

        now = 100
        session.onFaces(emptyList())
        now = 1_101
        session.onFaces(emptyList())

        assertNull(session.studentAt(1))
        assertFalse(session.isAssigned("a"))
        assertEquals(amelia, session.results().single().student)
    }

    @Test
    fun `a new tracking id on the same spot takes over the student`() {
        session.assign(1, amelia)
        session.onFaces(listOf(FaceReading(1, happy(0.9f), box(0))))

        now = 100
        session.onFaces(listOf(FaceReading(5, null, box(10))))

        assertEquals(amelia, session.studentAt(5))
        assertNull(session.studentAt(1))
        assertEquals(Emotion.SENANG, session.results().single().emotion.emotion)
        assertTrue(session.isAssigned("a"))
    }

    @Test
    fun `a face somewhere else is not linked to the missing student`() {
        session.assign(1, amelia)
        session.onFaces(listOf(FaceReading(1, happy(0.9f), box(0))))

        now = 100
        session.onFaces(listOf(FaceReading(5, null, box(500))))

        assertNull(session.studentAt(5))
        assertEquals(amelia, session.studentAt(1))
    }

    @Test
    fun `a face without a box is never linked by position`() {
        session.assign(1, amelia)
        session.onFaces(listOf(FaceReading(1, happy(0.9f), box(0))))

        now = 100
        session.onFaces(listOf(FaceReading(5, null)))

        assertNull(session.studentAt(5))
    }

    @Test
    fun `a new face is not linked after the grace period`() {
        session.assign(1, amelia)
        session.onFaces(listOf(FaceReading(1, happy(0.9f), box(0))))

        now = 100
        session.onFaces(emptyList())
        now = 1_200
        session.onFaces(listOf(FaceReading(5, null, box(0))))

        assertNull(session.studentAt(5))
    }

    @Test
    fun `two missing students go to the faces that overlap them most`() {
        session.assign(1, amelia)
        session.assign(2, farhan)
        session.onFaces(listOf(FaceReading(1, happy(), box(0)), FaceReading(2, sad(), box(300))))

        now = 100
        session.onFaces(listOf(FaceReading(8, null, box(305)), FaceReading(9, null, box(5))))

        assertEquals(farhan, session.studentAt(8))
        assertEquals(amelia, session.studentAt(9))
    }

    @Test
    fun `a missing student can be given to another face right away`() {
        session.assign(1, amelia)
        session.onFaces(listOf(FaceReading(1, happy(), box(0))))
        now = 100
        session.onFaces(emptyList())

        assertTrue(session.assign(7, amelia))

        assertNull(session.studentAt(1))
        assertEquals(amelia, session.studentAt(7))
    }

    @Test
    fun `the overlap of boxes is zero apart and one when equal`() {
        assertEquals(0f, box(0).overlap(box(200)), 0.0001f)
        assertEquals(1f, box(0).overlap(box(0)), 0.0001f)
        assertEquals(1f / 3f, box(0).overlap(box(50, size = 100).copy(top = 0, bottom = 100)), 0.0001f)
    }
}
