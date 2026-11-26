package com.nthb.kibooz.data.remote

/** Satu guru di sebuah kelas. */
data class ClassTeacher(
    /** Id guru di rute kelas, dipakai sebagai `teacherId`. Bukan id akun. */
    val id: String,
    val userId: String,
    /** True untuk akun yang sedang masuk. */
    val isSelf: Boolean,
    val fullName: String,
    val nip: String?,
    val avatarUrl: String?,
    /** True untuk satu-satunya pemilik kelas. */
    val isOwner: Boolean,
    val joinedAt: String
)

/** `data` dari `GET /guru/classes/{classId}/teachers`, yang paling lama bergabung lebih dulu. */
data class ClassTeacherList(val teachers: List<ClassTeacher>)

/** Isi `POST /guru/classes/{classId}/teachers`. */
data class AddTeacherRequest(val email: String)

/** Isi `PUT /guru/classes/{classId}/owner`. */
data class TransferOwnershipRequest(val teacherId: String)
