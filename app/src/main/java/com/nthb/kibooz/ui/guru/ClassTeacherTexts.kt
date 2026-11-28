package com.nthb.kibooz.ui.guru

import android.content.Context
import com.nthb.kibooz.R
import com.nthb.kibooz.util.ClassNames

/**
 * Teks ringkasan pengajar kelas, disusun di layar dari daftar nama dan teks dari string resource.
 * Pengambil teks diberikan dari luar agar cabang nama bisa diuji tanpa Context.
 */
class ClassTeacherTexts(private val text: (Int, List<Any>) -> String) {

    fun headline(names: List<String>): String = when (names.size) {
        0 -> text(R.string.class_teacher_default_name, emptyList())
        1 -> names[0]
        2 -> text(R.string.class_teacher_pair, listOf(names[0], names[1]))
        else -> text(R.string.class_teacher_more, listOf(names[0], names.size - 1))
    }

    fun pengajarLine(names: List<String>): String = text(R.string.class_teacher_line, listOf(headline(names)))

    fun subtitle(teacherCount: Int, className: String?): String {
        val role = if (teacherCount > 1) {
            text(R.string.class_teacher_role_count, listOf(teacherCount))
        } else {
            text(R.string.class_teacher_role, emptyList())
        }
        val cleanClass = className?.trim()?.takeIf { it.isNotEmpty() }?.let(ClassNames::withPrefix)
        return cleanClass?.let { text(R.string.class_teacher_subtitle_class, listOf(role, it)) } ?: role
    }

    fun bannerCount(teacherCount: Int, studentCountText: String): String =
        text(R.string.class_teacher_banner_count, listOf(maxOf(1, teacherCount), studentCountText))

    companion object {
        fun of(context: Context) = ClassTeacherTexts { id, args -> context.getString(id, *args.toTypedArray()) }
    }
}
