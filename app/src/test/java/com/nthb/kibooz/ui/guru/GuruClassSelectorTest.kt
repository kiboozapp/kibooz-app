package com.nthb.kibooz.ui.guru

import com.nthb.kibooz.data.remote.ClassSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GuruClassSelectorTest {

    private fun summary(id: String, name: String, archivedAt: String? = null) = ClassSummary(
        id = id,
        name = name,
        gradeLevel = "TK-A",
        schoolName = "TK Kibooz",
        academicYear = "2025/2026",
        joinCode = "KODE$id",
        joinOpen = true,
        totalStudents = 15,
        createdAt = "2026-01-01T00:00:00Z",
        archivedAt = archivedAt
    )

    @Test
    fun `selection persists for the same user and resets when account changes`() {
        val selection = GuruClassSelection()
        selection.select("guru-1", "class-sakura")

        assertEquals("class-sakura", selection.current("guru-1"))
        assertNull(selection.current("guru-2"))
        assertNull(selection.current("guru-1"))
    }

    @Test
    fun `visibleTeacherClasses hides archived classes when active classes exist`() {
        val c1 = summary("c1", "Matahari")
        val c2 = summary("c2", "Sakura", archivedAt = "2026-02-01T00:00:00Z")

        assertEquals(listOf(c1), visibleTeacherClasses(listOf(c1, c2)))
    }

    @Test
    fun `visibleTeacherClasses falls back to archived list when all classes are archived`() {
        val c1 = summary("c1", "Arsip 1", archivedAt = "2026-02-01T00:00:00Z")

        assertEquals(listOf(c1), visibleTeacherClasses(listOf(c1)))
    }

    @Test
    fun `resolveSelectedClass matches selected id or falls back cleanly`() {
        val c1 = summary("c1", "Matahari")
        val c2 = summary("c2", "Sakura")

        assertEquals(c2, resolveSelectedClass(listOf(c1, c2), "c2"))
        assertEquals(c2, resolveSelectedClass(listOf(c1, c2), null, fallbackClassId = "c2"))
        assertEquals(c1, resolveSelectedClass(listOf(c1, c2), "missing"))
        assertNull(resolveSelectedClass(emptyList(), "c1"))
    }
}
