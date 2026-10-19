package com.nthb.kibooz.ui.camera

interface StudentSource {
    fun students(): List<Student>
}

/**
 * Daftar contoh sampai ClassRepository (GET /classes/{classId}/students) tersedia. Id-nya berbentuk UUID
 * karena server menolak studentId lain dengan VALIDATION_ERROR.
 */
class FakeStudentSource : StudentSource {

    override fun students(): List<Student> = listOf(
        Student("6f1c2a52-0d4b-4a7e-9c11-3b8e5d2f0a01", "Amelia Siti Zahra"),
        Student("6f1c2a52-0d4b-4a7e-9c11-3b8e5d2f0a02", "Farhan Al-Fatih"),
        Student("6f1c2a52-0d4b-4a7e-9c11-3b8e5d2f0a03", "Kenzo Putra"),
        Student("6f1c2a52-0d4b-4a7e-9c11-3b8e5d2f0a04", "Zahra Putri"),
        Student("6f1c2a52-0d4b-4a7e-9c11-3b8e5d2f0a05", "Zainal Abidin")
    )
}
