package com.nthb.kibooz.ui.guru

import android.content.Context
import androidx.annotation.StringRes
import com.nthb.kibooz.R
import com.nthb.kibooz.util.ClassNames

/** Pengambil teks dari string resource, dipisah dari Context agar cabang nama bisa diuji tanpa Android. */
interface TeacherStrings {
    fun get(@StringRes id: Int): String

    fun get(@StringRes id: Int, first: Any): String

    fun get(@StringRes id: Int, first: Any, second: Any): String
}

private class ContextStrings(private val context: Context) : TeacherStrings {
    override fun get(id: Int): String = context.getString(id)

    override fun get(id: Int, first: Any): String = context.getString(id, first)

    override fun get(id: Int, first: Any, second: Any): String = context.getString(id, first, second)
}

/** Teks ringkasan pengajar kelas, disusun di layar dari daftar nama dan teks dari string resource. */
class ClassTeacherTexts(private val strings: TeacherStrings) {

    fun headline(names: List<String>): String = when (names.size) {
        0 -> strings.get(R.string.class_teacher_default_name)
        1 -> names[0]
        2 -> strings.get(R.string.class_teacher_pair, names[0], names[1])
        else -> strings.get(R.string.class_teacher_more, names[0], names.size - 1)
    }

    fun pengajarLine(names: List<String>): String = strings.get(R.string.class_teacher_line, headline(names))

    fun subtitle(teacherCount: Int, className: String?): String {
        val role = if (teacherCount > 1) {
            strings.get(R.string.class_teacher_role_count, teacherCount)
        } else {
            strings.get(R.string.class_teacher_role)
        }
        val cleanClass = className?.trim()?.takeIf { it.isNotEmpty() }?.let(ClassNames::withPrefix)
        return cleanClass?.let { strings.get(R.string.class_teacher_subtitle_class, role, it) } ?: role
    }

    fun bannerCount(teacherCount: Int, studentCountText: String): String =
        strings.get(R.string.class_teacher_banner_count, maxOf(1, teacherCount), studentCountText)

    companion object {
        fun of(context: Context) = ClassTeacherTexts(ContextStrings(context))
    }
}
