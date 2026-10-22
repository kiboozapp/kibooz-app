# Master Index: Dokumen Teknis dan Roadmap Pengembangan Kibooz

Indeks ini memuat spesifikasi teknis, Product Requirements Document (PRD), dan Work Breakdown Structure (WBS). Dokumen ini memandu tim mengubah prototipe UI Kibooz menjadi aplikasi fungsional penuh.

Susunan dokumen bersifat modular. Anggota tim (Backend, Frontend, AI/ML, QA, DevOps) dapat mengeksekusi tugas bersamaan tanpa saling tumpang tindih (*zero-overlap*).

---

## Daftar Berkas Panduan

| No | Dokumen | Fokus Area | Target Pembaca |
| :---: | :--- | :--- | :--- |
| **01** | [01_PRD_PRODUCT_REQUIREMENTS.md](01_PRD_PRODUCT_REQUIREMENTS.md) | Spesifikasi produk, persona pengguna, aturan bisnis, dan kebutuhan fungsional (FR-1 s.d. FR-8). | Seluruh Tim, Product Manager |
| **02** | [02_BACKEND_API_AND_DATABASE.md](02_BACKEND_API_AND_DATABASE.md) | Arsitektur API, skema relasional database, kontrak payload RESTful JSON, dan sistem otentikasi. | Backend Engineer, Database Admin |
| **03** | [03_FRONTEND_ANDROID_ROADMAP.md](03_FRONTEND_ANDROID_ROADMAP.md) | Refaktor arsitektur MVVM, integrasi Retrofit, Room DB, StateFlow, dan penghubungan 19 layar. | Android Frontend Engineers |
| **04** | [04_AI_ML_AND_CAMERA_PIPELINE.md](04_AI_ML_AND_CAMERA_PIPELINE.md) | Pipeline CameraX, model deteksi emosi wajah anak, dan model klasifikasi jenis sampah TFLite. | AI/ML Engineer, Vision Specialist |
| **05** | [05_QA_TESTING_AND_DEVOPS.md](05_QA_TESTING_AND_DEVOPS.md) | Matriks pengujian, otomasi UI Espresso, pipeline CI/CD GitHub Actions, dan rilis APK. | QA Engineer, DevOps Engineer |
| **06** | [06_TASK_WORK_BREAKDOWN_STRUCTURE.md](06_TASK_WORK_BREAKDOWN_STRUCTURE.md) | Pembagian tugas per individu, estimasi beban kerja, matriks RACI, dan diagram dependensi tugas. | Project Lead, Seluruh Tim |

---

## Status Baseline Proyek Saat Ini

Fondasi antarmuka dan komponen dasar sudah siap di repositori utama:

1. **Struktur Layar Utuh (19 Activity):**
   - Layar onboarding carousel interaktif dengan maskot beruang Kibby.
   - Portal otentikasi multi-peran (Wali Murid dan Guru).
   - Dashboard Wali Murid dan Dashboard Guru terpisah.
   - Ruang kelas interaktif (daftar modul, video pembelajaran, dan forum diskusi).
   - Visualisasi analitik kustom berbasis Android 2D Canvas (DonutMoodChart, WeeklyLineChart, MonthlyBarChart).
   - Sistem leaderboard dengan podium 3D, lencana poin, dan peringkat siswa.
   - Scanner kamera ganda (Mood AI dan Pilah Sampah).
2. **Sistem Desain Terstandarisasi:**
   - Palet warna konsisten (`#5D40B3`, `#FD9C27`, `#724AE2`, `#EFEBFF`).
   - Penanganan bilah status dan navigasi modern memakai helper [InsetsUtil.kt](../../app/src/main/java/com/nthb/kibooz/util/InsetsUtil.kt).
3. **Keterbatasan Saat Ini (Jarak Menuju 100%):**
   - Data masih berupa dummy statis di dalam activity.
   - Server backend dan basis data sudah tersedia di repositori backend (`SyafaHadyan/kibooz-backend`). Yang tersisa adalah menghubungkan aplikasi ke API tersebut.
   - Viewfinder kamera baru menampilkan grafis overlay tanpa inferensi model machine learning.
   - Belum ada caching Room dan manajemen state MVVM formal.

---

## Tahapan Rilis (Milestone Definition)

Status sisi backend untuk setiap fase dicatat di [ROADMAP.md](https://github.com/SyafaHadyan/kibooz-backend/blob/main/ROADMAP.md) pada repositori backend. Ketika salah satu dari dua dokumen ini berubah, dokumen yang lain diperbarui pada saat yang sama.

### Fase 1: Minimal Viable Product (MVP Core Loop)
- **Tujuan:** Menghubungkan alur utama dari registrasi, login, pencatatan mood manual, dan klaim poin sampah.
- **Hasil:**
  - RESTful API backend aktif dengan database PostgreSQL.
  - Login fungsional dengan token JWT dan penyimpanan sesi DataStore.
  - Input mood anak oleh guru tersimpan ke basis data dan langsung tampil di dashboard wali murid.
  - Input berat dan jenis sampah menambah poin serta mengubah urutan papan peringkat.

### Fase 2: Integrasi AI Vision dan Gamifikasi Lanjutan
- **Tujuan:** Mengotomatiskan deteksi emosi dan pilah sampah lewat kamera perangkat.
- **Hasil:**
  - Model TensorFlow Lite berjalan langsung pada CameraX ImageAnalysis.
  - Deteksi mood wajah anak menampilkan klasifikasi emosi (Senang, Sedih, Bingung, Marah).
  - Klasifikasi objek sampah mendeteksi Organik versus Anorganik dengan bounding box aktif.
  - Fitur video pembelajaran memutar video dari alamat https, termasuk berkas yang diunggah guru langsung ke Cloud Storage lewat alamat bertanda tangan.

### Fase 3: Hardening Produksi dan Pengujian Menyeluruh
- **Tujuan:** Menjamin keandalan aplikasi pada lingkungan sekolah nyata.
- **Hasil:**
  - Caching lokal Room Database untuk mode offline.
  - Notifikasi push (FCM) hanya sebagai pemicu bangun saat pesan percakapan baru masuk, dan aplikasi mengambil isi pesan dari API.
  - Cakupan unit test mencapai minimal 80% dengan pipeline CI/CD otomatis.
  - Berkas bundle AAB siap rilis.

---

## Prinsip Kolaborasi Antar Anggota Tim

1. **Kontrak API sebagai Titik Temu:** Tim Frontend dan Backend wajib menyepakati payload pada dokumen [02_BACKEND_API_AND_DATABASE.md](02_BACKEND_API_AND_DATABASE.md) sebelum menulis kode. Tim Frontend dapat memakai mock response agar pekerjaan tidak saling menunggu.
2. **Isolasi Fitur per Branch Git:** Setiap tugas dikerjakan pada branch terpisah dengan format `feat/<domain>-<nama-tugas>`. Jangan lakukan *push* langsung ke branch `main`.
3. **Standar Kode Tanpa Toleransi Eror:** Setiap pull request wajib lolos kompilasi `./gradlew assembleDebug` dan verifikasi lint tanpa bug regresi.
