package com.nthb.kibooz.ui.camera

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nthb.kibooz.R

object StudentPickerDialog {

    /**
     * Menampilkan daftar siswa. Siswa yang [isTaken] tidak bisa dipilih dan diberi keterangan, bukan hanya diredupkan.
     */
    fun show(context: Context, students: List<Student>, isTaken: (Student) -> Boolean, onPick: (Student) -> Unit) {
        val adapter = object : ArrayAdapter<Student>(context, android.R.layout.simple_list_item_1, students) {

            override fun isEnabled(position: Int) = !isTaken(students[position])

            override fun areAllItemsEnabled() = false

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                val student = students[position]
                val taken = isTaken(student)

                (view as android.widget.TextView).text =
                    if (taken) context.getString(R.string.pilih_siswa_sudah_dipilih, student.name) else student.name
                view.alpha = if (taken) 0.4f else 1f

                return view
            }
        }

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.pilih_siswa_judul)
            .setAdapter(adapter) { _, which -> onPick(students[which]) }
            .setNegativeButton(R.string.konsen_batal, null)
            .show()
    }
}
