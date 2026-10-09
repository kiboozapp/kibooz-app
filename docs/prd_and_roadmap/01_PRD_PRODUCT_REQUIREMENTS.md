# Product Requirements Document (PRD): Platform Edukasi Kibooz

PRD ini merinci visi produk, persona pengguna, kebutuhan fungsional, dan aturan bisnis aplikasi Kibooz.

---

## 1. Visi Produk dan Masalah Utama

### 1.1 Latar Belakang Masalah
Pendidikan Anak Usia Dini (PAUD) dan Taman Kanak-Kanak (TK) menghadapi dua masalah harian:
1. **Kesenjangan Komunikasi Emosi Anak:** Orang tua sulit memantau emosi anak di sekolah. Guru kekurangan waktu melapor status mood anak satu per satu.
2. **Kurangnya Kebiasaan Ekologis:** Pengenalan pilah sampah pada anak usia dini kerap kali berupa teori. Belum ada sarana interaktif yang menyenangkan.

### 1.2 Solusi Kibooz
Kibooz memadukan aplikasi Android dan karakter maskot beruang ungu (*Kibby*):
- Deteksi ekspresi wajah anak lewat kecerdasan buatan (*AI Facial Expression Recognition*).
- Gamifikasi pilah sampah organik dan anorganik dengan poin reward serta papan peringkat (*leaderboard*).
- Portal komunikasi guru dan orang tua dengan panduan penanganan psikologis praktis.

---

## 2. Persona Pengguna

### Persona 1: Guru Kelas (Pendidik)
- **Nama:** Siti Rahayu, S.Pd. (32 Tahun)
- **Peran:** Guru Wali Kelas A, TK Pertiwi Harapan Bangsa
- **Karakteristik:** Menangani 20-25 anak per hari. Butuh alat kerja cepat tanpa administrasi rumit.
- **Tujuan Utama:**
  - Mencatat presensi mood anak saat pagi tiba.
  - Memantau grafik mood mingguan kelas.
  - Mengirim saran penanganan saat anak tampak murung.

### Persona 2: Wali Murid (Orang Tua)
- **Nama:** Sarah Kartika, S.Pd. (29 Tahun)
- **Peran:** Ibu bekerja dari Amelia Siti Zahra (Siswa Kelas A)
- **Karakteristik:** Punya mobilitas tinggi tapi ingin memantau emosi anak.
- **Tujuan Utama:**
  - Menerima update mood anak usai kelas mulai.
  - Membaca langkah praktis saat anak cemas.
  - Memandu anak memilah sampah di rumah bersama Kibby.

### Persona 3: Siswa (Peserta Didik)
- **Nama:** Amelia Siti Zahra (5 Tahun)
- **Peran:** Siswa Aktif Kelas A
- **Karakteristik:** Responsif terhadap visual cerah dan apresiasi positif.
- **Tujuan Utama:**
  - Nyaman menyatakan perasaan tanpa rasa takut.
  - Senang memilah sampah demi podium juara.

---

## 3. Kebutuhan Fungsional (Functional Requirements)

### FR-1: Autentikasi dan Manajemen Sesi
- **FR-1.1:** Sistem menyajikan slide onboarding bersama maskot Kibby.
- **FR-1.2:** Pengguna memilih peran pada halaman Role Selection (Wali Murid atau Guru). Pilihan ini mengatur rute dashboard dan navigasi.
- **FR-1.3:** Login memakai email dan kata sandi dengan validasi input langsung.
- **FR-1.4:** Sistem menyimpan token JWT terenkripsi di DataStore. Pengguna tidak perlu login berulang kali.
- **FR-1.5:** Tombol keluar menghapus sesi dan membuka kembali layar pemilihan peran.

### FR-2: Portal dan Dashboard Guru
- **FR-2.1:** Menampilkan ringkasan metrik kelas: jumlah total siswa, persentase kehadiran, dan tren mood kelas.
- **FR-2.2:** Menyediakan jalan pintas ke Ruang Kelas, Input Mood Manual, dan Kamera Scan AI.
- **FR-2.3:** Mengelola daftar kelas (tambah kelas dan daftar siswa).
- **FR-2.4:** Menyediakan akses modul video pembelajaran dan forum diskusi pendidik.

### FR-3: Visualisasi Analitik Mood
- **FR-3.1 Donut Mood Chart:** Menampilkan proporsi empat emosi anak (Senang, Sedih, Marah, Bingung) dalam diagram donat.
- **FR-3.2 Weekly Line Chart:** Menampilkan fluktuasi skor kebahagiaan rata-rata kelas dari Senin hingga Jumat.
- **FR-3.3 Monthly Bar Chart:** Menampilkan distribusi frekuensi emosi selama satu bulan penuh.
- **FR-3.4 Interaktivitas Grafik:** Sentuhan pada segmen grafik menampilkan angka detail dan nama kategori emosi.

### FR-4: Video Pembelajaran dan Forum Diskusi
- **FR-4.1:** Menampilkan video edukasi anak dengan pemutar media terintegrasi (*Media3 ExoPlayer*). Video berupa alamat https, baik tautan ke tempat lain maupun berkas yang diunggah guru.
- **FR-4.2:** Forum diskusi kelas memfasilitasi pertukaran pesan guru dan wali seputar tumbuh kembang siswa, dalam bentuk utas dan balasan. Postingan yang pernah diubah penulisnya diberi label diedit.
- **FR-4.3:** Guru kelas dapat mengubah dan menghapus video pembelajaran. Pembuat sebuah utas atau balasan dapat mengubahnya, dan pembuatnya atau guru kelas dapat menghapusnya.

