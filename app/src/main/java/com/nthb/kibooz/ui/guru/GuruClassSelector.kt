package com.nthb.kibooz.ui.guru

import android.content.Context
import android.view.View
import android.widget.TextView
import androidx.core.view.isVisible
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nthb.kibooz.R
import com.nthb.kibooz.data.remote.ApiResult
import com.nthb.kibooz.data.remote.ClassSummary
import com.nthb.kibooz.data.repository.ClassRepository

/**
 * Menyimpan ID kelas Guru yang sedang dipilih selama sesi login berjalan agar Home Guru,
 * Statistik Mood, Leaderboard, dan Statistik Kinerja menampilkan kelas yang seragam.
 */
class GuruClassSelection {
    private var ownerUserId: String? = null
    private var selectedClassId: String? = null

    @Synchronized
    fun current(userId: String?): String? {
        if (ownerUserId != userId) {
            ownerUserId = userId
            selectedClassId = null
        }
        return selectedClassId
    }

    @Synchronized
    fun select(userId: String?, classId: String?) {
        ownerUserId = userId
        selectedClassId = classId?.takeIf { it.isNotBlank() }
    }
}

/** Mengembalikan kelas aktif terlebih dahulu, atau seluruh kelas bila semua sudah diarsipkan. */
fun visibleTeacherClasses(all: List<ClassSummary>): List<ClassSummary> {
    val active = all.filterNot { it.archived }
    return if (active.isNotEmpty()) active else all
}

fun resolveSelectedClass(
    classes: List<ClassSummary>,
    selectedClassId: String?,
    fallbackClassId: String? = null
): ClassSummary? {
    if (classes.isEmpty()) return null
    val targetId = selectedClassId?.takeIf { it.isNotBlank() } ?: fallbackClassId?.takeIf { it.isNotBlank() }
    return classes.firstOrNull { it.id == targetId } ?: classes.first()
}

suspend fun fetchVisibleTeacherClasses(classRepository: ClassRepository): List<ClassSummary>? =
    when (val result = classRepository.teacherClasses()) {
        is ApiResult.Success -> visibleTeacherClasses(result.data)
        else -> null
    }

suspend fun loadActiveTeacherClasses(classRepository: ClassRepository): List<ClassSummary> =
    fetchVisibleTeacherClasses(classRepository).orEmpty()

fun showGuruClassPickerDialog(
    context: Context,
    classes: List<ClassSummary>,
    selectedClassId: String?,
    onSelected: (ClassSummary) -> Unit
) {
    if (classes.size <= 1) return
    val labels = classes.map { item ->
        if (item.archived) {
            context.getString(R.string.guru_kelas_item_arsip, item.name, item.totalStudents)
        } else {
            context.getString(R.string.guru_kelas_item_aktif, item.name, item.totalStudents)
        }
    }.toTypedArray()
    val checkedIndex = classes.indexOfFirst { it.id == selectedClassId }
    MaterialAlertDialogBuilder(context)
        .setTitle(R.string.guru_pilih_kelas_judul)
        .setSingleChoiceItems(labels, checkedIndex) { dialog, which ->
            dialog.dismiss()
            classes.getOrNull(which)?.let(onSelected)
        }
        .setNegativeButton(R.string.batal, null)
        .show()
}

fun bindClassSwitcher(
    container: View,
    labelView: TextView,
    actionView: View,
    classes: List<ClassSummary>,
    selectedClassId: String?,
    fallbackClassName: String? = null,
    compactLabel: Boolean = false,
    onClassPicked: (ClassSummary) -> Unit
) {
    val activeClass = resolveSelectedClass(classes, selectedClassId)
    val labelText = when {
        activeClass != null && compactLabel -> activeClass.name
        activeClass != null -> container.context.getString(R.string.guru_kelas_ringkas, activeClass.name, activeClass.totalStudents)
        !fallbackClassName.isNullOrBlank() -> fallbackClassName
        else -> null
    }
    container.isVisible = labelText != null
    if (labelText == null) return

    labelView.text = labelText
    val canSwitch = classes.size > 1
    actionView.isVisible = canSwitch
    container.isClickable = canSwitch
    container.isFocusable = canSwitch
    if (canSwitch) {
        container.setOnClickListener {
            showGuruClassPickerDialog(container.context, classes, activeClass?.id ?: selectedClassId, onClassPicked)
        }
    } else {
        container.setOnClickListener(null)
    }
}
