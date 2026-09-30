# Kibooz App: Early Childhood Education and Eco-Gamification Platform

[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com)
[![Language](https://img.shields.io/badge/Language-Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-24%20(Nougat)-orange?style=for-the-badge)](https://developer.android.com/about/dashboards)
[![Target SDK](https://img.shields.io/badge/Target%20SDK-35%20(VanillaIceCream)-blue?style=for-the-badge)](https://developer.android.com/about/versions/15)
[![Build Tool](https://img.shields.io/badge/Gradle-8.14-02303A?style=for-the-badge&logo=gradle&logoColor=white)](https://gradle.org)
[![JDK](https://img.shields.io/badge/Java-JDK%2017-ED8B00?style=for-the-badge&logo=java&logoColor=white)](https://www.oracle.com/java/)

Kibooz adalah aplikasi Android untuk jenjang PAUD dan TK. Aplikasi ini menghubungkan guru dengan wali murid. Fitur intinya mencakup pemantauan kondisi emosional anak dan pembiasaan pilah sampah berhadiah poin reward.

---

## Daftar Isi

- [Fitur Utama](#fitur-utama)
  - [1. Autentikasi dan Onboarding](#1-autentikasi-dan-onboarding)
  - [2. Portal Guru](#2-portal-guru)
  - [3. Portal Wali Murid](#3-portal-wali-murid)
  - [4. Pemindai Kamera](#4-pemindai-kamera)
- [Arsitektur dan Teknologi](#arsitektur-dan-teknologi)
- [Desain Sistem dan Visual](#desain-sistem-dan-visual)
- [Struktur Direktori Proyek](#struktur-direktori-proyek)
- [Panduan Instalasi dan Menjalankan](#panduan-instalasi-dan-menjalankan)
- [Panduan Kolaborasi Git](#panduan-kolaborasi-git)
  - [Aturan Berkas Gitignore](#aturan-berkas-gitignore)
  - [Alur Kerja Branching dan Commit](#alur-kerja-branching-dan-commit)
- [Lisensi dan Hak Cipta](#lisensi-dan-hak-cipta)

---

## Fitur Utama

### 1. Autentikasi dan Onboarding
- **Splash Screen:** Layar pembuka dengan maskot Kibooz.
- **Carousel Onboarding:** Tiga slide pengenalan fitur utama dengan indikator titik dan tombol navigasi.
- **Pemilihan Peran:** Pengguna memilih portal sebagai Guru atau Wali Murid sebelum masuk.
- **Halaman Masuk:** Formulir login yang mengarahkan pengguna ke alur kerja masing-masing.

---

### 2. Portal Guru
- **Dashboard Guru:**
  - Header profil ringkas dan menu drawer samping.
  - Ringkasan mood harian siswa di kelas.
  - Akses cepat daftar kelas, mata pelajaran, dan jadwal kegiatan.
  - Bottom navigation bar terintegrasi.
- **Ruang Kelas Guru:**
  - Daftar kelas aktif beserta jumlah siswa.
  - Kotak dialog tambah kelas baru.
  - Pencarian dan filter kategori kelas.
- **Detail dan Peserta Kelas:**
  - Rincian mata pelajaran (Seni, Bahasa, Sosial, Sains).
  - Daftar siswa lengkap dengan perolehan poin dan status emosional.
- **Statistik Mood Siswa:**
  - Grafik kustom dengan Android Canvas:
    - Donut Mood Chart untuk proporsi emosi kelas.
    - Weekly Line Chart untuk tren mingguan.
    - Monthly Bar Chart untuk sebaran emosi bulanan.
- **Fitur Pendukung:**
  - Video pembelajaran untuk materi kelas.
  - Forum diskusi antarpendidik.
  - Pengaturan biodata dan profil guru.

---

### 3. Portal Wali Murid
- **Dashboard Wali:**
  - Kartu pemantau mood anak hari ini.
  - Informasi perolehan poin pilah sampah.
  - Akses langsung menuju scanner kamera.
- **Papan Peringkat:**
  - Podium 3D juara 1, 2, dan 3 dengan avatar melingkar penuh.
  - Lencana poin solid berkontras tinggi agar teks terbaca jelas.
  - Daftar peringkat siswa lanjutan dalam kartu putih melengkung.
- **Statistik Pilah Sampah:**
  - Riwayat pemilahan kategori organik, anorganik, B3, dan daur ulang.
  - Akumulasi poin reward anak.
- **Panduan Mood:**
  - Rekomendasi tindakan bagi orang tua saat anak mengalami emosi negatif.
- **Manajemen Akun:**
  - Halaman profil anak dan edit biodata siswa.
  - Halaman profil wali murid.

---

### 4. Pemindai Kamera
- **Scanner Mood Anak:** Viewfinder kamera dengan overlay pemandu deteksi ekspresi wajah.
- **Scanner Pilah Sampah:** Viewfinder kamera dengan kotak deteksi objek dan kartu identifikasi jenis sampah.

---

## Arsitektur dan Teknologi

| Komponen | Spesifikasi / Pustaka | Keterangan |
| :--- | :--- | :--- |
| **Bahasa Pemrograman** | Kotlin 1.9+ | Bahasa utama proyek |
| **Arsitektur UI** | MVC dengan ViewBinding | Pengikatan tampilan tanpa `findViewById` |
| **Komponen UI** | Material Design 3 (M3) | MaterialCardView, ShapeableImageView |
| **Grafik Kustom** | Custom View 2D Canvas | DonutMoodChart, WeeklyLineChart, MonthlyBarChart |
| **Navigasi** | Intent Stack dan Drawer | Navigasi antarlayar dan menu samping |
| **Edge-to-Edge** | WindowInsetsCompat (`InsetsUtil`) | Penanganan area status bar dan navigation bar |
| **Build Tool** | Gradle Kotlin DSL (`build.gradle.kts`) | Konfigurasi modular |

---

## Desain Sistem dan Visual

Daftar token warna yang digunakan di seluruh aplikasi:

| Token Warna | Nilai HEX | Penggunaan |
| :--- | :---: | :--- |
| **Primary Purple** | `#5D40B3` | Background utama, header, tombol aksi |
| **Accent Orange** | `#FD9C27` | Lencana poin, aksen reward, highlight |
| **Secondary Violet** | `#724AE2` | Gradasi kartu, balok juara 1 |
| **Soft Lilac** | `#EFEBFF` | Latar kartu modul dan tag |
| **Neutral White** | `#FFFFFF` | Latar bottom sheet dan teks kontras |
| **Text Dark** | `#201A3F` | Teks judul pada latar terang |
| **Text Subtitle** | `#756E8A` | Teks keterangan dan placeholder |

---

## Struktur Direktori Proyek

```text
KiboozApp/
├── gradle/                    # Wrapper binaries
├── app/
│   ├── build.gradle.kts       # Konfigurasi dependensi aplikasi
│   ├── proguard-rules.pro     # Aturan R8 dan ProGuard
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/valentinusmarvel/kibooz/
│       │   │   ├── ui/
│       │   │   │   ├── auth/          # Login dan RoleSelection
│       │   │   │   ├── camera/        # CameraScanMood dan CameraScanSampah
│       │   │   │   ├── common/        # Donut, Bar, dan Line Chart Views
│       │   │   │   ├── guru/          # Ruang Kelas, Detail, Mood, Profil
│       │   │   │   ├── onboarding/    # Carousel Onboarding dan Adapter
│       │   │   │   ├── splash/        # Splash Screen
│       │   │   │   └── wali/          # Dashboard Wali, Leaderboard, Sampah
│       │   │   └── util/
│       │   │       └── InsetsUtil.kt  # Helper Insets System Bar
│       │   └── res/
│       │       ├── drawable/          # Vektor ikon, shape, dan background
│       │       ├── layout/            # Layout XML seluruh activity
│       │       ├── values/            # colors.xml, strings.xml, themes.xml
│       │       └── mipmap/            # Ikon launcher Kibooz
├── .gitignore                 # Aturan pengabaian berkas bersama
├── build.gradle.kts           # Root gradle script
├── gradle.properties          # Pengaturan JVM gradle
├── gradlew / gradlew.bat      # Script eksekusi wrapper
└── settings.gradle.kts        # Pengaturan repositori dan modul
```

---

## Panduan Instalasi dan Menjalankan

### Prasyarat
1. Android Studio Hedgehog (2023.1.1) atau versi lebih baru.
2. JDK 17.
3. Perangkat fisik atau emulator dengan Android 7.0 (API 24) ke atas.

### Langkah Setup
1. Clone repositori ini:
   ```bash
   git clone https://github.com/ValentinusMarvel/kibooz-app.git
   cd kibooz-app
   ```
2. Buka Android Studio.
3. Pilih menu **Open**, lalu tentukan direktori `KiboozApp`.
4. Tunggu sinkronisasi Gradle selesai. Android Studio membuat file `local.properties` secara otomatis.
5. Sambungkan perangkat Android atau aktifkan emulator.
6. Tekan tombol **Run** (`Shift + F10`).

---

## Panduan Kolaborasi Git

### Aturan Berkas Gitignore

Berkas `.gitignore` memastikan repositori tetap bersih:
- Berkas `local.properties` tidak di-commit karena berisi path lokal SDK setiap komputer.
- Direktori `.gradle/`, `.kotlin/`, dan seluruh output `build/` diabaikan otomatis.
- File konfigurasi workspace IDE dan file biner `.apk` tidak dimasukkan ke dalam commit.

### Alur Kerja Branching dan Commit

Gunakan alur berikut untuk kolaborasi:

1. Buat branch baru dari `main`:
   ```bash
   git checkout -b feat/nama-fitur
   ```
2. Tulis pesan commit secara ringkas dan deskriptif:
   - `feat: tambah filter pencarian ruang kelas`
   - `fix: perbaiki batas avatar leaderboard`
   - `docs: perbarui panduan instalasi`
3. Unggah branch ke remote:
   ```bash
   git push origin feat/nama-fitur
   ```
4. Buka Pull Request di GitHub untuk ditinjau bersama sebelum digabung ke `main`.

---

## Lisensi dan Hak Cipta

Proyek ini dikembangkan untuk mata kuliah Pengembangan Aplikasi Perangkat Bergerak (PAPB). Hak cipta desain dan aset berada pada pengembang Kibooz.
