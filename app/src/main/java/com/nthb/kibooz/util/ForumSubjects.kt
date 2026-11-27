package com.nthb.kibooz.util

/** Kode dan label mata pelajaran kurikulum Kibooz untuk segmentasi Forum Diskusi per kelas. */
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

sealed interface ForumFilter {
    val chipLabel: String
    val subjectCodeQuery: String?
    val generalOnlyQuery: Boolean?
    val postSubjectCode: String?

    data object All : ForumFilter {
        override val chipLabel: String = ForumSubjects.ALL_LABEL
        override val subjectCodeQuery: String? = null
        override val generalOnlyQuery: Boolean? = null
        override val postSubjectCode: String? = null
    }

    data object General : ForumFilter {
        override val chipLabel: String = ForumSubjects.GENERAL_LABEL
        override val subjectCodeQuery: String? = null
        override val generalOnlyQuery: Boolean = true
        override val postSubjectCode: String? = null
    }

    data class Subject(val code: String) : ForumFilter {
        override val chipLabel: String
            get() = ForumSubjects.fromCode(code)?.chipLabel ?: code
        override val subjectCodeQuery: String = code
        override val generalOnlyQuery: Boolean? = null
        override val postSubjectCode: String = code
    }
}

object ForumSubjects {
    const val EXTRA_MAPEL_CODE = "MAPEL_CODE"
    const val EXTRA_MAPEL_NAME = "MAPEL_NAME"
    const val ALL_LABEL = "Semua"
    const val GENERAL_LABEL = "Umum"
    const val GENERAL_BADGE = "Umum Kelas"

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

    val filterOptions: List<ForumFilter> = listOf(ForumFilter.All, ForumFilter.General) +
        ForumSubject.entries.map { ForumFilter.Subject(it.code) }

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

    fun initialFilter(mapelCode: String?, subjectName: String?): ForumFilter =
        resolveCode(mapelCode, subjectName)?.let { ForumFilter.Subject(it) } ?: ForumFilter.All

    fun headerTitle(className: String?): String =
        className?.trim()?.takeIf { it.isNotEmpty() } ?: DEFAULT_HEADER_TITLE

    fun threadTitle(filter: ForumFilter): String = when (filter) {
        is ForumFilter.Subject -> "Diskusi ${subjectDisplayName(filter.code)}"
        ForumFilter.General -> "Diskusi Umum Kelas"
        ForumFilter.All -> "Diskusi Kelas"
    }

    fun threadBadge(subjectCode: String?): String =
        fromCode(subjectCode)?.displayName ?: GENERAL_BADGE

    fun subtitle(filter: ForumFilter, studentCount: Int, teacherCount: Int): String {
        val topic = when (filter) {
            is ForumFilter.Subject -> "Mapel ${subjectDisplayName(filter.code)}"
            ForumFilter.General -> GENERAL_BADGE
            ForumFilter.All -> "Semua Topik Kelas"
        }
        return "$topic \u2022 $studentCount Siswa \u2022 $teacherCount Guru Pengajar"
    }

    fun inputHint(filter: ForumFilter): String = when (filter) {
        is ForumFilter.Subject -> "Tulis diskusi ${subjectDisplayName(filter.code)}..."
        ForumFilter.General -> "Tulis diskusi umum kelas..."
        ForumFilter.All -> "Tulis pesan diskusi kelas..."
    }

    fun emptyMessage(filter: ForumFilter): String = when (filter) {
        is ForumFilter.Subject -> "Belum ada diskusi di ${subjectDisplayName(filter.code)}$EMPTY_SUFFIX"
        ForumFilter.General -> "Belum ada diskusi umum di kelas ini$EMPTY_SUFFIX"
        ForumFilter.All -> "Belum ada pesan di forum diskusi ini$EMPTY_SUFFIX"
    }

    fun bannerTitle(className: String?, subjectCode: String?): String {
        val cleanClass = className?.trim()?.takeIf { it.isNotEmpty() }
        val subject = fromCode(subjectCode)
        return when {
            subject != null && cleanClass != null -> "Diskusi ${subject.chipLabel} \u2022 $cleanClass"
            subject != null -> "Diskusi ${subject.displayName}"
            cleanClass != null -> "Ruang Diskusi $cleanClass"
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

    private fun subjectDisplayName(code: String): String =
        fromCode(code)?.displayName ?: code
}
