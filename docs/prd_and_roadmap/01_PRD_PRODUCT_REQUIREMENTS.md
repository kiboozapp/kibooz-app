# Product Requirements Document (PRD): Platform Edukasi Kibooz

Dokumen ini mendefinisikan visi produk, persona pengguna, batasan teknis, aturan bisnis, dan spesifikasi kebutuhan fungsional serta non-fungsional aplikasi Kibooz.

---

## 1. Visi Produk dan Pernyataan Masalah

### 1.1 Latar Belakang Masalah
Pendidikan Anak Usia Dini (PAUD) dan Taman Kanak-Kanak (TK) menghadapi dua tantangan utama dalam interaksi harian:
1. **Kesenjangan Komunikasi Emosi Anak:** Orang tua kesulitan mengetahui dinamika emosional anak selama di sekolah. Guru memiliki keterbatasan waktu untuk melaporkan status mood setiap anak secara personal satu per satu.
2. **Kurangnya Kebiasaan Ekologis Sejak Dini:** Pengenalan pemilahan sampah pada anak usia dini sering kali bersifat teoritis tanpa insentif interaktif yang menyenangkan.

### 1.2 Solusi Kibooz
Kibooz menghadirkan platform mobile terintegrasi berbasis Android dengan maskot karakter beruang ungu (*Kibby*) yang menggabungkan:
- Pemantauan kondisi emosional anak secara objektif menggunakan kecerdasan buatan (*AI Facial Expression Recognition*).
- Gamifikasi pemilahan sampah organik dan anorganik dengan sistem poin reward dan papan peringkat (*leaderboard*).
- Portal komunikasi dua arah antara guru kelas dan orang tua murid dengan panduan penanganan psikologis praktis.

---

## 2. Persona Pengguna

### Persona 1: Guru Kelas (Pendidik)
- **Nama:** Siti Rahayu, S.Pd. (32 Tahun)
- **Peran:** Guru Wali Kelas A, TK Pertiwi Harapan Bangsa
- **Karakteristik:** Menangani 20-25 anak setiap hari. Membutuhkan alat kerja cepat tanpa prosedur administrasi yang berbelit.
- **Tujuan Utama:**
  - Melakukan presensi mood anak saat kedatangan pagi hari dalam hitungan detik.
  - Memantau sebaran grafik mood mingguan kelas untuk mengevaluasi efektivitas metode belajar.
  - Mengirimkan rekomendasi penanganan kepada orang tua jika anak menunjukkan tanda murung atau gelisah.

### Persona 2: Wali Murid (Orang Tua)
- **Nama:** Sarah Kartika, S.Pd. (29 Tahun)
- **Peran:** Ibu bekerja dari Amelia Siti Zahra (Siswa Kelas A)
- **Karakteristik:** Memiliki mobilitas tinggi namun ingin selalu terlibat aktif dalam tumbuh kembang psikologis anak.
- **Tujuan Utama:**
  - Menerima pembaruan status mood harian anak segera setelah jam sekolah dimulai.
  - Mengakses langkah konkret penanganan ketika anak terdeteksi mengalami emosi negatif.
  - Memotivasi anak memilah sampah di rumah dan di sekolah untuk mengumpulkan poin reward bersama maskot Kibby.

### Persona 3: Siswa (Peserta Didik)
- **Nama:** Amelia Siti Zahra (5 Tahun)
- **Peran:** Siswa Aktif Kelas A
- **Karakteristik:** Sangat responsif terhadap visual warna-warni, maskot ramah, dan apresiasi positif.
- **Tujuan Utama:**
  - Merasa nyaman mengekspresikan perasaan tanpa rasa takut atau tertekan.
  - Bersemangat membuang sampah pada tempat yang tepat untuk meraih peringkat podium juara.

---

## 3. Kebutuhan Fungsional (Functional Requirements)

