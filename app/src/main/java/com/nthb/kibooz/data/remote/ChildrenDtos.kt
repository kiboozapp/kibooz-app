package com.nthb.kibooz.data.remote

/** Data Wali dari `PATCH /wali/profile` dan dari `guardian` di `GET /wali/child/{studentId}`. Email tidak bisa diubah. */
data class Guardian(
    val fullName: String,
    val email: String,
    val phoneNumber: String?,
    val whatsappNumber: String?,
    val address: String?
)

/**
 * `data` dari `GET /wali/child/{studentId}`. [nisn] kosong jika [nisnNeedsUpdate] true, yaitu ketika guru memverifikasi
 * nomor yang sama untuk anak lain. [nisnVerified] true selama guru menjamin nomornya.
 */
data class ChildProfile(
    val id: String,
    val fullName: String,
    val nisn: String,
    val nisnVerified: Boolean = false,
    val nisnNeedsUpdate: Boolean = false,
    val avatarUrl: String?,
    val classId: String,
    val className: String,
    val gradeLevel: String,
    val schoolName: String,
    val academicYear: String,
    val currentPoints: Int,
    val seasonPoints: Int,
    val classRank: Int,
    val guardian: Guardian
)

/** Isi `POST /wali/children`. [classCode] huruf besar 4 sampai 12 huruf atau angka. */
data class AddChildRequest(val classCode: String, val student: NewStudent)

/** Isi `PATCH /wali/children/{studentId}`. Isian null tidak ikut terkirim dan tidak berubah di server. Paling sedikit satu terisi. */
data class UpdateChildRequest(val fullName: String? = null, val nisn: String? = null)

/**
 * Isi `PATCH /wali/profile`. Isian null tidak ikut terkirim dan tidak berubah di server. Teks kosong menghapus nomor
 * telepon, nomor WhatsApp atau alamat. Paling sedikit satu terisi.
 */
data class UpdateWaliProfileRequest(
    val fullName: String? = null,
    val phoneNumber: String? = null,
    val whatsappNumber: String? = null,
    val address: String? = null
)
