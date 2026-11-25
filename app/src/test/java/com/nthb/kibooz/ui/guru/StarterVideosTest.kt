package com.nthb.kibooz.ui.guru

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StarterVideosTest {

    @Test
    fun `every thematic subject returns three valid https starter videos`() {
        val subjects = listOf(
            "Bahasa & Komunikasi",
            "Seni & Kreativitas",
            "Berpikir & Logika",
            "Belajar Fisik Motorik",
            "Nilai Agama & Moral",
            "Sosial & Emotional"
        )

        subjects.forEach { subject ->
            val items = StarterVideos.forSubject(subject)
            assertEquals("Expected 3 starter videos for $subject", 3, items.size)
            items.forEach { video ->
                assertTrue("Title must not be blank for $subject", video.title.isNotBlank())
                assertTrue("Description must not be blank for $subject", video.description.isNotBlank())
                assertTrue(
                    "URL must use https:// for $subject",
                    video.videoUrl.startsWith("https://")
                )
            }
        }
    }
}
