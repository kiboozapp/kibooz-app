package com.nthb.kibooz.ui.camera

import com.nthb.kibooz.data.remote.MoodSource
import com.nthb.kibooz.ml.Emotion
import com.nthb.kibooz.model.ScannedStudentMood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewMoodRulesTest {

    private fun row(
        mood: String = "SENANG",
        confidence: Float = 0.94f,
        confirmed: Boolean = false,
        source: MoodSource = MoodSource.AI_CAMERA,
        detected: String = mood,
        notes: String = ""
    ) = ScannedStudentMood(
        id = "student-1",
        studentName = "Ani",
        avatarRes = 0,
        currentMood = mood,
        confidenceScore = confidence,
        teacherNotes = notes,
        isConfirmed = confirmed,
        source = source,
        detectedMood = detected
    )

    @Test
    fun `Kalem is sent as SENANG and the other labels keep their API names`() {
        assertEquals(Emotion.SENANG, emotionFor("KALEM"))
        assertEquals(Emotion.SENANG, emotionFor("SENANG"))
        assertEquals(Emotion.SEDIH, emotionFor("SEDIH"))
        assertEquals(Emotion.MARAH, emotionFor("MARAH"))
        assertEquals(Emotion.BINGUNG, emotionFor("BINGUNG"))
        assertNull(emotionFor("HAPPY"))
    }

    @Test
    fun `an untouched camera result is sent as AI_CAMERA with the model score`() {
        val entry = row(mood = "BINGUNG", confidence = 0.82f, notes = "  Ragu  ").toEntry()

        assertEquals("student-1", entry.studentId)
        assertEquals(Emotion.BINGUNG, entry.moodType)
        assertEquals(MoodSource.AI_CAMERA, entry.source)
        assertEquals(0.82f, entry.confidenceScore)
        assertEquals("Ragu", entry.notes)
    }

    @Test
    fun `a label the teacher changed is sent as MANUAL_INPUT without the model score`() {
        val entry = row(mood = "MARAH", detected = "SENANG", confidence = 0.9f).toEntry()

        assertEquals(Emotion.MARAH, entry.moodType)
        assertEquals(MoodSource.MANUAL_INPUT, entry.source)
        assertNull(entry.confidenceScore)
    }

    @Test
    fun `switching Senang to Kalem is not a change because both are SENANG`() {
        val row = row(mood = "KALEM", detected = "SENANG")

        assertFalse(row.isChangedByTeacher())
        assertEquals(MoodSource.AI_CAMERA, row.toEntry().source)
        assertEquals(Emotion.SENANG, row.toEntry().moodType)
    }

    @Test
    fun `a label the teacher already chose on the camera screen stays MANUAL_INPUT`() {
        val entry = row(source = MoodSource.MANUAL_INPUT, confidence = 1f).toEntry()

        assertEquals(MoodSource.MANUAL_INPUT, entry.source)
        assertNull(entry.confidenceScore)
    }

    @Test
    fun `blank notes are not sent and long notes are cut to 500 characters`() {
        assertNull(row(notes = "   ").toEntry().notes)
        assertEquals(500, row(notes = "x".repeat(800)).toEntry().notes?.length)
    }

    @Test
    fun `an unknown label is a programming error and is not sent`() {
        try {
            row(mood = "HAPPY").toEntry()
            org.junit.Assert.fail("expected the unknown label to be refused")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message!!.contains("HAPPY"))
        }
    }

    @Test
    fun `BR-04 asks for confirmation below 75 percent until it is confirmed or changed`() {
        assertTrue(row(confidence = 0.71f).needsConfirmation())
        assertFalse(row(confidence = 0.71f, confirmed = true).needsConfirmation())
        assertFalse(row(confidence = 0.71f, mood = "MARAH", detected = "BINGUNG").needsConfirmation())
        assertFalse(row(confidence = 0.71f, source = MoodSource.MANUAL_INPUT).needsConfirmation())
    }

    @Test
    fun `75 percent and above needs no confirmation`() {
        assertFalse(row(confidence = 0.75f).needsConfirmation())
        assertFalse(row(confidence = 0.94f).needsConfirmation())
    }

    @Test
    fun `every review label maps to an API mood and every API mood can be chosen`() {
        assertTrue(REVIEW_MOOD_LABELS.all { emotionFor(it) != null })
        assertEquals(Emotion.entries.toSet(), REVIEW_MOOD_LABELS.mapNotNull(::emotionFor).toSet())
        assertTrue("SEDIH" in REVIEW_MOOD_LABELS)
    }

    @Test
    fun `a teacher who picks Sedih over the camera label sends it as a manual entry`() {
        val entry = row(mood = "SEDIH", detected = "SENANG").toEntry()

        assertEquals(Emotion.SEDIH, entry.moodType)
        assertEquals(MoodSource.MANUAL_INPUT, entry.source)
    }

    @Test
    fun `a student added by the teacher is a confirmed manual entry without a score`() {
        val added = manualRow(Student("s9", "Budi"), "SEDIH", avatarRes = 0)

        assertFalse(added.needsConfirmation())
        val entry = added.toEntry()
        assertEquals("s9", entry.studentId)
        assertEquals(Emotion.SEDIH, entry.moodType)
        assertEquals(MoodSource.MANUAL_INPUT, entry.source)
        assertNull(entry.confidenceScore)
    }

    @Test
    fun `a Kalem pick for an added student is still sent as SENANG`() {
        assertEquals(Emotion.SENANG, manualRow(Student("s9", "Budi"), "KALEM", 0).toEntry().moodType)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `an unknown label cannot become a row`() {
        manualRow(Student("s9", "Budi"), "HAPPY", 0)
    }

    @Test
    fun `every review label has its own choice text`() {
        val texts = REVIEW_MOOD_LABELS.map(::reviewMoodText)
        assertEquals(REVIEW_MOOD_LABELS.size, texts.toSet().size)
        assertTrue(texts.none { it in REVIEW_MOOD_LABELS })
    }
}
