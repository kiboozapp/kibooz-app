package com.nthb.kibooz.model

import com.nthb.kibooz.data.remote.MoodSource
import java.io.Serializable

data class ScannedStudentMood(
    val id: String,
    var studentName: String,
    val avatarRes: Int,
    var currentMood: String, // "SENANG", "KALEM", "BINGUNG", "MARAH" atau "SEDIH"
    val confidenceScore: Float, // e.g. 0.94f
    var teacherNotes: String = "",
    var isConfirmed: Boolean = true,
    /** AI_CAMERA jika labelnya dari model, MANUAL_INPUT jika guru sudah menggantinya di layar kamera. */
    val source: MoodSource = MoodSource.AI_CAMERA,
    /** Label yang diterima dari kamera. [currentMood] berbeda dari ini berarti guru mengubahnya di layar review. */
    val detectedMood: String = currentMood
) : Serializable
