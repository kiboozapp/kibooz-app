package com.nthb.kibooz.ui.guru

/** Spesifikasi satu video materi bawaan yang siap disimpan ke server atau dipakai sebagai cadangan offline. */
data class StarterVideoSpec(
    val title: String,
    val description: String,
    val videoUrl: String
)

/**
 * Daftar video bawaan per tema pembelajaran. Setiap entri memakai alamat https:// yang sah sehingga dapat
 * disimpan langsung ke endpoint `POST /classes/{classId}/videos` menjadi data resmi di server.
 */
object StarterVideos {

    fun forSubject(subjectName: String): List<StarterVideoSpec> = when {
        subjectName.contains("Seni", ignoreCase = true) -> listOf(
            StarterVideoSpec(
                title = "Kreasi Melipat & Menggunting Kertas",
                description = "Melatih kreativitas dan motorik halus anak lewat aktivitas seni melipat kertas warna.",
                videoUrl = "https://www.youtube.com/watch?v=VoukA9eehoQ"
            ),
            StarterVideoSpec(
                title = "Mengenal Warna Primer & Sekunder",
                description = "Mengenalkan pencampuran warna dasar secara visual dan menyenangkan.",
                videoUrl = "https://www.youtube.com/watch?v=e23kDLhj_qA"
            ),
            StarterVideoSpec(
                title = "Karya Daur Ulang Bersama Kibby",
                description = "Mengajak anak berkreasi memanfaatkan bahan bekas aman di rumah.",
                videoUrl = "https://www.youtube.com/watch?v=wAjP-tqKJi0"
            )
        )
        subjectName.contains("Logika", ignoreCase = true) || subjectName.contains("Berpikir", ignoreCase = true) -> listOf(
            StarterVideoSpec(
                title = "Belajar Berhitung Seru Bersama Kibby",
                description = "Pengenalan angka dan konsep berhitung dasar melalui permainan interaktif.",
                videoUrl = "https://www.youtube.com/watch?v=VoukA9eehoQ"
            ),
            StarterVideoSpec(
                title = "Mengenal Pola & Bentuk Geometri",
                description = "Mengidentifikasi bentuk lingkaran, segitiga, persegi, dan pola urutan sederhana.",
                videoUrl = "https://www.youtube.com/watch?v=e23kDLhj_qA"
            ),
            StarterVideoSpec(
                title = "Teka-Teki Logika Anak Ceria",
                description = "Melatih daya pikir sebab-akibat dengan teka-teki visual untuk usia dini.",
                videoUrl = "https://www.youtube.com/watch?v=TKOVGPjaTQc"
            )
        )
        subjectName.contains("Motorik", ignoreCase = true) || subjectName.contains("Fisik", ignoreCase = true) -> listOf(
            StarterVideoSpec(
                title = "Senam Ceria Anak Hebat",
                description = "Panduan gerak irama ringan untuk menjaga kebugaran dan keceriaan anak.",
                videoUrl = "https://www.youtube.com/watch?v=noh1FVOlBT8"
            ),
            StarterVideoSpec(
                title = "Gerakan Tangkas & Keseimbangan Tubuh",
                description = "Latihan koordinasi gerak dan keseimbangan tubuh yang aman dilakukan di rumah.",
                videoUrl = "https://www.youtube.com/watch?v=VoukA9eehoQ"
            ),
            StarterVideoSpec(
                title = "Permainan Melompat & Melempar Bola",
                description = "Permainan ketangkasan dasar untuk menguatkan otot kaki dan tangan.",
                videoUrl = "https://www.youtube.com/watch?v=wAjP-tqKJi0"
            )
        )
        subjectName.contains("Agama", ignoreCase = true) || subjectName.contains("Moral", ignoreCase = true) -> listOf(
            StarterVideoSpec(
                title = "Doa Harian & Adab Sebelum Makan",
                description = "Membiasakan doa harian dan adab sopan santun anak sejak usia dini.",
                videoUrl = "https://www.youtube.com/watch?v=wAjP-tqKJi0"
            ),
            StarterVideoSpec(
                title = "Kisah Teladan Kejujuran & Kasih Sayang",
                description = "Cerita pendek tentang sikap jujur dan saling menyayangi sesama teman.",
                videoUrl = "https://www.youtube.com/watch?v=Xz8qBhIj9Nw"
            ),
            StarterVideoSpec(
                title = "Menjaga Kebersihan Sebagian dari Iman",
                description = "Mengajarkan kebiasaan bersih diri dan menjaga kebersihan lingkungan kelas.",
                videoUrl = "https://www.youtube.com/watch?v=TKOVGPjaTQc"
            )
        )
        subjectName.contains("Sosial", ignoreCase = true) ||
            subjectName.contains("Emotional", ignoreCase = true) ||
            subjectName.contains("Emosional", ignoreCase = true) -> listOf(
            StarterVideoSpec(
                title = "Mengenal Emosi & Ekspresi Wajah",
                description = "Membantu anak mengenali perasaan senang, sedih, bingung, dan kesal secara sehat.",
                videoUrl = "https://www.youtube.com/watch?v=wAjP-tqKJi0"
            ),
            StarterVideoSpec(
                title = "Belajar Berbagi & Bekerja Sama",
                description = "Menumbuhkan empati serta kebiasaan berbagi mainan dan bekerja sama dengan teman.",
                videoUrl = "https://www.youtube.com/watch?v=Xz8qBhIj9Nw"
            ),
            StarterVideoSpec(
                title = "Mengucap Maaf & Terima Kasih",
                description = "Membiasakan kata ajaib tolong, maaf, dan terima kasih dalam pergaulan sehari-hari.",
                videoUrl = "https://www.youtube.com/watch?v=TKOVGPjaTQc"
            )
        )
        else -> listOf(
            StarterVideoSpec(
                title = "Mengenal Huruf dan Suara",
                description = "Mengenalkan huruf vokal, konsonan, dan pelafalan kata dasar bersama Kibby.",
                videoUrl = "https://www.youtube.com/watch?v=e23kDLhj_qA"
            ),
            StarterVideoSpec(
                title = "Mengenal Nama-Nama Benda di Sekitar",
                description = "Belajar mengenali benda sehari-hari di rumah dan kelas secara menyenangkan.",
                videoUrl = "https://www.youtube.com/watch?v=VoukA9eehoQ"
            ),
            StarterVideoSpec(
                title = "Ekspresi & Bercerita Bersama Kibby",
                description = "Melatih keberanian anak mengungkapkan perasaan dan bercerita bersama teman.",
                videoUrl = "https://www.youtube.com/watch?v=Xz8qBhIj9Nw"
            )
        )
    }
}