### FR-1: Autentikasi dan Manajemen Sesi
- **FR-1.1:** Sistem menyediakan slide onboarding dengan visualisasi maskot Kibby dan indikator halaman.
- **FR-1.2:** Pengguna memilih peran pada halaman Role Selection (Wali Murid atau Guru). Pilihan ini menentukan dashboard dan izin navigasi yang diberikan.
- **FR-1.3:** Login menggunakan kredensial email dan kata sandi dengan validasi format masukan secara *real-time*.
- **FR-1.4:** Sistem menyimpan token akses terenkripsi pada penyimpanan lokal perangkat sehingga pengguna tidak perlu login ulang saat membuka aplikasi kembali.
- **FR-1.5:** Tombol keluar akun menghapus sesi aktif dan mengarahkan kembali ke halaman pemilihan peran.

### FR-2: Portal dan Dashboard Guru
- **FR-2.1:** Menampilkan ringkasan metrik kelas: jumlah total siswa, persentase siswa hadir, dan indeks mood dominan hari ini.
- **FR-2.2:** Menyediakan daftar jalan pintas cepat ke Ruang Kelas, Input Mood Manual, dan Kamera Scan AI.
- **FR-2.3:** Mengelola daftar kelas (menambah kelas baru, menentukan mata pelajaran, dan melihat daftar siswa terdaftar).
- **FR-2.4:** Menyediakan akses ke modul video pembelajaran dan forum diskusi antarpendidik.

### FR-3: Visualisasi Analitik Mood
- **FR-3.1 Donut Mood Chart:** Menampilkan proporsi empat kategori emosi anak (Senang, Sedih, Marah, Bingung) dalam bentuk diagram donat interaktif.
- **FR-3.2 Weekly Line Chart:** Menampilkan fluktuasi skor kebahagiaan rata-rata kelas dari hari Senin hingga Jumat.
- **FR-3.3 Monthly Bar Chart:** Menampilkan distribusi frekuensi emosi selama periode satu bulan penuh.
- **FR-3.4 Interaktivitas Grafik:** Sentuhan pada segmen grafik menampilkan angka detail dan label kategori emosi.

### FR-4: Video Pembelajaran dan Forum Diskusi
- **FR-4.1:** Menampilkan katalog video edukasi ramah anak dengan pemutar media terintegrasi (*ExoPlayer* / *Media3*).
- **FR-4.2:** Ruang forum diskusi kelas memungkinkan guru dan wali bertukar pesan dan catatan perkembangan siswa.

### FR-5: Portal dan Dashboard Wali Murid
- **FR-5.1:** Menampilkan status mood terkini anak hari ini lengkap dengan catatan guru pendamping.
- **FR-5.2:** Menampilkan kartu perolehan poin pilah sampah anak dan peringkat saat ini di kelas.
- **FR-5.3 Detail Panduan Penanganan Emosi:** Menyediakan 4 langkah sistematis bagi orang tua saat anak terdeteksi mengalami emosi cemas atau ragu:
  1. Turun ke level pandang anak (kontak mata setara).
  2. Validasi perasaannya tanpa penghakiman.
  3. Mengajak berbicara perlahan dengan kalimat sederhana.
  4. Memberikan contoh nyata, sentuhan menenangkan, dan pelukan hangat.
- **FR-5.4:** Tombol aksi konfirmasi "Terapkan Panduan & Laporkan ke Guru" untuk memberitahu guru bahwa langkah penanganan telah dilakukan di rumah.

### FR-6: Ekosistem Gamifikasi Pilah Sampah
- **FR-6.1:** Pencatatan aktivitas pilah sampah kategori Organik dan Anorganik.
- **FR-6.2:** Aturan perhitungan poin:
  - Sampah Organik: 10 poin per aksi/pembuangan terverifikasi.
  - Sampah Anorganik: 15 poin per aksi/pembuangan terverifikasi.
- **FR-6.3 Leaderboard Podium:** Menampilkan 3 besar siswa terbaik pada panggung podium grafis khusus dengan lencana emas, perak, dan perunggu.
- **FR-6.4 Daftar Peringkat Lanjutan:** Menampilkan posisi anak pengguna beserta peringkat siswa lainnya secara transparan.

