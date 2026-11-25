package com.nthb.kibooz.ui.wali

import com.nthb.kibooz.data.remote.RankEntry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PodiumCelebrationTest {

    private val podium = listOf(
        RankEntry(1, "Adit Pratama", 120, null),
        RankEntry(2, "Bella Saphira", 110, null),
        RankEntry(3, "Cahya Putra", 95, null)
    )

    @Test
    fun `a child on the podium is found by name`() {
        assertTrue(podium.hasStudent("Adit Pratama"))
        assertTrue(podium.hasStudent("Cahya Putra"))
    }

    @Test
    fun `a child outside the podium is not found`() {
        assertFalse(podium.hasStudent("Dimas Anggara"))
    }

    @Test
    fun `no highlight name means no celebration, as for a teacher`() {
        assertFalse(podium.hasStudent(null))
    }

    @Test
    fun `an empty podium has nobody`() {
        assertFalse(emptyList<RankEntry>().hasStudent("Adit Pratama"))
    }
}
