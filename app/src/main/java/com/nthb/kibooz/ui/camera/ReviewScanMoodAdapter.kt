package com.nthb.kibooz.ui.camera

import android.text.InputFilter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.RecyclerView
import com.nthb.kibooz.R
import com.nthb.kibooz.data.remote.MoodSource
import com.nthb.kibooz.databinding.ItemReviewScanStudentBinding
import com.nthb.kibooz.model.ScannedStudentMood

class ReviewScanMoodAdapter(
    private val students: MutableList<ScannedStudentMood>,
    private val onMoodChanged: (student: ScannedStudentMood, newMood: String) -> Unit,
    private val onConfirmed: (student: ScannedStudentMood) -> Unit,
    private val onDeleteClicked: (student: ScannedStudentMood, position: Int) -> Unit
) : RecyclerView.Adapter<ReviewScanMoodAdapter.StudentViewHolder>() {

    inner class StudentViewHolder(val binding: ItemReviewScanStudentBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(student: ScannedStudentMood) {
            binding.tvStudentName.text = student.studentName
            binding.ivAvatar.setImageResource(student.avatarRes)

            // AI Confidence Score Badge. Label pilihan guru tidak punya skor model
            val confidencePct = (student.confidenceScore * 100).toInt()
            binding.tvAiDetectionBadge.text = if (student.source == MoodSource.MANUAL_INPUT) {
                binding.root.context.getString(R.string.review_dipilih_guru)
            } else {
                "AI: ${student.detectedMood} ($confidencePct%)"
            }

            // Aturan Bisnis BR-04: di bawah 75% guru harus mengonfirmasi atau mengganti labelnya
            updateConfirmationWarning(student)
            binding.tvLowConfidenceWarning.setOnClickListener {
                student.isConfirmed = true
                updateConfirmationWarning(student)
                onConfirmed(student)
            }

            // Sync notes
            if (binding.etTeacherNotes.text.toString() != student.teacherNotes) {
                binding.etTeacherNotes.setText(student.teacherNotes)
            }

            binding.etTeacherNotes.filters = arrayOf(InputFilter.LengthFilter(MAX_NOTES_LENGTH))
            binding.etTeacherNotes.doAfterTextChanged { text ->
                student.teacherNotes = text?.toString().orEmpty()
            }

            // Set mood button styles
            updateMoodSelectionUI(student.currentMood)

            // Setup Mood Click Listeners
            chips().forEach { (mood, chip) ->
                chip.view.setOnClickListener {
                    if (student.currentMood != mood) {
                        student.currentMood = mood
                        updateMoodSelectionUI(mood)
                        updateConfirmationWarning(student)
                        onMoodChanged(student, mood)
                    }
                }
            }

            // Delete Action
            binding.ivDeleteStudent.setOnClickListener {
                val currentPos = bindingAdapterPosition
                if (currentPos != RecyclerView.NO_POSITION) {
                    onDeleteClicked(student, currentPos)
                }
            }
        }

        private fun updateConfirmationWarning(student: ScannedStudentMood) {
            binding.tvLowConfidenceWarning.text = binding.root.context.getString(R.string.review_perlu_dicek)
            binding.tvLowConfidenceWarning.visibility = if (student.needsConfirmation()) View.VISIBLE else View.GONE
        }

        /** Satu chip untuk setiap label di [REVIEW_MOOD_LABELS], dengan warna saat terpilih. */
        private fun chips(): Map<String, Chip> = mapOf(
            "SENANG" to Chip(binding.btnMoodHappy, R.drawable.bg_chip_mood_happy, 0xFF7A4F01.toInt()),
            "KALEM" to Chip(binding.btnMoodKalem, R.drawable.bg_chip_mood_kalem, 0xFF2E7D32.toInt()),
            "SEDIH" to Chip(binding.btnMoodSedih, R.drawable.bg_chip_mood_sedih, 0xFF1565C0.toInt()),
            "BINGUNG" to Chip(binding.btnMoodBingung, R.drawable.bg_chip_mood_bingung, 0xFFE65100.toInt()),
            "MARAH" to Chip(binding.btnMoodMarah, R.drawable.bg_chip_mood_marah, 0xFFC62828.toInt())
        )

        private fun updateMoodSelectionUI(selectedMood: String) {
            val textColorDark = ContextCompat.getColor(binding.root.context, R.color.kibooz_text_dark)

            chips().forEach { (mood, chip) ->
                val selected = mood == selectedMood
                chip.view.setBackgroundResource(if (selected) chip.selectedBackground else R.drawable.bg_chip_mood_unselected)
                chip.view.setTextColor(if (selected) chip.selectedTextColor else textColorDark)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StudentViewHolder {
        val binding = ItemReviewScanStudentBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return StudentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: StudentViewHolder, position: Int) {
        holder.bind(students[position])
    }

    override fun getItemCount(): Int = students.size
}

/** Satu chip mood di layar review beserta latar dan warna teksnya saat terpilih. */
private class Chip(val view: TextView, @DrawableRes val selectedBackground: Int, val selectedTextColor: Int)
