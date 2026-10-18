package com.nthb.kibooz.ui.camera

interface StudentSource {
    fun students(): List<Student>
}

/** Daftar contoh sampai ClassRepository (GET /classes/{classId}/students) tersedia. */
class FakeStudentSource : StudentSource {

    override fun students(): List<Student> = listOf(
        Student("siswa-1", "Amelia Siti Zahra"),
        Student("siswa-2", "Farhan Al-Fatih"),
        Student("siswa-3", "Kenzo Putra"),
        Student("siswa-4", "Zahra Putri"),
        Student("siswa-5", "Zainal Abidin")
    )
}
