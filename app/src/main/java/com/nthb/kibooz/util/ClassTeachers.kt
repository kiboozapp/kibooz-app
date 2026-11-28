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
 * Helper murni untuk menyaring dan mengurutkan daftar pengajar kelas secara konsisten
 * di seluruh portal Guru dan Wali tanpa ketergantungan Android atau teks nama palsu.
 */
object ClassTeachers {
    fun fromClassTeachers(teachers: List<ClassTeacher>): List<ClassTeacherSummary> {
        val seen = LinkedHashSet<String>()
        return teachers
            .asSequence()
            .filter { it.fullName.isNotBlank() }
            .sortedWith(compareByDescending<ClassTeacher> { it.isOwner }.thenByDescending { it.isSelf })
            .filter { teacher ->
                val identity = teacher.id.trim()
                    .ifEmpty { teacher.userId.trim() }
                    .ifEmpty { teacher.fullName.trim().lowercase() }
                seen.add(identity)
            }
            .map {
                ClassTeacherSummary(
                    fullName = it.fullName.trim(),
                    avatarUrl = it.avatarUrl?.trim()?.takeIf(String::isNotEmpty),
                    isOwner = it.isOwner
                )
            }
            .toList()
    }

    fun fromChatContacts(contacts: List<ChatContact>, classId: String?): List<ClassTeacherSummary> {
        // Tanpa kelas tujuan tidak ada pengajar yang bisa dipilih, supaya pengajar kelas lain tidak ikut tampil
        val targetClassId = classId?.trim()?.takeIf { it.isNotEmpty() } ?: return emptyList()
        val seen = LinkedHashSet<String>()
        return contacts
            .asSequence()
            .filter { !it.deleted && it.role.equals("GURU", ignoreCase = true) && it.fullName.isNotBlank() }
            .filter { contact -> contact.classes.any { it.id == targetClassId } }
            .filter { contact ->
                val identity = contact.id.trim().ifEmpty { contact.fullName.trim().lowercase() }
                seen.add(identity)
            }
            .map {
                ClassTeacherSummary(
                    fullName = it.fullName.trim(),
                    avatarUrl = it.avatarUrl?.trim()?.takeIf(String::isNotEmpty)
                )
            }
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
}
