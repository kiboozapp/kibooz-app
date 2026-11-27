package com.nthb.kibooz.util

import com.nthb.kibooz.data.remote.ChatContact
import com.nthb.kibooz.data.remote.ClassTeacher
import com.nthb.kibooz.data.session.Role
import com.nthb.kibooz.data.session.SessionUser

/** Ringkasan pengajar suatu kelas untuk tampilan `DetailKelas` dan `PesertaKelas`. */
data class ClassTeacherSummary(
    val fullName: String,
    val avatarUrl: String? = null,
    val isOwner: Boolean = false
)

/**
 * Helper murni untuk menyaring, mengurutkan, dan memformat daftar pengajar kelas secara konsisten
 * di seluruh portal Guru dan Wali tanpa ketergantungan Android atau teks nama palsu.
 */
object ClassTeachers {

    const val DEFAULT_TEACHER_LABEL = "Guru Kelas"
    const val DEFAULT_ROLE_LABEL = "Guru Pengajar"

    fun fromClassTeachers(teachers: List<ClassTeacher>): List<ClassTeacherSummary> {
        val seen = LinkedHashSet<String>()
        return teachers
            .asSequence()
            .filter { it.fullName.isNotBlank() }
            .sortedWith(compareByDescending<ClassTeacher> { it.isOwner }.thenByDescending { it.isSelf })
            .map {
                ClassTeacherSummary(
                    fullName = it.fullName.trim(),
                    avatarUrl = it.avatarUrl?.trim()?.takeIf(String::isNotEmpty),
                    isOwner = it.isOwner
                )
            }
            .filter { seen.add(it.fullName.lowercase()) }
            .toList()
    }

    fun fromChatContacts(contacts: List<ChatContact>, classId: String?): List<ClassTeacherSummary> {
        val targetClassId = classId?.trim()?.takeIf { it.isNotEmpty() }
        val seen = LinkedHashSet<String>()
        return contacts
            .asSequence()
            .filter { !it.deleted && it.role.equals("GURU", ignoreCase = true) && it.fullName.isNotBlank() }
            .filter { contact -> targetClassId == null || contact.classes.any { it.id == targetClassId } }
            .map {
                ClassTeacherSummary(
                    fullName = it.fullName.trim(),
                    avatarUrl = it.avatarUrl?.trim()?.takeIf(String::isNotEmpty)
                )
            }
            .filter { seen.add(it.fullName.lowercase()) }
            .toList()
    }

    fun mergeWithSessionFallback(
        teachers: List<ClassTeacherSummary>,
        sessionUser: SessionUser?
    ): List<ClassTeacherSummary> {
        if (teachers.isNotEmpty()) return teachers
        if (sessionUser?.role == Role.GURU && sessionUser.fullName.isNotBlank()) {
            return listOf(
                ClassTeacherSummary(
                    fullName = sessionUser.fullName.trim(),
                    avatarUrl = sessionUser.avatarUrl?.trim()?.takeIf(String::isNotEmpty),
                    isOwner = true
                )
            )
        }
        return emptyList()
    }

    fun formatHeadline(teachers: List<ClassTeacherSummary>): String =
        formatHeadlineOrNull(teachers) ?: DEFAULT_TEACHER_LABEL

    fun formatHeadlineOrNull(teachers: List<ClassTeacherSummary>): String? = when (teachers.size) {
        0 -> null
        1 -> teachers[0].fullName
        2 -> "${teachers[0].fullName}, ${teachers[1].fullName}"
        else -> "${teachers[0].fullName}, +${teachers.size - 1} pengajar"
    }

    fun formatPengajarLine(teachers: List<ClassTeacherSummary>): String =
        "Pengajar: ${formatHeadline(teachers)}"

    fun formatSubtitle(teachers: List<ClassTeacherSummary>, className: String?): String {
        val rolePart = if (teachers.size > 1) {
            "${teachers.size} $DEFAULT_ROLE_LABEL"
        } else {
            DEFAULT_ROLE_LABEL
        }
        val cleanClass = className?.trim()?.takeIf { it.isNotBlank() }?.let(ClassNames::withPrefix)
        return cleanClass?.let { "$rolePart • $it" } ?: rolePart
    }

    fun formatBannerCount(teacherCount: Int, studentCountText: String): String =
        "${maxOf(1, teacherCount)} $DEFAULT_ROLE_LABEL & $studentCountText"
}
