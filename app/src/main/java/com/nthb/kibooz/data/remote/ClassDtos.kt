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
    val todayMood: Emotion?,
    /** True selama guru menjamin kebenaran NISN siswa ini. */
    val nisnVerified: Boolean = false,
    /** True jika NISN dikosongkan karena guru memverifikasi nomor sama untuk anak lain. */
    val nisnNeedsUpdate: Boolean = false
)

/** `data` dari `POST/DELETE /guru/classes/{classId}/students/{studentId}/nisn-verification`. */
data class NisnVerificationData(
    val nisnVerified: Boolean,
    val displaced: Int = 0
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
    val createdAt: String,
    /** Waktu kelas diarsipkan, null selama kelas belum diarsipkan. Kelas yang diarsipkan hanya bisa dibaca. */
    val archivedAt: String? = null
) {
    val archived: Boolean get() = archivedAt != null
}

/** Isi `POST /guru/classes`. Isian yang null tidak dikirim, jadi server memakai nilai bawaannya (sekolah Guru, tahun ajaran berjalan). */
data class CreateClassRequest(
    val name: String,
    val gradeLevel: String? = null,
    val academicYear: String? = null,
    val schoolName: String? = null
)

/** `data` dari `POST /guru/classes/{classId}/join-code`. */
data class JoinCodeData(val joinCode: String)

/** Isi `PUT /guru/classes/{classId}/joining`. */
data class SetJoiningRequest(val joinOpen: Boolean)

/** `data` dari `PUT /guru/classes/{classId}/joining`. */
data class JoiningData(val joinOpen: Boolean)

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
    val classArchivedAt: String? = null,
    val joinCode: String? = null
)

/** Satu permohonan pindah kelas yang menunggu keputusan guru (`GET /guru/classes/{classId}/move-requests`). */
data class ClassMoveRequest(
    val id: String,
    val studentId: String,
    val studentName: String,
    val requestedAt: String
)

data class ClassMoveRequestList(val requests: List<ClassMoveRequest> = emptyList())

/** Pengajar dalam satu kelas (`GET /guru/classes/{classId}/teachers`). */
data class ClassTeacher(
    val id: String,
    val userId: String = "",
    val isSelf: Boolean = false,
    val fullName: String,
    val nip: String? = null,
    val avatarUrl: String? = null,
    val isOwner: Boolean = false,
    val joinedAt: String = ""
)

data class ClassTeacherList(val teachers: List<ClassTeacher> = emptyList())
