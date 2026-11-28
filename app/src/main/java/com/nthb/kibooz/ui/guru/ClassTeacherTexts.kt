package com.nthb.kibooz.ui.guru

import android.content.Context
import com.nthb.kibooz.R
import com.nthb.kibooz.util.ClassNames

/** Teks ringkasan pengajar kelas, disusun di layar dari daftar nama dan teks dari string resource. */
object ClassTeacherTexts {

    fun headline(context: Context, names: List<String>): String = when (names.size) {
        0 -> context.getString(R.string.class_teacher_default_name)
        1 -> names[0]
        2 -> context.getString(R.string.class_teacher_pair, names[0], names[1])
        else -> context.getString(R.string.class_teacher_more, names[0], names.size - 1)
    }

    fun pengajarLine(context: Context, names: List<String>): String =
        context.getString(R.string.class_teacher_line, headline(context, names))

    fun subtitle(context: Context, teacherCount: Int, className: String?): String {
        val role = if (teacherCount > 1) {
            context.getString(R.string.class_teacher_role_count, teacherCount)
        } else {
            context.getString(R.string.class_teacher_role)
        }
        val cleanClass = className?.trim()?.takeIf { it.isNotEmpty() }?.let(ClassNames::withPrefix)
        return cleanClass?.let { context.getString(R.string.class_teacher_subtitle_class, role, it) } ?: role
    }

    fun bannerCount(context: Context, teacherCount: Int, studentCountText: String): String =
        context.getString(R.string.class_teacher_banner_count, maxOf(1, teacherCount), studentCountText)
}
