package com.nthb.kibooz.ui.camera

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.RecyclerView
import com.nthb.kibooz.R
import com.nthb.kibooz.databinding.ItemReviewScanStudentBinding
import com.nthb.kibooz.model.ScannedStudentMood

class ReviewScanMoodAdapter(
    private val students: MutableList<ScannedStudentMood>,
    private val onMoodChanged: (student: ScannedStudentMood, newMood: String) -> Unit,
    private val onDeleteClicked: (student: ScannedStudentMood, position: Int) -> Unit
) : RecyclerView.Adapter<ReviewScanMoodAdapter.StudentViewHolder>() {

    inner class StudentViewHolder(val binding: ItemReviewScanStudentBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(student: ScannedStudentMood) {
            binding.tvStudentName.text = student.studentName
            binding.ivAvatar.setImageResource(student.avatarRes)

            // AI Confidence Score Badge
            val confidencePct = (student.confidenceScore * 100).toInt()
            binding.tvAiDetectionBadge.text = "AI: ${student.currentMood} ($confidencePct%)"

            // Warning if confidence is below 75% (Aturan Bisnis BR-04)
            if (student.confidenceScore < 0.75f) {
                binding.tvLowConfidenceWarning.visibility = View.VISIBLE
            } else {
                binding.tvLowConfidenceWarning.visibility = View.GONE
            }

            // Sync notes
            if (binding.etTeacherNotes.text.toString() != student.teacherNotes) {
                binding.etTeacherNotes.setText(student.teacherNotes)
            }

            binding.etTeacherNotes.doAfterTextChanged { text ->
                student.teacherNotes = text?.toString().orEmpty()
            }

            // Set mood button styles
            updateMoodSelectionUI(student.currentMood)

            // Setup Mood Click Listeners
            binding.btnMoodHappy.setOnClickListener {
                if (student.currentMood != "SENANG") {
                    student.currentMood = "SENANG"
                    updateMoodSelectionUI("SENANG")
                    onMoodChanged(student, "SENANG")
                }
            }

            binding.btnMoodKalem.setOnClickListener {
                if (student.currentMood != "KALEM") {
                    student.currentMood = "KALEM"
                    updateMoodSelectionUI("KALEM")
                    onMoodChanged(student, "KALEM")
                }
            }

            binding.btnMoodBingung.setOnClickListener {
                if (student.currentMood != "BINGUNG") {
                    student.currentMood = "BINGUNG"
                    updateMoodSelectionUI("BINGUNG")
                    onMoodChanged(student, "BINGUNG")
                }
            }

            binding.btnMoodMarah.setOnClickListener {
                if (student.currentMood != "MARAH") {
                    student.currentMood = "MARAH"
                    updateMoodSelectionUI("MARAH")
                    onMoodChanged(student, "MARAH")
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

        private fun updateMoodSelectionUI(selectedMood: String) {
            val context = binding.root.context
            val textColorDark = ContextCompat.getColor(context, R.color.kibooz_text_dark)

            // Reset all to unselected
            binding.btnMoodHappy.setBackgroundResource(R.drawable.bg_chip_mood_unselected)
            binding.btnMoodHappy.setTextColor(textColorDark)

            binding.btnMoodKalem.setBackgroundResource(R.drawable.bg_chip_mood_unselected)
            binding.btnMoodKalem.setTextColor(textColorDark)

            binding.btnMoodBingung.setBackgroundResource(R.drawable.bg_chip_mood_unselected)
            binding.btnMoodBingung.setTextColor(textColorDark)

            binding.btnMoodMarah.setBackgroundResource(R.drawable.bg_chip_mood_unselected)
            binding.btnMoodMarah.setTextColor(textColorDark)

            // Highlight chosen mood
            when (selectedMood) {
                "SENANG" -> {
                    binding.btnMoodHappy.setBackgroundResource(R.drawable.bg_chip_mood_happy)
                    binding.btnMoodHappy.setTextColor(0xFF7A4F01.toInt())
                }
                "KALEM" -> {
                    binding.btnMoodKalem.setBackgroundResource(R.drawable.bg_chip_mood_kalem)
                    binding.btnMoodKalem.setTextColor(0xFF2E7D32.toInt())
                }
                "BINGUNG" -> {
                    binding.btnMoodBingung.setBackgroundResource(R.drawable.bg_chip_mood_bingung)
                    binding.btnMoodBingung.setTextColor(0xFFE65100.toInt())
                }
                "MARAH" -> {
                    binding.btnMoodMarah.setBackgroundResource(R.drawable.bg_chip_mood_marah)
                    binding.btnMoodMarah.setTextColor(0xFFC62828.toInt())
                }
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