### FR-7: Profil Pengguna dan Data Siswa
- **FR-7.1 Profil Wali:** Menampilkan identitas wali murid, nama anak asuh, tautan ubah sandi, syarat ketentuan, dan kebijakan privasi.
- **FR-7.2 Profil Siswa:** Menampilkan NISN resmi (Nomor Induk Siswa Nasional), nama lengkap, kelas, kontak darurat, dan alamat rumah.
- **FR-7.3 Profil Guru:** Menampilkan NIP (Nomor Induk Pegawai), email dinas, nomor telepon, alamat, dan grafik persentase rata-rata mood kelas yang dibimbing.
- **FR-7.4:** Formulir edit profil dengan validasi kolom dan pembaharuan foto profil.

### FR-8: Sistem Kamera AI (Computer Vision)
- **FR-8.1 Viewfinder Kamera:** Antarmuka kamera layar penuh menggunakan CameraX dengan rasio bingkai stabil 30fps.
- **FR-8.2 AI Scanner Mood:** Memproses bingkai wajah anak secara lokal, mengenali ekspresi, dan memetakan ke salah satu dari 4 kategori emosi dengan tingkat kepercayaan (*confidence score*).
- **FR-8.3 AI Scanner Sampah:** Mendeteksi objek sampah yang diarahkan ke kamera, mengidentifikasi kategori (Organik vs Anorganik), dan menampilkan kartu konfirmasi perolehan poin.

---

## 4. Kebutuhan Non-Fungsional (Non-Functional Requirements)

| Kode | Kategori | Spesifikasi Kebutuhan |
| :--- | :--- | :--- |
| **NFR-1** | **Performa** | Waktu *cold start* aplikasi di bawah 2.0 detik pada perangkat acuan (RAM 4GB). Render antarmuka dan chart stabil pada 60fps tanpa *jank*. |
| **NFR-2** | **Keamanan & Privasi** | Sesuai prinsip perlindungan privasi anak (COPPA). Data biometrik mentah wajah tidak dikirimkan ke cloud; hanya vektor inferensi atau label emosi yang dicatat. Komunikasi jaringan wajib menggunakan HTTPS dengan enkripsi TLS 1.3. |
| **NFR-3** | **Keandalan Offline** | Data profil, ringkasan kelas, dan panduan emosi dapat diakses tanpa koneksi internet melalui penyimpanan cache lokal Room Database. Aksi pencatatan saat offline disimpan dalam antrean sinkronisasi (*sync queue*). |
| **NFR-4** | **Kompatibilitas** | Mendukung Android versi 7.0 (API Level 24) hingga Android 15 (API Level 35). Mendukung berbagai rasio aspek layar modern (18:9, 19.5:9, 20:9) dengan adaptasi *edge-to-edge insets*. |
| **NFR-5** | **Aksesibilitas** | Seluruh elemen teks memenuhi rasio kontras warna minimal 4.5:1 (WCAG AA). Setiap tombol dan ikon memiliki atribut `contentDescription` yang jelas untuk pembaca layar (*TalkBack*). |
| **NFR-6** | **Lokalisasi** | Antarmuka disajikan dalam Bahasa Indonesia baku yang komunikatif, ramah anak, dan bebas dari ambiguitas istilah teknis. |

---

## 5. Aturan Bisnis (Business Rules)

1. **BR-01 Batas Klaim Poin Harian:** Seorang siswa maksimal dapat mengklaim poin pilah sampah sebanyak 5 kali per hari untuk mencegah eksploitasi poin buatan.
2. **BR-02 Hak Akses Data Emosi:** Data emosi individual siswa hanya dapat dilihat oleh guru kelas yang bersangkutan dan wali murid dari anak tersebut. Guru kelas lain atau wali murid siswa lain tidak memiliki akses ke riwayat emosi personal siswa lain.
3. **BR-03 Pembaruan Papan Peringkat:** Papan peringkat diperbarui secara instan (*real-time*) setiap kali ada penambahan poin terkonfirmasi. Peringkat direset setiap awal semester akademik dengan riwayat penghargaan semester sebelumnya tetap tersimpan di arsip prestasi.
4. **BR-04 Konfirmasi Deteksi AI di Bawah Ambang Batas:** Jika skor keyakinan (*confidence score*) AI di bawah 75%, sistem wajib menampilkan dialog konfirmasi manual kepada pengguna (guru atau wali) untuk memastikan keakuratan data emosi atau jenis sampah.