### FR-5: Portal dan Dashboard Wali Murid
- **FR-5.1:** Menampilkan status mood anak hari ini lengkap dengan catatan guru pendamping.
- **FR-5.2:** Menampilkan kartu perolehan poin pilah sampah dan peringkat anak di kelas.
- **FR-5.3 Detail Panduan Penanganan Emosi:** Menyediakan 4 langkah konkret bagi orang tua:
  1. Turun ke level pandang anak (kontak mata setara).
  2. Validasi perasaan anak tanpa penghakiman.
  3. Ajak berbicara santai dengan kalimat pendek.
  4. Berikan sentuhan menenangkan dan pelukan hangat.
- **FR-5.4:** Tombol aksi konfirmasi "Terapkan Panduan & Laporkan ke Guru" memberitahu guru bahwa langkah penanganan sudah berjalan di rumah.

### FR-6: Ekosistem Gamifikasi Pilah Sampah
- **FR-6.1:** Pencatatan aktivitas pilah sampah kategori Organik dan Anorganik.
- **FR-6.2:** Aturan perhitungan poin:
  - Sampah Organik: 10 poin per aksi terverifikasi.
  - Sampah Anorganik: 15 poin per aksi terverifikasi.
- **FR-6.3 Leaderboard Podium:** Menampilkan 3 besar siswa terbaik pada panggung podium dengan lencana juara.
- **FR-6.4 Daftar Peringkat Lanjutan:** Menampilkan urutan ranking kelas dan skor masing-masing anak.

### FR-7: Profil Pengguna dan Data Siswa
- **FR-7.1 Profil Wali:** Menampilkan identitas wali murid, nama anak asuh, tautan ubah sandi, dan syarat ketentuan.
- **FR-7.2 Profil Siswa:** Menampilkan NISN resmi (Nomor Induk Siswa Nasional), nama lengkap, kelas, kontak darurat, dan alamat rumah.
- **FR-7.3 Profil Guru:** Menampilkan NIP (Nomor Induk Pegawai), email dinas, nomor telepon, alamat, dan grafik mood kelas bimbingan.
- **FR-7.4:** Formulir edit profil memvalidasi input teks dan memperbarui foto profil.

### FR-8: Sistem Kamera AI (Computer Vision)
- **FR-8.1 Viewfinder Kamera:** Antarmuka layar penuh CameraX dengan rasio bingkai stabil 30fps.
- **FR-8.2 AI Scanner Mood:** Memproses bingkai wajah anak di perangkat. Model mengenali ekspresi dan memetakan ke salah satu dari 4 emosi.
- **FR-8.3 AI Scanner Sampah:** Mendeteksi objek sampah di depan kamera, menentukan kategori (Organik vs Anorganik), dan menampilkan kartu poin.

---

## 4. Kebutuhan Non-Fungsional (Non-Functional Requirements)

| Kode | Kategori | Spesifikasi Kebutuhan |
| :--- | :--- | :--- |
| **NFR-1** | **Performa** | Cold start aplikasi di bawah 2.0 detik pada RAM 4GB. Render UI dan chart stabil pada 60fps tanpa lagging. |
| **NFR-2** | **Keamanan & Privasi** | Patuh aturan privasi anak (COPPA). Data foto wajah mentah tidak dikirim ke server. Jaringan wajib HTTPS dengan TLS 1.3. |
| **NFR-3** | **Keandalan Offline** | Data profil, ringkasan kelas, dan panduan emosi tersimpan di cache Room DB. Aksi offline masuk ke antrean sinkronisasi. |
| **NFR-4** | **Kompatibilitas** | Mendukung Android versi 7.0 (API Level 24) hingga Android 15 (API Level 35). Mendukung rasio layar modern lewat `InsetsUtil`. |
| **NFR-5** | **Aksesibilitas** | Teks memenuhi rasio kontras minimal 4.5:1 (WCAG AA). Tombol dan ikon memiliki atribut `contentDescription` untuk TalkBack. |
| **NFR-6** | **Bahasa** | Antarmuka memakai Bahasa Indonesia santun, ramah anak, dan bebas istilah asing yang membingungkan. |

---

## 5. Aturan Bisnis (Business Rules)

1. **BR-01 Batas Klaim Poin Harian:** Siswa dapat mengklaim poin pilah sampah maksimal 5 kali per hari. Batasan ini mencegah eksploitasi poin buatan.
2. **BR-02 Hak Akses Data Emosi:** Guru kelas dan wali murid berhak melihat data emosi siswa terkait. Guru atau wali lain tidak memiliki izin akses riwayat ini.
3. **BR-03 Pembaruan Papan Peringkat:** Papan peringkat langsung diperbarui setiap ada poin masuk. Sistem mereset peringkat pada awal semester baru. Pengaturan ulang otomatis ini belum dibangun di backend.
4. **BR-04 Konfirmasi Deteksi AI di Bawah Ambang Batas:** Skor keyakinan (*confidence score*) AI minimal 75%. Jika hasil di bawah 75%, sistem menampilkan dialog konfirmasi manual kepada pengguna. Langkah ini mencegah salah catat data.
