package com.nthb.kibooz.data.remote

import com.nthb.kibooz.ml.Emotion

/** `data` dari `GET /classes/{classId}/students`: halaman siswa beserta `page`, `limit` dan `total`. */
data class ClassStudentList(
    val page: Int,
    val limit: Int,
    val total: Int,
    val students: List<ClassStudent>
)

data class ClassStudent(
    val id: String,
    val fullName: String,
    val nisn: String,
    val avatarUrl: String?,
    val currentPoints: Int,
    val seasonPoints: Int,
    val classRank: Int,
    /** Mood terbaru hari ini, null jika belum dicatat. */
    val todayMood: Emotion?
)

/** `data` dari `GET /guru/classes`: halaman kelas yang diajar Guru beserta `page`, `limit` dan `total`. */
data class ClassList(
    val page: Int,
    val limit: Int,
    val total: Int,
    val classes: List<ClassSummary>
)

data class ClassSummary(
    val id: String,
    val name: String,
    val gradeLevel: String,
    val schoolName: String,
    val academicYear: String,
    val joinCode: String,
    val joinOpen: Boolean,
    val totalStudents: Int,
    val createdAt: String
)

data class ChildList(val children: List<ChildSummary>)

/** Anak milik Wali. [className] di sini berupa nama kelas biasa, bukan format dashboard. */
data class ChildSummary(
    val id: String,
    val fullName: String,
    val nisn: String,
    val avatarUrl: String?,
    val classId: String,
    val className: String,
    /** True selama guru menjamin NISN. Server mengirimnya di setiap daftar anak. */
    val nisnVerified: Boolean = false,
    /** True jika [nisn] kosong karena guru memverifikasi nomor yang sama untuk anak lain. Wali harus mengisi nomor yang benar. */
    val nisnNeedsUpdate: Boolean = false,
    /** Waktu kelas diarsipkan, hilang selama belum. Kelas arsip hanya bisa dibaca. */
    val classArchivedAt: String? = null
)
