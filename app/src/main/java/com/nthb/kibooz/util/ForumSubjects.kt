package com.nthb.kibooz.util

import com.nthb.kibooz.data.remote.ForumThreadItem

/** Kode dan label mata pelajaran kurikulum Kibooz untuk segmentasi Forum Diskusi per kelas dan mapel. */
enum class ForumSubject(
    val code: String,
    val displayName: String,
    val chipLabel: String
) {
    SENI("SENI-A1", "Seni & Kreativitas", "Seni"),
    BAHASA("BHS-A1", "Bahasa & Komunikasi", "Bahasa"),
    LOGIKA("LOG-A1", "Berpikir & Logika", "Logika"),
    MOTORIK("MOT-A1", "Belajar Fisik Motorik", "Motorik"),
    AGAMA("AGM-A1", "Nilai Agama & Moral", "Agama"),
    SOSIAL("SOS-A1", "Sosial & Emotional", "Sosial")
}

object ForumSubjects {
    const val EXTRA_MAPEL_CODE = "MAPEL_CODE"
    const val EXTRA_MAPEL_NAME = "MAPEL_NAME"

    private const val DEFAULT_HEADER_TITLE = "Forum Diskusi Kelas"
    private const val EMPTY_SUFFIX = ".\nMulai percakapan bersama guru dan wali murid!"

    private val keywordRules: List<Pair<ForumSubject, List<String>>> = listOf(
        ForumSubject.BAHASA to listOf("bahasa", "komunikasi"),
        ForumSubject.LOGIKA to listOf("logika", "berpikir"),
        ForumSubject.MOTORIK to listOf("motorik", "fisik"),
        ForumSubject.AGAMA to listOf("agama", "moral"),
        ForumSubject.SOSIAL to listOf("sosial", "emotional", "emosional"),
        ForumSubject.SENI to listOf("seni", "kreativitas")
    )

    val filterSubjects: List<ForumSubject> = ForumSubject.entries

    fun fromCode(code: String?): ForumSubject? {
        val trimmed = code?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return ForumSubject.entries.firstOrNull { it.code.equals(trimmed, ignoreCase = true) }
    }

    fun fromName(name: String?): ForumSubject? {
        val normalized = name?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
        return keywordRules.firstOrNull { (_, keywords) ->
            keywords.any { keyword -> normalized.contains(keyword) }
        }?.first
    }

    fun resolve(mapelCode: String?, subjectName: String?): ForumSubject? =
        fromCode(mapelCode) ?: fromName(subjectName)

    fun resolveCode(mapelCode: String?, subjectName: String?): String? =
        resolve(mapelCode, subjectName)?.code

    fun initialSubject(mapelCode: String?, subjectName: String?): ForumSubject =
        resolve(mapelCode, subjectName) ?: ForumSubject.SENI

    fun headerTitle(className: String?): String =
        className?.trim()?.takeIf { it.isNotEmpty() } ?: DEFAULT_HEADER_TITLE

    fun threadTitle(subject: ForumSubject): String =
        "Diskusi ${subject.displayName}"

    fun subtitle(subject: ForumSubject, studentCount: Int, teacherCount: Int): String =
        "Mapel ${subject.displayName} \u2022 $studentCount Siswa \u2022 $teacherCount Guru Pengajar"

    fun inputHint(subject: ForumSubject): String =
        "Tulis diskusi ${subject.displayName}..."

    fun emptyMessage(subject: ForumSubject): String =
        "Belum ada diskusi di Mapel ${subject.displayName}$EMPTY_SUFFIX"

    fun bannerTitle(className: String?, subjectCode: String?): String {
        val cleanClass = className?.trim().orEmpty()
        val subject = fromCode(subjectCode)
        return when {
            subject != null && cleanClass.isNotEmpty() -> "Diskusi ${subject.chipLabel} \u2022 $cleanClass"
            subject != null -> "Diskusi ${subject.displayName}"
            cleanClass.isNotEmpty() -> "Ruang Diskusi $cleanClass"
            else -> "Ruang Diskusi Kelas Aktif"
        }
    }

    fun bannerSubtitle(threadCount: Int, subjectCode: String?): String {
        val subject = fromCode(subjectCode)
        if (threadCount <= 0) {
            return subject?.let { "Belum ada diskusi ${it.displayName}" }
                ?: "Belum ada pesan di forum kelas"
        }
        return subject?.let { "$threadCount pesan diskusi ${it.displayName}" }
            ?: "$threadCount pesan diskusi wali murid & guru"
    }

    fun filterThreadsBySubject(threads: List<ForumThreadItem>, subjectCode: String?): List<ForumThreadItem> {
        val target = fromCode(subjectCode) ?: return threads
        return threads.filter { it.subjectCode?.trim().equals(target.code, ignoreCase = true) }
    }

    fun resolveFilteredBannerCount(
        threads: List<ForumThreadItem>,
        serverTotal: Int,
        subjectCode: String?
    ): Int {
        val matching = filterThreadsBySubject(threads, subjectCode)
        return if (matching.size == threads.size) maxOf(serverTotal, matching.size) else matching.size
    }
}
