package com.nthb.kibooz.model

import java.io.Serializable

data class ScannedStudentMood(
    val id: String,
    var studentName: String,
    val avatarRes: Int,
    var currentMood: String, // "SENANG", "KALEM", "BINGUNG", "MARAH"
    val confidenceScore: Float, // e.g. 0.94f
    var teacherNotes: String = "",
    var isConfirmed: Boolean = true
) : Serializable
